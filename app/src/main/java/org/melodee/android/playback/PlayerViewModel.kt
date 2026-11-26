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
    val mode: CatalogMode = CatalogMode.LIVE,
    val loading: Boolean = true,
    val authChecked: Boolean = false,
    val signedIn: Boolean = false,
    val accountName: String? = null,
    val home: HomeFeed = HomeFeed(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val albumPage: Int = 1,
    val artistPage: Int = 1,
    val playlistPage: Int = 1,
    val trackPage: Int = 1,
    val favoritePage: Int = 1,
    val hasMoreAlbums: Boolean = false,
    val hasMoreArtists: Boolean = false,
    val hasMorePlaylists: Boolean = false,
    val hasMoreTracks: Boolean = false,
    val hasMoreFavorites: Boolean = false,
    val loadingMoreAlbums: Boolean = false,
    val loadingMoreArtists: Boolean = false,
    val loadingMorePlaylists: Boolean = false,
    val loadingMoreTracks: Boolean = false,
    val loadingMoreFavorites: Boolean = false,
    val selectedAlbum: Album? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedArtist: Artist? = null,
    val selectedTracks: List<Track> = emptyList(),
    val artistAlbums: List<Album> = emptyList(),
    val artistTracks: List<Track> = emptyList(),
    val selectionLoading: Boolean = false,
    val selectionError: String? = null,
    val playlistTrackPage: Int = 1,
    val hasMorePlaylistTracks: Boolean = false,
    val loadingMorePlaylistTracks: Boolean = false,
    val artistAlbumPage: Int = 1,
    val artistTrackPage: Int = 1,
    val hasMoreArtistAlbums: Boolean = false,
    val hasMoreArtistTracks: Boolean = false,
    val loadingMoreArtistAlbums: Boolean = false,
    val loadingMoreArtistTracks: Boolean = false,
    val search: SearchResults = SearchResults(),
    val searchQuery: String = "",
    val searchLoading: Boolean = false,
    val searchPage: Int = 1,
    val hasMoreSearch: Boolean = false,
    val loadingMoreSearch: Boolean = false,
    val error: String? = null,
)

