package org.melodee.android.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.melodee.android.MainActivity
import org.melodee.android.MelodeeApplication
import org.melodee.android.data.PlaybackBookmark

/** The sole player owner. The Activity, car host and notification are session clients. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val app get() = application as MelodeeApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bookmarkMutex = Mutex()
    private val initialStateReady = CompletableDeferred<Unit>()
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private var rememberPosition = false

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()

        scope.launch {
            try {
                // Preferences must be known before restoring or writing any queue.
                val settings = app.preferences.settings.first()
                rememberPosition = settings.rememberPosition
                player.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
                val saved = bookmarkMutex.withLock {
                    if (rememberPosition) app.database.playbackDao().load()
                    else { app.database.playbackDao().clear(); null }
                }
                // A new controller request wins over state loaded asynchronously.
                if (saved != null && player.mediaItemCount == 0) restore(saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w("MelodeePlayback", "Playback state could not be restored")
            } finally {
                initialStateReady.complete(Unit)
            }
            try {
                app.preferences.settings.collect { settings ->
                    rememberPosition = settings.rememberPosition
                    player.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
                    if (!rememberPosition) bookmarkMutex.withLock { app.database.playbackDao().clear() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w("MelodeePlayback", "Playback preferences could not be observed")
            }
        }
        scope.launch {
            initialStateReady.await()
            while (isActive) {
                delay(2_000)
                saveBookmark()
            }
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (initialStateReady.isCompleted && events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
                    )) scope.launch { saveBookmark() }
            }
        })
    }

    private suspend fun saveBookmark() {
        try {
            bookmarkMutex.withLock {
                // Capture after locking so an old save cannot overwrite a newer queue
                // or a preference-driven clear.
                if (!rememberPosition) return@withLock
                if (player.mediaItemCount == 0) app.database.playbackDao().clear()
                else app.database.playbackDao().save(snapshot())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("MelodeePlayback", "Playback state could not be saved")
        }
    }

    private fun snapshot(): PlaybackBookmark {
        val order = mutableListOf<Int>()
        val visited = mutableSetOf<Int>()
        val timeline = player.currentTimeline
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && order.size < player.mediaItemCount && visited.add(index)) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return PlaybackBookmark(
            trackIds = (0 until player.mediaItemCount).joinToString("|") { player.getMediaItemAt(it).mediaId },
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            shuffleOrder = order.joinToString(","),
        )
    }

    private fun restore(saved: PlaybackBookmark) {
        val restored = resolveBookmark(saved) ?: return
        player.setMediaItems(restored.mediaItems, restored.startIndex, restored.startPositionMs)
        restoreModes(saved, restored.mediaItems.size)
        player.prepare()
        // Never forces autoplay. A user or trusted controller must request play.
    }

    private fun restoreModes(saved: PlaybackBookmark?, itemCount: Int) {
        val order = saved?.shuffleOrder?.split(',')?.mapNotNull(String::toIntOrNull).orEmpty()
        if (player.mediaItemCount == itemCount && order.size == itemCount && order.toSet() == (0 until itemCount).toSet()) {
            player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), 0L))
        }
        player.shuffleModeEnabled = saved?.shuffle ?: false
        player.repeatMode = saved?.repeatMode?.takeIf {
            it in setOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL)
        } ?: Player.REPEAT_MODE_OFF
    }

    private fun resolveBookmark(saved: PlaybackBookmark): MediaSession.MediaItemsWithStartPosition? {
        val surviving = saved.trackIds.split('|').mapIndexedNotNull { index, id ->
            app.catalog.track(id)?.let { index to app.catalog.mediaItem(it) }
        }
        if (surviving.isEmpty()) return null
        val selected = surviving.indexOfFirst { it.first >= saved.currentIndex }
            .takeIf { it >= 0 } ?: surviving.lastIndex
        val position = if (surviving[selected].first == saved.currentIndex) saved.positionMs.coerceAtLeast(0) else 0
        return MediaSession.MediaItemsWithStartPosition(surviving.map { it.second }, selected, position)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.uid != Process.myUid() && !controller.isTrusted &&
                !session.isMediaNotificationController(controller)) {
                return MediaSession.ConnectionResult.reject()
            }
            return super.onConnect(session, controller)
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            val styles = Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            }
            // Media3 advertises legacy search support from available library commands.
            val rootParams = LibraryParams.Builder().setExtras(styles).build()
            return Futures.immediateFuture(LibraryResult.ofItem(app.catalog.node("root", "Melodee"), rootParams))
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            if (browseNode(parentId) == null) Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            else itemPage(app.catalog.children(parentId), page, pageSize, params)

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val item = app.catalog.track(mediaId)?.let(app.catalog::mediaItem) ?: browseNode(mediaId)
            return Futures.immediateFuture(if (item == null)
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else LibraryResult.ofItem(item, null))
        }

        private fun browseNode(id: String): MediaItem? = when (id) {
            "root" -> app.catalog.node(id, "Melodee")
            "home" -> app.catalog.node(id, "Home")
            "albums" -> app.catalog.node(id, "Albums")
            "playlists" -> app.catalog.node(id, "Playlists")
            else -> when {
                id.startsWith("album:") -> app.catalog.album(id.substringAfter(':'))?.let {
                    app.catalog.node(id, it.title, it.artist, it.id)
                }
                id.startsWith("playlist:") -> app.catalog.playlist(id.substringAfter(':'))?.let {
                    app.catalog.node(id, it.title, it.subtitle, "afterglow")
                }
                else -> null
            }
        }

        private fun itemPage(items: List<MediaItem>, page: Int, pageSize: Int, params: LibraryParams?):
            ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (page < 0 || pageSize < 1) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            val offset = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
            return Futures.immediateFuture(LibraryResult.ofItemList(items.drop(offset).take(pageSize), params))
        }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, search(query).size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = itemPage(search(query), page, pageSize, params)

        private fun search(query: String): List<MediaItem> {
            val playlist = app.catalog.playlists.firstOrNull { it.title.equals(query.trim(), ignoreCase = true) }
            return playlist?.let { app.catalog.items(it.trackIds) }
                ?: app.catalog.search(query).map(app.catalog::mediaItem)
        }

        override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(resolve(mediaItems))

        override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // A car browser selects one track ID. Keep its album queued. Explicit lists
            // from the phone retain exactly the requested queue.
            val selected = mediaItems.singleOrNull()?.takeIf { it.requestMetadata.searchQuery == null }
                ?.let { app.catalog.track(it.mediaId) }
            if (controller.uid != Process.myUid() && selected != null) {
                val album = app.catalog.items(app.catalog.album(selected.albumId)?.trackIds.orEmpty())
                if (album.isNotEmpty()) return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(
                    album, album.indexOfFirst { it.mediaId == selected.id }.coerceAtLeast(0),
                    normalizedPosition(startPositionMs),
                ))
            }
            val resolved = resolve(mediaItems)
            if (resolved.isEmpty() && mediaItems.isNotEmpty()) {
                return Futures.immediateFailedFuture(IllegalArgumentException("No matching media"))
            }
            val index = if (resolved.isEmpty()) 0 else startIndex.coerceIn(resolved.indices)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(resolved, index,
                normalizedPosition(startPositionMs)))
        }

        private fun normalizedPosition(position: Long) = if (position == C.TIME_UNSET) 0L else position.coerceAtLeast(0)

        private fun resolve(items: List<MediaItem>): List<MediaItem> = items.flatMap { item ->
            val query = item.requestMetadata.searchQuery
            when {
                query != null -> search(query)
                item.mediaId.startsWith("album:") -> app.catalog.items(
                    app.catalog.album(item.mediaId.substringAfter(':'))?.trackIds.orEmpty())
                item.mediaId.startsWith("playlist:") -> app.catalog.items(
                    app.catalog.playlist(item.mediaId.substringAfter(':'))?.trackIds.orEmpty())
                else -> listOfNotNull(app.catalog.track(item.mediaId)?.let(app.catalog::mediaItem))
            }
        }

        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo):
            ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val job = scope.launch {
                try {
                    initialStateReady.await()
                    val saved = if (player.mediaItemCount > 0) snapshot() else bookmarkMutex.withLock {
                        if (rememberPosition) app.database.playbackDao().load() else null
                    }
                    val restored = saved?.let(::resolveBookmark)
                    val fallback = app.catalog.albums.firstOrNull()?.let { app.catalog.items(it.trackIds) }.orEmpty()
                    val request = restored ?: MediaSession.MediaItemsWithStartPosition(fallback, 0, 0)
                    // Existing queue already has its player modes; avoid regenerating shuffle.
                    if (player.mediaItemCount == 0) restoreModes(saved, request.mediaItems.size)
                    result.set(request)
                } catch (cancelled: CancellationException) {
                    result.cancel(false)
                    throw cancelled
                } catch (error: Exception) {
                    result.setException(error)
                }
            }
            job.invokeOnCompletion { if (it != null && !result.isDone) result.cancel(false) }
            result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
            return result
        }
    }
}
