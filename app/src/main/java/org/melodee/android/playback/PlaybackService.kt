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
import androidx.media3.common.Timeline
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.melodee.android.MainActivity
import org.melodee.android.MelodeeApplication
import org.melodee.android.catalog.LiveMediaItems
import org.melodee.android.catalog.MusicCatalog
import org.melodee.android.model.PlaybackSnapshot
import org.melodee.android.model.QueueContext
import org.melodee.android.model.QueueEntry
import org.melodee.android.model.Track
import org.melodee.core.cache.PlaybackContext
import org.melodee.core.auth.AccountScope

/** The sole player owner. The Activity, car host and notification are session clients. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val app get() = application as MelodeeApplication
    private val container get() = app.container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bookmarkMutex = Mutex()
    private val initialStateReady = CompletableDeferred<Unit>()
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private var rememberPosition = false
    private var cachedCatalog: MusicCatalog? = null
    private var accountScope: AccountScope? = null
    private var queueContext = QueueContext.MANUAL
    private var queueContextId: String? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(container.audioCache.mediaSourceFactory)
            .build().apply {
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
                catalog()
                val account = container.session.currentAccount()
                accountScope = account?.let { AccountScope(it.serverId, it.accountId) }
                val saved = bookmarkMutex.withLock {
                    if (!rememberPosition) {
                        app.database.playbackDao().clear()
                        accountScope?.let { container.queueStore.clear(it) }
                        null
                    } else {
                        accountScope?.let { container.queueStore.load(it) }
                    }
                }
                // A new controller request wins over state loaded asynchronously.
                if (saved != null && player.mediaItemCount == 0) restoreLive(saved)
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
                    if (!rememberPosition) bookmarkMutex.withLock {
                        app.database.playbackDao().clear()
                        accountScope?.let { container.queueStore.clear(it) }
                    }
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
        scope.launch {
            container.sessionCleared.collect {
                cachedCatalog = null
                accountScope = null
                container.prefetchExecutor.cancel()
                player.stop()
                player.clearMediaItems()
            }
        }
        scope.launch {
            var previous: String? = null
            container.accountFlow.distinctUntilChanged().collect { account ->
                val current = account?.let { "${it.serverId}|${it.accountId}" }
                if (previous != current) {
                    container.prefetchExecutor.cancel()
                    cachedCatalog = null
                    accountScope = null
                    // Do not let a demo fixture queue survive a transition into a live account.
                    if (current != null && player.mediaItemCount > 0 &&
                        (0 until player.mediaItemCount).any {
                            !player.getMediaItemAt(it).mediaId.startsWith("live:")
                        }
                    ) {
                        player.stop()
                        player.clearMediaItems()
                    }
                }
                previous = current
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

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) schedulePrefetch() else container.prefetchExecutor.cancel()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                schedulePrefetch()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                schedulePrefetch()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                schedulePrefetch()
            }
        })
    }

    private fun schedulePrefetch() {
        val catalog = cachedCatalog ?: return
        if (!player.isPlaying || player.mediaItemCount == 0) {
            container.prefetchExecutor.cancel()
            return
        }
        val tracks = (0 until player.mediaItemCount).mapNotNull { index ->
            catalog.trackFromMediaItem(player.getMediaItemAt(index))
        }
        val context = when (queueContext) {
            QueueContext.ALBUM -> PlaybackContext.ALBUM
            QueueContext.PLAYLIST -> PlaybackContext.PLAYLIST
            QueueContext.ARTIST -> PlaybackContext.ARTIST
            QueueContext.RADIO -> PlaybackContext.RADIO
            QueueContext.MANUAL -> PlaybackContext.MANUAL
        }
        container.prefetchExecutor.schedule(tracks, player.currentMediaItemIndex.coerceAtLeast(0), context)
    }

    private suspend fun catalog(): MusicCatalog {
        cachedCatalog?.let { return it }
        val selected = container.activeCatalog()
        cachedCatalog = selected
        accountScope = container.session.currentAccount()?.let { AccountScope(it.serverId, it.accountId) }
        return selected
    }

    private suspend fun saveBookmark() {
        try {
            bookmarkMutex.withLock {
                // Capture after locking so an old save cannot overwrite a newer queue
                // or a preference-driven clear.
                if (!rememberPosition) return@withLock
                if (accountScope == null && container.session.currentAccount() == null) return@withLock
                val catalog = cachedCatalog ?: catalog()
                if (player.mediaItemCount == 0) {
                    app.database.playbackDao().clear()
                    accountScope?.let { container.queueStore.clear(it) }
                    return@withLock
                }
                saveLiveSnapshot()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("MelodeePlayback", "Playback state could not be saved")
        }
    }

    private suspend fun saveLiveSnapshot() {
        val account = accountScope ?: container.session.currentAccount()?.let {
            AccountScope(it.serverId, it.accountId).also { scope -> accountScope = scope }
        } ?: return
        val entries = (0 until player.mediaItemCount).mapNotNull { index ->
            val item = player.getMediaItemAt(index)
            val track = LiveMediaItems.trackFrom(item) ?: return@mapNotNull null
            QueueEntry(entryId = "$index:${item.mediaId}", track = track)
        }
        if (entries.isEmpty()) {
            container.queueStore.clear(account)
            return
        }
        container.queueStore.save(
            account,
            PlaybackSnapshot(
                entries = entries,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = player.currentPosition.coerceAtLeast(0),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                shuffleOrder = actualOrder(),
                context = queueContext,
                contextId = queueContextId,
            ),
        )
    }

    private fun actualOrder(): List<Int> {
        val order = mutableListOf<Int>()
        val visited = mutableSetOf<Int>()
        val timeline = player.currentTimeline
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && order.size < player.mediaItemCount && visited.add(index)) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return order
    }

    private fun restoreLive(saved: PlaybackSnapshot) {
        val items = saved.entries.map { LiveMediaItems.mediaItem(it.track) }
        if (items.isEmpty()) return
        val index = saved.currentIndex.coerceIn(items.indices)
        player.setMediaItems(items, index, saved.positionMs.coerceAtLeast(0))
        restoreModes(saved.shuffle, saved.repeatMode, saved.shuffleOrder, items.size)
        queueContext = saved.context
        queueContextId = saved.contextId
        player.prepare()
    }

    private fun restoreModes(shuffle: Boolean, repeatMode: Int, shuffleOrder: List<Int>, itemCount: Int) {
        if (player.mediaItemCount == itemCount && shuffleOrder.size == itemCount &&
            shuffleOrder.toSet() == (0 until itemCount).toSet()
        ) {
            player.setShuffleOrder(DefaultShuffleOrder(shuffleOrder.toIntArray(), 0L))
        }
        player.shuffleModeEnabled = shuffle
        player.repeatMode = repeatMode.takeIf {
            it in setOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL)
        } ?: Player.REPEAT_MODE_OFF
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
            val rootParams = LibraryParams.Builder().setExtras(styles).build()
            return future { LibraryResult.ofItem(catalog().root(), rootParams) }
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val selected = catalog()
            if (selected.item(parentId) == null) {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } else {
                val items = selected.children(parentId, page, pageSize)
                LibraryResult.ofItemList(items, params)
            }
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = future {
            catalog().item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = future {
            val count = catalog().searchCount(query)
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(catalog().search(query, page, pageSize), params)
        }

        override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> = future {
            resolveItems(catalog(), mediaItems)
        }

        override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val selected = catalog()
            val single = mediaItems.singleOrNull()?.takeIf { it.requestMetadata.searchQuery == null }
            if (controller.uid != Process.myUid() && single != null) {
                val expanded = selected.expandSelection(single.mediaId)
                if (!expanded.isNullOrEmpty()) {
                    queueContextId = single.mediaId
                    queueContext = when {
                        single.mediaId.contains("playlist") -> QueueContext.PLAYLIST
                        single.mediaId.contains("artist") -> QueueContext.ARTIST
                        else -> QueueContext.ALBUM
                    }
                    val items = selected.mediaItems(expanded)
                    if (items.isNotEmpty()) {
                        val selectedId = single.mediaId.removePrefix("live:track:")
                        val index = expanded.indexOf(selectedId).takeIf { it >= 0 } ?: 0
                        return@future MediaSession.MediaItemsWithStartPosition(
                            items, index, normalizedPosition(startPositionMs),
                        )
                    }
                }
            }
            val resolved = resolveItems(selected, mediaItems)
            if (resolved.isEmpty() && mediaItems.isNotEmpty()) {
                throw IllegalArgumentException("No matching media")
            }
            val index = if (resolved.isEmpty()) 0 else startIndex.coerceIn(resolved.indices)
            queueContext = QueueContext.MANUAL
            queueContextId = null
            MediaSession.MediaItemsWithStartPosition(resolved, index, normalizedPosition(startPositionMs))
        }

        override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo):
            ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            initialStateReady.await()
            if (player.mediaItemCount > 0) {
                val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                return@future MediaSession.MediaItemsWithStartPosition(
                    items, player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0),
                )
            }
            val selected = catalog()
            val account = accountScope ?: container.session.currentAccount()?.let {
                AccountScope(it.serverId, it.accountId).also { value -> accountScope = value }
            }
            val saved = account?.let { container.queueStore.load(it) }
            if (saved != null) {
                player.shuffleModeEnabled = saved.shuffle
                player.repeatMode = saved.repeatMode
                queueContext = saved.context
                queueContextId = saved.contextId
                MediaSession.MediaItemsWithStartPosition(
                    saved.entries.map { LiveMediaItems.mediaItem(it.track) },
                    saved.currentIndex.coerceIn(saved.entries.indices),
                    saved.positionMs.coerceAtLeast(0),
                )
            } else {
                val fallbackTracks = runCatching {
                    val album = selected.home().recentAlbums.firstOrNull()
                    if (album != null) selected.albumTracks(album.id) else selected.tracks(1, 25)
                }.getOrDefault(emptyList())
                MediaSession.MediaItemsWithStartPosition(
                    selected.mediaItems(fallbackTracks.map(Track::id)), 0, 0,
                )
            }
        }

        private fun normalizedPosition(position: Long) = if (position == C.TIME_UNSET) 0L else position.coerceAtLeast(0)

        private suspend fun resolveItems(catalog: MusicCatalog, items: List<MediaItem>): List<MediaItem> =
            items.flatMap { item ->
                val query = item.requestMetadata.searchQuery
                when {
                    query != null -> catalog.search(query, 1, 100)
                    item.mediaId.startsWith("live:track:") ->
                        catalog.mediaItems(listOf(item.mediaId.removePrefix("live:track:")))
                    item.mediaId.startsWith("live:album:") || item.mediaId.startsWith("live:playlist:") ||
                        item.mediaId.startsWith("live:artist:") || item.mediaId == "live:favorites" -> {
                        catalog.expandSelection(item.mediaId)?.let { catalog.mediaItems(it) }.orEmpty()
                    }
                    else -> catalog.mediaItems(listOf(item.mediaId))
                }
            }

        private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
            val result = SettableFuture.create<T>()
            val job = scope.launch {
                try {
                    result.set(block())
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