private const val ALBUM_PAGE_SIZE = 50
private const val ARTIST_PAGE_SIZE = 50
private const val PLAYLIST_PAGE_SIZE = 50
private const val TRACK_PAGE_SIZE = 100
private const val FAVORITE_PAGE_SIZE = 50
private const val ARTIST_ALBUM_PAGE_SIZE = 50
private const val ARTIST_TRACK_PAGE_SIZE = 100
private const val PLAYLIST_TRACK_PAGE_SIZE = 100
private const val SEARCH_PAGE_SIZE = 50

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MelodeeApplication
    private val container = app.container
    private var activeCatalog: MusicCatalog = container.liveCatalog

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
                val account = container.session.currentAccount()
                val needsLogin = state == org.melodee.core.auth.AuthenticationState.SIGNED_OUT ||
                    state == org.melodee.core.auth.AuthenticationState.REAUTHENTICATION_REQUIRED ||
                    state == org.melodee.core.auth.AuthenticationState.REFRESH_OUTCOME_UNKNOWN
                mutableLibrary.value = mutableLibrary.value.copy(
                    signedIn = account != null && !needsLogin,
                    accountName = account?.username,
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
            mutableLibrary.value = mutableLibrary.value.copy(
                loading = true, error = null, selectedAlbum = null, selectedPlaylist = null,
                selectedArtist = null, selectedTracks = emptyList(), artistAlbums = emptyList(),
                artistTracks = emptyList(), selectionLoading = false, selectionError = null,
            )
            try {
                val account = container.session.currentAccount()
                if (account == null) {
                    mutableLibrary.value = LibraryUiState(
                        mode = CatalogMode.LIVE, loading = false, authChecked = true, signedIn = false,
                    )
                    return@launch
                }
                activeCatalog = container.liveCatalog
                val mode = activeCatalog.mode
                val home = runCatching { activeCatalog.home() }.getOrElse { HomeFeed() }
                val albums = runCatching { activeCatalog.albums(1, ALBUM_PAGE_SIZE) }.getOrElse { emptyList() }
                val artists = runCatching { activeCatalog.artists(1, ARTIST_PAGE_SIZE) }.getOrElse { emptyList() }
                val playlists = runCatching { activeCatalog.playlists(1, PLAYLIST_PAGE_SIZE) }.getOrElse { emptyList() }
                val tracks = runCatching { activeCatalog.tracks(1, TRACK_PAGE_SIZE) }.getOrElse { emptyList() }
                val favorites = runCatching { activeCatalog.favorites(1, FAVORITE_PAGE_SIZE) }.getOrElse { emptyList() }
                val live = mode == CatalogMode.LIVE
                mutableLibrary.value = mutableLibrary.value.copy(
                    mode = mode, loading = false, authChecked = true, signedIn = true,
                    accountName = account.username, home = home, albums = albums, artists = artists,
                    playlists = playlists, tracks = tracks, favorites = favorites,
                    albumPage = 1, artistPage = 1, playlistPage = 1, trackPage = 1, favoritePage = 1,
                    hasMoreAlbums = live && albums.size >= ALBUM_PAGE_SIZE,
                    hasMoreArtists = live && artists.size >= ARTIST_PAGE_SIZE,
                    hasMorePlaylists = live && playlists.size >= PLAYLIST_PAGE_SIZE,
                    hasMoreTracks = live && tracks.size >= TRACK_PAGE_SIZE,
                    hasMoreFavorites = live && favorites.size >= FAVORITE_PAGE_SIZE,
                    loadingMoreAlbums = false, loadingMoreArtists = false, loadingMorePlaylists = false,
                    loadingMoreTracks = false, loadingMoreFavorites = false,
                    playlistTrackPage = 1, hasMorePlaylistTracks = false, loadingMorePlaylistTracks = false,
                    artistAlbumPage = 1, artistTrackPage = 1, hasMoreArtistAlbums = false,
                    hasMoreArtistTracks = false, loadingMoreArtistAlbums = false, loadingMoreArtistTracks = false,
                    searchPage = 1, hasMoreSearch = false, loadingMoreSearch = false,
                    error = if (live && albums.isEmpty() && artists.isEmpty()) {
                        "Melodee could not retrieve your catalog. Check the connection or sign in again."
                    } else null,
                )
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    loading = false, authChecked = true,
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
            val favorite = mutableLibrary.value.favorites.none { it.id == id }
            runCatching { activeCatalog.setFavorite(id, favorite) }
                .onFailure { mutableState.value = mutableState.value.copy(error = libraryError(it)) }
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
            mutableLibrary.value = mutableLibrary.value.copy(
                search = SearchResults(), searchLoading = false, searchPage = 1,
                hasMoreSearch = false, loadingMoreSearch = false,
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            try {
                val results = activeCatalog.searchDomain(query.trim(), 1, SEARCH_PAGE_SIZE)
                val loaded = results.tracks.size + results.albums.size + results.artists.size + results.playlists.size
                val more = results.totalCount > loaded ||
                    listOf(results.tracks.size, results.albums.size, results.artists.size, results.playlists.size)
                        .any { it >= SEARCH_PAGE_SIZE }
                mutableLibrary.value = mutableLibrary.value.copy(
                    search = results, searchLoading = false, searchPage = 1,
                    hasMoreSearch = more, loadingMoreSearch = false,
                )
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
                selectedAlbum = null, selectedPlaylist = null, selectedArtist = null,
                selectedTracks = emptyList(), artistAlbums = emptyList(), artistTracks = emptyList(),
                selectionLoading = true, selectionError = null,
            )
            try {
                val album = activeCatalog.album(id) ?: throw IllegalStateException("Album not found")
                val tracks = activeCatalog.albumTracks(id)
                mutableLibrary.value = mutableLibrary.value.copy(
                    selectedAlbum = album, selectedPlaylist = null, selectedArtist = null,
                    selectedTracks = tracks, artistAlbums = emptyList(), artistTracks = emptyList(),
                    selectionLoading = false, selectionError = null,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    selectionLoading = false, selectionError = libraryError(error),
                )
            }
        }
    }

    fun openPlaylist(id: String) {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            mutableLibrary.value = mutableLibrary.value.copy(
                selectedAlbum = null, selectedPlaylist = null, selectedArtist = null,
                selectedTracks = emptyList(), artistAlbums = emptyList(), artistTracks = emptyList(),
                selectionLoading = true, selectionError = null,
            )
            try {
                val playlist = activeCatalog.playlist(id) ?: throw IllegalStateException("Playlist not found")
                val tracks = activeCatalog.playlistTracks(id, 1, PLAYLIST_TRACK_PAGE_SIZE)
                mutableLibrary.value = mutableLibrary.value.copy(
                    selectedAlbum = null, selectedPlaylist = playlist, selectedArtist = null,
                    selectedTracks = tracks, artistAlbums = emptyList(), artistTracks = emptyList(),
                    selectionLoading = false, selectionError = null,
                    playlistTrackPage = 1, hasMorePlaylistTracks = tracks.size >= PLAYLIST_TRACK_PAGE_SIZE,
                    loadingMorePlaylistTracks = false,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    selectionLoading = false, selectionError = libraryError(error),
                )
            }
        }
    }

    fun openArtist(id: String) {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            mutableLibrary.value = mutableLibrary.value.copy(
                selectedAlbum = null, selectedPlaylist = null, selectedArtist = null,
                selectedTracks = emptyList(), artistAlbums = emptyList(), artistTracks = emptyList(),
                selectionLoading = true, selectionError = null,
            )
            try {
                val artist = activeCatalog.artist(id) ?: throw IllegalStateException("Artist not found")
                val albums = activeCatalog.artistAlbums(id, 1, ARTIST_ALBUM_PAGE_SIZE)
                val tracks = activeCatalog.artistTracks(id, 1, ARTIST_TRACK_PAGE_SIZE)
                mutableLibrary.value = mutableLibrary.value.copy(
                    selectedAlbum = null, selectedPlaylist = null, selectedArtist = artist,
                    selectedTracks = emptyList(), artistAlbums = albums, artistTracks = tracks,
                    selectionLoading = false, selectionError = null,
                    artistAlbumPage = 1, artistTrackPage = 1,
                    hasMoreArtistAlbums = albums.size >= ARTIST_ALBUM_PAGE_SIZE,
                    hasMoreArtistTracks = tracks.size >= ARTIST_TRACK_PAGE_SIZE,
                    loadingMoreArtistAlbums = false, loadingMoreArtistTracks = false,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    selectionLoading = false, selectionError = libraryError(error),
                )
            }
        }
    }

    fun loadMoreAlbums() = loadMoreLibraryPage(
        loading = mutableLibrary.value.loadingMoreAlbums || !mutableLibrary.value.hasMoreAlbums,
        markLoading = { copy(loadingMoreAlbums = true) },
        page = mutableLibrary.value.albumPage,
        pageSize = ALBUM_PAGE_SIZE,
        fetch = { page -> activeCatalog.albums(page, ALBUM_PAGE_SIZE) },
        apply = { state, items, page, more ->
            state.copy(albums = (state.albums + items).distinctBy(Album::id), albumPage = page,
                hasMoreAlbums = more, loadingMoreAlbums = false)
        },
    )

    fun loadMoreArtists() = loadMoreLibraryPage(
        loading = mutableLibrary.value.loadingMoreArtists || !mutableLibrary.value.hasMoreArtists,
        markLoading = { copy(loadingMoreArtists = true) },
        page = mutableLibrary.value.artistPage,
        pageSize = ARTIST_PAGE_SIZE,
        fetch = { page -> activeCatalog.artists(page, ARTIST_PAGE_SIZE) },
        apply = { state, items, page, more ->
            state.copy(artists = (state.artists + items).distinctBy(Artist::id), artistPage = page,
                hasMoreArtists = more, loadingMoreArtists = false)
        },
    )

    fun loadMorePlaylists() = loadMoreLibraryPage(
        loading = mutableLibrary.value.loadingMorePlaylists || !mutableLibrary.value.hasMorePlaylists,
        markLoading = { copy(loadingMorePlaylists = true) },
        page = mutableLibrary.value.playlistPage,
        pageSize = PLAYLIST_PAGE_SIZE,
        fetch = { page -> activeCatalog.playlists(page, PLAYLIST_PAGE_SIZE) },
        apply = { state, items, page, more ->
            state.copy(playlists = (state.playlists + items).distinctBy(Playlist::id), playlistPage = page,
                hasMorePlaylists = more, loadingMorePlaylists = false)
        },
    )

    fun loadMoreTracks() = loadMoreLibraryPage(
        loading = mutableLibrary.value.loadingMoreTracks || !mutableLibrary.value.hasMoreTracks,
        markLoading = { copy(loadingMoreTracks = true) },
        page = mutableLibrary.value.trackPage,
        pageSize = TRACK_PAGE_SIZE,
        fetch = { page -> activeCatalog.tracks(page, TRACK_PAGE_SIZE) },
        apply = { state, items, page, more ->
            state.copy(tracks = (state.tracks + items).distinctBy(Track::id), trackPage = page,
                hasMoreTracks = more, loadingMoreTracks = false)
        },
    )

    fun loadMoreFavorites() = loadMoreLibraryPage(
        loading = mutableLibrary.value.loadingMoreFavorites || !mutableLibrary.value.hasMoreFavorites,
        markLoading = { copy(loadingMoreFavorites = true) },
        page = mutableLibrary.value.favoritePage,
        pageSize = FAVORITE_PAGE_SIZE,
        fetch = { page -> activeCatalog.favorites(page, FAVORITE_PAGE_SIZE) },
        apply = { state, items, page, more ->
            state.copy(favorites = (state.favorites + items).distinctBy(Track::id), favoritePage = page,
                hasMoreFavorites = more, loadingMoreFavorites = false)
        },
    )

    fun loadMorePlaylistTracks() {
        val state = mutableLibrary.value
        val playlist = state.selectedPlaylist ?: return
        if (state.loadingMorePlaylistTracks || !state.hasMorePlaylistTracks) return
        mutableLibrary.value = state.copy(loadingMorePlaylistTracks = true)
        viewModelScope.launch {
            try {
                val next = activeCatalog.playlistTracks(playlist.id, state.playlistTrackPage + 1, PLAYLIST_TRACK_PAGE_SIZE)
                mutableLibrary.value = mutableLibrary.value.copy(
                    selectedTracks = (mutableLibrary.value.selectedTracks + next).distinctBy(Track::id),
                    playlistTrackPage = state.playlistTrackPage + 1,
                    hasMorePlaylistTracks = next.size >= PLAYLIST_TRACK_PAGE_SIZE,
                    loadingMorePlaylistTracks = false,
                )
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    loadingMorePlaylistTracks = false, selectionError = libraryError(error),
                )
            }
        }
    }

    fun loadMoreArtistAlbums() {
        val state = mutableLibrary.value
        val artist = state.selectedArtist ?: return
        if (state.loadingMoreArtistAlbums || !state.hasMoreArtistAlbums) return
        mutableLibrary.value = state.copy(loadingMoreArtistAlbums = true)
        viewModelScope.launch {
            try {
                val next = activeCatalog.artistAlbums(artist.id, state.artistAlbumPage + 1, ARTIST_ALBUM_PAGE_SIZE)
                mutableLibrary.value = mutableLibrary.value.copy(
                    artistAlbums = (mutableLibrary.value.artistAlbums + next).distinctBy(Album::id),
                    artistAlbumPage = state.artistAlbumPage + 1,
                    hasMoreArtistAlbums = next.size >= ARTIST_ALBUM_PAGE_SIZE,
                    loadingMoreArtistAlbums = false,
                )
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    loadingMoreArtistAlbums = false, selectionError = libraryError(error),
                )
            }
        }
    }

    fun loadMoreArtistTracks() {
        val state = mutableLibrary.value
        val artist = state.selectedArtist ?: return
        if (state.loadingMoreArtistTracks || !state.hasMoreArtistTracks) return
        mutableLibrary.value = state.copy(loadingMoreArtistTracks = true)
        viewModelScope.launch {
            try {
                val next = activeCatalog.artistTracks(artist.id, state.artistTrackPage + 1, ARTIST_TRACK_PAGE_SIZE)
                mutableLibrary.value = mutableLibrary.value.copy(
                    artistTracks = (mutableLibrary.value.artistTracks + next).distinctBy(Track::id),
                    artistTrackPage = state.artistTrackPage + 1,
                    hasMoreArtistTracks = next.size >= ARTIST_TRACK_PAGE_SIZE,
                    loadingMoreArtistTracks = false,
                )
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    loadingMoreArtistTracks = false, selectionError = libraryError(error),
                )
            }
        }
    }

    fun loadMoreSearch() {
        val state = mutableLibrary.value
        if (state.loadingMoreSearch || !state.hasMoreSearch || state.searchQuery.isBlank()) return
        mutableLibrary.value = state.copy(loadingMoreSearch = true)
        viewModelScope.launch {
            try {
                val next = activeCatalog.searchDomain(state.searchQuery.trim(), state.searchPage + 1, SEARCH_PAGE_SIZE)
                val merged = state.search.copy(
                    tracks = (state.search.tracks + next.tracks).distinctBy(Track::id),
                    albums = (state.search.albums + next.albums).distinctBy(Album::id),
                    artists = (state.search.artists + next.artists).distinctBy(Artist::id),
                    playlists = (state.search.playlists + next.playlists).distinctBy(Playlist::id),
                    totalCount = maxOf(state.search.totalCount, next.totalCount),
                )
                val loaded = merged.tracks.size + merged.albums.size + merged.artists.size + merged.playlists.size
                mutableLibrary.value = mutableLibrary.value.copy(
                    search = merged, searchPage = state.searchPage + 1,
                    hasMoreSearch = merged.totalCount > loaded ||
                        listOf(next.tracks.size, next.albums.size, next.artists.size, next.playlists.size)
                            .any { it >= SEARCH_PAGE_SIZE },
                    loadingMoreSearch = false,
                )
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    loadingMoreSearch = false, error = libraryError(error),
                )
            }
        }
    }

    private fun <T> loadMoreLibraryPage(
        loading: Boolean,
        markLoading: LibraryUiState.() -> LibraryUiState,
        page: Int,
        pageSize: Int,
        fetch: suspend (Int) -> List<T>,
        apply: (LibraryUiState, List<T>, Int, Boolean) -> LibraryUiState,
    ) {
        if (loading) return
        mutableLibrary.value = mutableLibrary.value.markLoading()
        viewModelScope.launch {
            try {
                val items = fetch(page + 1)
                mutableLibrary.value = apply(mutableLibrary.value, items, page + 1, items.size >= pageSize)
            } catch (error: Exception) {
                mutableLibrary.value = mutableLibrary.value.copy(
                    loadingMoreAlbums = false, loadingMoreArtists = false, loadingMorePlaylists = false,
                    loadingMoreTracks = false, loadingMoreFavorites = false, error = libraryError(error),
                )
            }
        }
    }

    private fun libraryError(error: Throwable): String = when (error) {
        is org.melodee.android.live.auth.NotSignedInException -> "Sign in to your Melodee server to browse your library."
        is org.melodee.android.live.auth.ReauthenticationRequiredException -> "Your session expired. Sign in again."
        is org.melodee.android.live.auth.SessionOutcomeUnknownException ->
            "Sign-in could not be confirmed. Sign in again to recover."
        is java.io.IOException -> "Could not reach the Melodee server."
        else -> "The Melodee catalog could not be retrieved."
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
