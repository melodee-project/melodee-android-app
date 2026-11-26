package org.melodee.android.playback

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.melodee.android.MelodeeApplication
import org.melodee.android.catalog.MusicCatalog
import org.melodee.android.data.UserSettings
import org.melodee.android.model.Album
import org.melodee.android.model.Artist
import org.melodee.android.model.CatalogMode
import org.melodee.android.model.HomeFeed
import org.melodee.android.model.Playlist
import org.melodee.android.model.SearchResults
import org.melodee.android.model.Track

data class PlaybackUiState(
    val connected: Boolean = false,
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val queueOrder: List<Int> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
)

data class LibraryUiState(
    val mode: CatalogMode = CatalogMode.DEMO,
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val accountName: String? = null,
    val home: HomeFeed = HomeFeed(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val selectedAlbum: Album? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedTracks: List<Track> = emptyList(),
    val search: SearchResults = SearchResults(),
    val searchQuery: String = "",
    val searchLoading: Boolean = false,
    val error: String? = null,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MelodeeApplication
    private val container = app.container
    private var activeCatalog: MusicCatalog = container.demoCatalog

    val settings = app.preferences.settings.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5_000), UserSettings())
    private val mutableState = MutableStateFlow(PlaybackUiState())
    val state = mutableState.asStateFlow()
    private val mutableLibrary = MutableStateFlow(LibraryUiState())
    val library = mutableLibrary.asStateFlow()
    private var controller: MediaController? = null
    private var libraryJob: Job? = null
    private var searchJob: Job? = null
    private var selectionJob: Job? = null
    private val controllerFuture = MediaController.Builder(app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { update() }
        override fun onPlayerError(error: PlaybackException) {
            mutableState.value = mutableState.value.copy(error = friendlyPlaybackError(error))
        }
    }

    init {
        controllerFuture.addListener({
            if (!controllerFuture.isCancelled) {
                try {
                    controller = controllerFuture.get().also { it.addListener(listener) }
                    update()
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(
                        error = "Couldn't connect to the player. Reopen Melodee to retry.",
                    )
                }
            }
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            container.accountFlow.distinctUntilChanged().collect { reloadLibrary() }
        }
        viewModelScope.launch {
            container.session.authState.collect { state ->
                mutableLibrary.value = mutableLibrary.value.copy(
                    signedIn = container.session.currentAccount() != null,
                    accountName = container.session.currentAccount()?.username,
                    error = if (state == org.melodee.core.auth.AuthenticationState.REAUTHENTICATION_REQUIRED) {
                        "Sign in again to continue"
                    } else mutableLibrary.value.error,
                )
            }
        }
        viewModelScope.launch {
            while (isActive) { delay(250); update() }
        }
    }

    private fun reloadLibrary() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            mutableLibrary.value = mutableLibrary.value.copy(loading = true, error = null)
            try {
                activeCatalog = container.activeCatalog()
                val account = container.session.currentAccount()
                val mode = activeCatalog.mode
                val home = runCatching { activeCatalog.home() }.getOrElse { HomeFeed() }
                val albums = runCatching { activeCatalog.albums(1, 50) }.getOrElse { emptyList() }
                val artists = runCatching { activeCatalog.artists(1, 50) }.getOrElse { emptyList() }
                val playlists = runCatching { activeCatalog.playlists(1, 50) }.getOrElse { emptyList() }
                val tracks = runCatching { activeCatalog.tracks(1, 100) }.getOrElse { emptyList() }
                val favorites = runCatching { activeCatalog.favorites() }.getOrElse { emptyList() }
                mutableLibrary.value = mutableLibrary.value.copy(
                    mode = mode, loading = false, signedIn = account != null,
                    accountName = account?.username, home = home, albums = albums, artists = artists,
                    playlists = playlists, tracks = tracks, favorites = favorites,
                    error = if (mode == CatalogMode.LIVE && account != null && albums.isEmpty() && artists.isEmpty()) {
                        "Melodee could not load the library. Check the connection or sign in again."
                    } else null,
                )
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    loading = false,
                    error = libraryError(error),
                )
            }
        }
    }

    private fun update() {
        val player = controller ?: return
        val catalog = activeCatalog
        val current = player.currentMediaItem?.let(catalog::trackFromMediaItem)
        val queue = (0 until player.mediaItemCount).mapNotNull { index ->
            catalog.trackFromMediaItem(player.getMediaItemAt(index))
        }
        val order = mutableListOf<Int>()
        val timeline = player.currentTimeline
        var index = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
        while (index != C.INDEX_UNSET && index !in order && order.size < player.mediaItemCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        mutableState.value = mutableState.value.copy(
            connected = true,
            current = current,
            queue = queue,
            queueOrder = order,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: current?.durationMs ?: 0,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    fun play(ids: List<String>, index: Int = 0) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val items = activeCatalog.mediaItems(ids)
            if (items.isEmpty()) {
                mutableState.value = mutableState.value.copy(error = "That music is no longer available.")
                return@launch
            }
            controller?.apply {
                setMediaItems(items, index.coerceIn(items.indices), 0)
                prepare()
                play()
            }
            clearError()
        }
    }

    fun playAlbum(id: String, index: Int = 0) = viewModelScope.launch {
        val tracks = runCatching { activeCatalog.albumTracks(id) }.getOrElse { emptyList() }
        if (tracks.isNotEmpty()) play(tracks.map(Track::id), index)
    }

    fun playPlaylist(id: String) = viewModelScope.launch {
        val tracks = runCatching { activeCatalog.playlistTracks(id) }.getOrElse { emptyList() }
        if (tracks.isNotEmpty()) play(tracks.map(Track::id), 0)
    }

    fun togglePlay() {
        controller?.let { player ->
            if (player.isPlaying) player.pause()
            else {
                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    fun seek(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0)) }
    fun selectQueue(index: Int) { controller?.apply { seekTo(index, 0); play() } }
    fun removeQueue(index: Int) { controller?.removeMediaItem(index) }
    fun moveUp(index: Int) { if (index > 0) controller?.moveMediaItem(index, index - 1) }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }

    fun favorite(id: String) {
        viewModelScope.launch {
            if (mutableLibrary.value.mode == CatalogMode.DEMO) {
                app.preferences.toggleFavorite(id)
            } else {
                val favorite = mutableLibrary.value.favorites.none { it.id == id }
                runCatching { activeCatalog.setFavorite(id, favorite) }
                    .onFailure { mutableState.value = mutableState.value.copy(error = libraryError(it)) }
            }
            reloadLibrary()
        }
    }

    fun rememberPosition(value: Boolean) { viewModelScope.launch { app.preferences.rememberPosition(value) } }
    fun pauseOnDisconnect(value: Boolean) { viewModelScope.launch { app.preferences.pauseOnDisconnect(value) } }
    fun clearQueue() { controller?.apply { stop(); clearMediaItems() }; viewModelScope.launch {
        app.database.playbackDao().clear()
        val account = container.session.currentAccount()
        if (account != null) container.queueStore.clear(org.melodee.core.auth.AccountScope(account.serverId, account.accountId))
    } }
    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }

    fun refresh() = reloadLibrary()

    fun login(origin: String, userName: String, password: String, result: (org.melodee.android.live.auth.LiveLoginResult) -> Unit) {
        viewModelScope.launch { result(container.session.login(origin, userName, password)) }
    }

    fun signOut() {
        viewModelScope.launch { container.session.logout() }
    }

    fun search(query: String) {
        searchJob?.cancel()
        mutableLibrary.value = mutableLibrary.value.copy(searchQuery = query, searchLoading = query.isNotBlank())
        if (query.isBlank()) {
            mutableLibrary.value = mutableLibrary.value.copy(search = SearchResults(), searchLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            try {
                val results = activeCatalog.searchDomain(query.trim())
                mutableLibrary.value = mutableLibrary.value.copy(search = results, searchLoading = false)
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    search = SearchResults(), searchLoading = false, error = libraryError(error),
                )
            }
        }
    }

    fun openAlbum(id: String) {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            mutableLibrary.value = mutableLibrary.value.copy(
                selectedAlbum = null, selectedPlaylist = null, selectedTracks = emptyList(), error = null,
            )
            val album = runCatching { activeCatalog.album(id) }.getOrNull()
            val tracks = runCatching { activeCatalog.albumTracks(id) }.getOrElse { emptyList() }
            mutableLibrary.value = mutableLibrary.value.copy(
                selectedAlbum = album, selectedPlaylist = null, selectedTracks = tracks,
                error = if (tracks.isEmpty()) "This album does not have any available tracks." else null,
            )
        }
    }

    fun openPlaylist(id: String) {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            mutableLibrary.value = mutableLibrary.value.copy(
                selectedAlbum = null, selectedPlaylist = null, selectedTracks = emptyList(), error = null,
            )
            val playlist = runCatching { activeCatalog.playlist(id) }.getOrNull()
            val tracks = runCatching { activeCatalog.playlistTracks(id) }.getOrElse { emptyList() }
            mutableLibrary.value = mutableLibrary.value.copy(
                selectedAlbum = null, selectedPlaylist = playlist, selectedTracks = tracks,
                error = if (tracks.isEmpty()) "This playlist does not have any available tracks." else null,
            )
        }
    }

    private fun libraryError(error: Throwable): String = when (error) {
        is org.melodee.android.live.auth.NotSignedInException -> "Sign in to your Melodee server to browse your library."
        is org.melodee.android.live.auth.ReauthenticationRequiredException -> "Your session expired. Sign in again."
        is org.melodee.android.live.auth.SessionOutcomeUnknownException ->
            "Sign-in could not be confirmed. Sign in again to recover."
        is java.io.IOException -> "Could not reach the Melodee server."
        else -> "The Melodee library could not be loaded."
    }

    private fun friendlyPlaybackError(error: PlaybackException): String =
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Connection lost. Cached music can keep playing; retry when the network returns."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "The server rejected this stream. Retry to resolve a fresh one."
            else -> "Playback stopped. Try playing this track again."
        }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
