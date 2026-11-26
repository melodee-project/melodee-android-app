package org.melodee.android

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import org.melodee.android.catalog.DemoMusicCatalog
import org.melodee.android.catalog.LiveMusicCatalog
import org.melodee.android.catalog.MusicCatalog
import org.melodee.android.live.api.MelodeeApi
import org.melodee.android.live.auth.KeystoreSessionStore
import org.melodee.android.live.auth.LiveAccountStore
import org.melodee.android.live.auth.LiveSessionManager
import org.melodee.android.live.cache.AudioCache
import org.melodee.android.live.data.LiveCatalogCache
import org.melodee.android.live.data.LiveQueueStore
import org.melodee.android.live.prefetch.Media3TrackDownloader
import org.melodee.android.live.prefetch.PrefetchExecutor
import org.melodee.core.cache.PrefetchPolicy

/**
 * One process-owned object graph. No Activity, screen or MediaController owns a
 * token timer, HTTP client, audio cache or second player.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MelodeeContainer(private val app: MelodeeApplication) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    val api: MelodeeApi = MelodeeApi(httpClient)
    private val accountStore = LiveAccountStore(app)

    val accountFlow = accountStore.accountFlow

    private val sessionStore = KeystoreSessionStore(app)

    val sessionCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val session: LiveSessionManager = LiveSessionManager(
        api = api,
        store = sessionStore,
        accountStore = accountStore,
        scope = appScope,
        allowInsecureLoopback = BuildConfig.DEBUG,
        onSessionCleared = {
            runCatching { audioCache.clearAccountAudio() }
            runCatching { app.database.catalogCacheDao().clear() }
            runCatching { app.database.queueSnapshotDao().clear() }
            sessionCleared.tryEmit(Unit)
        },
    )

    val database get() = app.database
    val preferences get() = app.preferences

    val audioCache: AudioCache by lazy { AudioCache(app, session, api) }

    val catalogCache: LiveCatalogCache by lazy { LiveCatalogCache(app.database.catalogCacheDao()) }
    val queueStore: LiveQueueStore by lazy { LiveQueueStore(app.database.queueSnapshotDao()) }
    val liveCatalog: LiveMusicCatalog by lazy {
        LiveMusicCatalog(api, session, catalogCache, BuildConfig.DEBUG)
    }
    val demoCatalog: MusicCatalog = DemoMusicCatalog(app.catalog)
    val prefetchExecutor: PrefetchExecutor by lazy {
        PrefetchExecutor(appScope, Media3TrackDownloader(audioCache, session), PrefetchPolicy())
    }

    init {
        session.startProactiveRenewal()
    }

    /** Live data is selected once an account is present; otherwise the demo stays fully usable. */
    suspend fun activeCatalog(): MusicCatalog =
        if (session.currentAccount() != null) liveCatalog else demoCatalog
}
