package org.melodee.android.live.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.melodee.android.catalog.LiveMusicCatalog
import org.melodee.android.data.PlaybackDatabase
import org.melodee.android.live.api.MelodeeApi
import org.melodee.android.live.api.OriginPolicy
import org.melodee.android.live.auth.AccountMetadataStore
import org.melodee.android.live.auth.LiveAccount
import org.melodee.android.live.auth.LiveLoginResult
import org.melodee.android.live.auth.LiveSessionManager
import org.melodee.android.live.data.LiveCatalogCache
import org.melodee.android.live.data.LiveQueueStore
import org.melodee.android.model.Artwork
import org.melodee.android.model.CatalogMode
import org.melodee.android.model.PlaybackSnapshot
import org.melodee.android.model.QueueContext
import org.melodee.android.model.QueueEntry
import org.melodee.android.model.Track
import org.melodee.core.auth.AccountScope
import org.melodee.core.auth.SessionStore
import org.melodee.core.auth.StoredSession

@RunWith(AndroidJUnit4::class)
class LiveMusicCatalogTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var database: PlaybackDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var session: LiveSessionManager
    private lateinit var catalog: LiveMusicCatalog
    private lateinit var queueStore: LiveQueueStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().apply { start() }
        database = Room.inMemoryDatabaseBuilder(context, PlaybackDatabase::class.java)
            .allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val api = MelodeeApi(OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build())
        val accounts = MemoryAccountStore()
        session = LiveSessionManager(
            api = api,
            store = MemorySessionStore(),
            accountStore = accounts,
            scope = scope,
            allowInsecureLoopback = true,
            onSessionCleared = {},
        )
        catalog = LiveMusicCatalog(api, session, LiveCatalogCache(database.catalogCacheDao()), true)
        queueStore = LiveQueueStore(database.queueSnapshotDao())
    }

    @After fun tearDown() {
        scope.cancel()
        server.shutdown()
        database.close()
    }

    @Test fun loginThenAlbumSongsAndMediaItems() = runBlocking {
        server.enqueue(json(authJson()))
        assertTrue(session.login(server.url("/").toString(), "alice", "secret") is LiveLoginResult.Success)
        server.takeRequest()

        server.enqueue(json(albumsJson()))
        val albums = catalog.albums()
        assertEquals(1, albums.size)
        assertEquals("Northern Lights", albums.first().title)
        assertEquals(CatalogMode.LIVE, albums.first().mode)

        server.enqueue(json(albumSongsJson()))
        val tracks = catalog.albumTracks("album-1")
        assertEquals(2, tracks.size)
        assertEquals("Solar Wind", tracks.first().title)

        val items = catalog.mediaItems(tracks.map(Track::id))
        assertEquals(2, items.size)
        assertTrue(items.first().mediaId.startsWith("live:track:"))
        assertTrue(items.first().localConfiguration!!.uri.toString().startsWith("melodee://track/"))
        assertEquals("Northern Lights", items.first().mediaMetadata.albumTitle.toString())

        assertEquals(listOf("track-1", "track-2"), catalog.expandSelection("live:album:album-1"))
        assertNotNull(catalog.item("live:album:album-1"))
    }

    @Test fun cachedAlbumsRemainReadableWhenServerIsGone() = runBlocking {
        server.enqueue(json(authJson()))
        session.login(server.url("/").toString(), "alice", "secret")
        server.takeRequest()
        server.enqueue(json(albumsJson()))
        assertEquals(1, catalog.albums().size)
        server.shutdown()
        val cached = catalog.albums()
        assertEquals(1, cached.size)
        assertEquals("Northern Lights", cached.first().title)
    }

    @Test fun liveQueueSnapshotIsAccountScopedAndContainsNoSignedUrl() = runBlocking {
        val account = AccountScope("https://music.example", "user-1")
        val other = AccountScope("https://music.example", "user-2")
        val track = Track(
            id = "track-1", title = "Solar Wind", artist = "Aurora", albumId = "album-1",
            albumTitle = "Northern Lights", durationMs = 2500, bitrateKbps = 320,
            artwork = Artwork.Remote("https://music.example/images/song_1/640"),
            mode = CatalogMode.LIVE, revision = "rev-1",
        )
        val snapshot = PlaybackSnapshot(
            entries = listOf(QueueEntry("entry-1", track), QueueEntry("entry-2", track)),
            currentIndex = 1, positionMs = 900, shuffle = true, repeatMode = 2,
            shuffleOrder = listOf(1, 0), context = QueueContext.ALBUM, contextId = "album-1",
        )
        queueStore.save(account, snapshot)
        val loaded = queueStore.load(account)!!
        assertEquals(2, loaded.entries.size)
        assertEquals("entry-2", loaded.entries[1].entryId)
        assertEquals("track-1", loaded.entries[0].track.id)
        assertEquals("https://music.example/images/song_1/640", (loaded.entries[0].track.artwork as Artwork.Remote).url)
        assertEquals(1, loaded.currentIndex)
        assertNull(queueStore.load(other))
    }

    private fun json(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .addHeader("Content-Type", "application/json")
        .setBody(body.trimIndent())

    private fun authJson() = """{"user":{"id":"user-1","username":"alice"},"serverVersion":"1",
        "token":"access-1","expiresAt":"2030-01-01T00:00:00Z","refreshToken":"refresh-1"}"""

    private fun albumsJson() = """{"meta":{"totalCount":1,"pageSize":50,"currentPage":1,"totalPages":1,"hasNext":false},
        "data":[{"id":"album-1","name":"Northern Lights","releaseYear":2021,
        "artist":{"id":"artist-1","name":"Aurora"},"imageUrl":"https://music.example/images/album_1/640",
        "songCount":2,"durationMs":5000,"updatedAt":"2026-01-01 00:00:00"}]}"""

    private fun albumSongsJson() = """{"meta":{"totalCount":2,"pageSize":100,"currentPage":1,"totalPages":1,"hasNext":false},
        "data":[
        {"id":"track-1","title":"Solar Wind","durationMs":2500,"songNumber":1,"bitrate":320,
         "artist":{"id":"artist-1","name":"Aurora"},
         "album":{"id":"album-1","name":"Northern Lights","artist":{"id":"artist-1","name":"Aurora"}},
         "imageUrl":"https://music.example/images/song_1/640","streamUrl":"/song/stream/track-1/user-1/token",
         "updatedAt":"2026-01-01 00:00:00"},
        {"id":"track-2","title":"Aurora","durationMs":2500,"songNumber":2,"bitrate":320,
         "artist":{"id":"artist-1","name":"Aurora"},
         "album":{"id":"album-1","name":"Northern Lights","artist":{"id":"artist-1","name":"Aurora"}},
         "imageUrl":"https://music.example/images/song_2/640","streamUrl":"/song/stream/track-2/user-1/token",
         "updatedAt":"2026-01-01 00:00:00"}]}"""

    private class MemorySessionStore : SessionStore {
        var value: StoredSession? = null
        override suspend fun read() = value
        override suspend fun write(session: StoredSession?) { value = session }
    }

    private class MemoryAccountStore : AccountMetadataStore {
        private val value = MutableStateFlow<LiveAccount?>(null)
        override val accountFlow: Flow<LiveAccount?> = value
        override suspend fun current() = value.value
        override suspend fun save(metadata: LiveAccount) { value.value = metadata }
        override suspend fun clear() { value.value = null }
        override suspend fun deviceId() = "device-1"
    }
}
