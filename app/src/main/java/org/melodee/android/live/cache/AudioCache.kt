package org.melodee.android.live.cache

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.melodee.android.live.api.MelodeeApi
import org.melodee.android.live.auth.LiveSessionManager
import org.melodee.android.live.auth.NotSignedInException
import org.melodee.core.cache.TrackKey

/**
 * One bounded, app-private audio cache shared by playback and prefetch.
 * Cache keys are stable track identities, never signed URLs. A cache hit is
 * served before resolving a new transport URL; a miss resolves a fresh signed
 * URL, which also covers IP/network changes.
 */
@UnstableApi
class AudioCache(
    context: Context,
    private val session: LiveSessionManager,
    private val api: MelodeeApi,
    maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val cache: Cache = SimpleCache(
        File(context.cacheDir, "audio-cache"),
        LeastRecentlyUsedCacheEvictor(maxBytes),
        StandaloneDatabaseProvider(context),
    )

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Melodee-Android/${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
        .setAllowCrossProtocolRedirects(false)

    private val defaultFactory = DefaultDataSource.Factory(context, httpFactory)

    private val resolvingFactory = ResolvingDataSource.Factory(defaultFactory) { dataSpec ->
        if (dataSpec.uri.scheme != MELODEE_SCHEME) dataSpec else {
            val trackId = dataSpec.uri.pathSegments.lastOrNull()
                ?: throw IOException("Malformed media URI")
            val resolved = try {
                runBlocking {
                    withTimeout(20_000) {
                        session.authorized { origin, token -> api.streamUrl(origin, token, trackId) }
                    }
                }
            } catch (error: NotSignedInException) {
                throw IOException("Sign in to resolve this track", error)
            } catch (error: Exception) {
                throw IOException("Could not resolve a fresh stream URL", error)
            }
            dataSpec.withUri(resolved.toString().toUri())
        }
    }

    private val cacheKeyFactory = CacheKeyFactory { dataSpec ->
        keyFor(dataSpec)
    }

    private val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolvingFactory)
        .setCacheKeyFactory(cacheKeyFactory)

    val dataSourceFactory: DataSource.Factory = cacheDataSourceFactory

    fun createPrefetchDataSource(): CacheDataSource = cacheDataSourceFactory.createDataSource()

    val mediaSourceFactory: MediaSource.Factory = DefaultMediaSourceFactory(dataSourceFactory)

    fun cachedLength(key: String, position: Long, length: Long): Long = cache.getCachedLength(key, position, length)

    fun contentLength(key: String): Long =
        androidx.media3.datasource.cache.ContentMetadata.getContentLength(cache.getContentMetadata(key))

    fun cachedBytes(key: String): Long {
        val length = contentLength(key).takeIf { it > 0 } ?: Long.MAX_VALUE
        return cache.getCachedBytes(key, 0, length).coerceAtLeast(0)
    }

    fun isCached(key: String, position: Long, length: Long): Boolean = cache.isCached(key, position, length)

    fun cachedSpace(): Long = cache.getCacheSpace()

    /** Logout/account switch removes account audio; untracked URIs are not account data. */
    fun clearAccountAudio() {
        cache.keys.filter { it.startsWith("audio-v1-") }.forEach(cache::removeResource)
    }

    fun cacheKey(serverId: String, accountId: String, trackId: String, revision: String, bitrateKbps: Int): String =
        TrackKey(serverId, accountId, trackId, revision, "bitrate-${bitrateKbps.coerceAtLeast(0)}").cacheKey()

    private fun keyFor(dataSpec: DataSpec): String {
        val uri = dataSpec.uri
        if (uri.scheme != MELODEE_SCHEME) return "untracked-" + uri.toString()
        val trackId = uri.pathSegments.lastOrNull() ?: return "invalid-" + uri.toString()
        val account = runBlocking { session.currentAccount() } ?: return "unscoped-" + uri.toString()
        val revision = uri.getQueryParameter("rev") ?: "unknown"
        val bitrate = uri.getQueryParameter("bitrate")?.toIntOrNull() ?: 0
        return cacheKey(account.serverId, account.accountId, trackId, revision, bitrate)
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 1_024L * 1_024L * 1_024L
        const val MELODEE_SCHEME = "melodee"
    }
}
