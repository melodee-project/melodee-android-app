package org.melodee.android.live.prefetch

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.melodee.android.catalog.LiveMediaItems
import org.melodee.android.live.auth.LiveSessionManager
import org.melodee.android.live.cache.AudioCache
import org.melodee.android.model.Track
import org.melodee.core.auth.AccountScope
import org.melodee.core.cache.CachedTrack

/** Media3 CacheWriter implementation. The executor owns cancellation/generation checks. */
@UnstableApi
class Media3TrackDownloader(
    private val audioCache: AudioCache,
    private val session: LiveSessionManager,
) : TrackDownloader {
    override suspend fun cached(track: Track): CachedTrack? {
        val key = keyFor(track) ?: return null
        val bytes = audioCache.cachedBytes(key)
        if (bytes <= 0) return null
        val length = audioCache.contentLength(key)
        return CachedTrack(bytes, length > 0 && audioCache.isCached(key, 0, length))
    }

    override suspend fun download(track: Track, maxBytes: Long, onBytes: (Long) -> Unit): Long {
        val key = keyFor(track) ?: return 0
        val dataSource = audioCache.createPrefetchDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(LiveMediaItems.trackUri(track).toUri())
            .setKey(key)
            .build()
        var writer: CacheWriter? = null
        var cancelledForBudget = false
        val progress = CacheWriter.ProgressListener { requestLength, _, newBytesCached ->
            if (requestLength > maxBytes) {
                cancelledForBudget = true
                writer?.cancel()
            } else {
                onBytes(newBytesCached)
            }
        }
        writer = CacheWriter(dataSource, dataSpec, ByteArray(BUFFER_BYTES), progress)
        try {
            withContext(Dispatchers.IO) { writer.cache() }
        } finally {
            dataSource.close()
        }
        return if (cancelledForBudget) 0 else audioCache.cachedBytes(key)
    }

    private suspend fun keyFor(track: Track): String? {
        val account = session.currentAccount() ?: return null
        val scope = AccountScope(account.serverId, account.accountId)
        return audioCache.cacheKey(scope.serverId, scope.accountId, track.id, track.revision, track.bitrateKbps)
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
