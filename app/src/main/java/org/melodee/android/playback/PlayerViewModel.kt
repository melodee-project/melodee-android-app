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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.melodee.android.MelodeeApplication
import org.melodee.android.data.DemoTrack
import org.melodee.android.data.UserSettings

data class PlaybackUiState(
    val connected: Boolean = false,
    val current: DemoTrack? = null,
    val queue: List<DemoTrack> = emptyList(),
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

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MelodeeApplication
    val catalog get() = app.catalog
    val settings = app.preferences.settings.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5_000), UserSettings())
    private val mutableState = MutableStateFlow(PlaybackUiState())
    val state = mutableState.asStateFlow()
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { update() }
        override fun onPlayerError(error: PlaybackException) {
            mutableState.value = mutableState.value.copy(error = "Playback stopped. Try playing this track again.")
        }
    }
    init {
        controllerFuture.addListener({
            if (!controllerFuture.isCancelled) {
                try {
                    controller = controllerFuture.get().also { it.addListener(listener) }
                    update()
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(error = "Couldn't connect to the player. Reopen Melodee to retry.")
                }
            }
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            while (isActive) { delay(250); update() }
        }
    }
    private fun update() {
        val player = controller ?: return
        val track = player.currentMediaItem?.mediaId?.let(catalog::track)
        val order = mutableListOf<Int>()
        val timeline = player.currentTimeline
        var index = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
        while (index != C.INDEX_UNSET && index !in order && order.size < player.mediaItemCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        mutableState.value = mutableState.value.copy(
            connected = true, current = track,
            queue = (0 until player.mediaItemCount).mapNotNull { catalog.track(player.getMediaItemAt(it).mediaId) },
            queueOrder = order,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            isPlaying = player.isPlaying, isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: track?.durationMs ?: 0,
            shuffle = player.shuffleModeEnabled, repeatMode = player.repeatMode,
        )
    }
    fun play(ids: List<String>, index: Int = 0) {
        val items = catalog.items(ids)
        if (items.isEmpty()) return
        controller?.apply {
            setMediaItems(items, index.coerceIn(items.indices), 0)
            prepare()
            play()
        }
        clearError()
    }
    fun playAlbum(id: String, index: Int = 0) = play(catalog.album(id)?.trackIds.orEmpty(), index)
    fun playPlaylist(id: String) = play(catalog.playlist(id)?.trackIds.orEmpty())
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
    fun favorite(id: String) { viewModelScope.launch { app.preferences.toggleFavorite(id) } }
    fun rememberPosition(value: Boolean) { viewModelScope.launch { app.preferences.rememberPosition(value) } }
    fun pauseOnDisconnect(value: Boolean) { viewModelScope.launch { app.preferences.pauseOnDisconnect(value) } }
    fun clearQueue() { controller?.apply { stop(); clearMediaItems() }; viewModelScope.launch { app.database.playbackDao().clear() } }
    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }
    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}

