package org.melodee.android.live.api

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MelodeeApiTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: ServerOrigin
    private lateinit var api: MelodeeApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        origin = OriginPolicy.normalize(server.url("/").toString(), allowInsecureLoopback = true).getOrThrow()
        api = MelodeeApi(OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build())
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `authenticate posts username and parses credentials without leaking them`() = runTest {
        server.enqueue(json("""
            {"user":{"id":"user-1","username":"alice"},
             "serverVersion":"1.0","token":"access-secret",
             "expiresAt":"2026-11-26T12:00:00Z",
             "refreshToken":"refresh-secret",
             "refreshTokenExpiresAt":"2026-12-26T12:00:00Z"}
        """))
        val result = api.authenticate(origin, "alice", "correct horse")
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/authenticate", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"userName\":\"alice\""))
        assertEquals("access-secret", result.token)
        assertEquals("refresh-secret", result.refreshToken)
        assertFalse(result.toString().contains("access-secret"))
        assertFalse(result.toString().contains("refresh-secret"))
    }

    @Test fun `albums accepts numeric strings and pagination metadata`() = runTest {
        server.enqueue(json("""
            {"meta":{"totalCount":"10","pageSize":"5","currentPage":1,"totalPages":2,"hasNext":true},
             "data":[{"id":"album-1","name":"Northern Lights","releaseYear":"2021",
               "artist":{"id":"artist-1","name":"Aurora"},"songCount":"3","durationMs":"7700"}]}
        """))
        val page = api.albums(origin, "token")
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/api/v1/albums?"))
        assertTrue(request.path!!.contains("pageSize=50"))
        assertEquals(1, page.data.size)
        assertEquals("Northern Lights", page.data.first().name)
        assertEquals(2021, page.data.first().releaseYear)
        assertEquals(3, page.data.first().songCount)
        assertEquals(10, page.meta.totalCount)
    }

    @Test fun `paged catalog and search send the requested page`() = runTest {
        server.enqueue(json("""{"meta":{"totalCount":0},"data":[]}"""))
        api.albums(origin, "token", page = 4, pageSize = 25)
        val albumsRequest = server.takeRequest()
        assertTrue(albumsRequest.path!!.contains("page=4"))
        assertTrue(albumsRequest.path!!.contains("pageSize=25"))

        server.enqueue(json("""{"meta":{},"data":{"artists":[],"albums":[],"songs":[],"playlists":[]}}"""))
        api.search(origin, "token", "Dune", pageSize = 10, page = 3)
        val searchRequest = server.takeRequest()
        val body = searchRequest.body.readUtf8()
        assertTrue(body.contains("\"artistPage\":3"))
        assertTrue(body.contains("\"albumPage\":3"))
        assertTrue(body.contains("\"songPage\":3"))
    }

    @Test fun `search sends the grouped POST request and parses all result types`() = runTest {
        server.enqueue(json("""
            {"meta":{"totalCount":2,"pageSize":20,"currentPage":1,"totalPages":1},
             "data":{"totalCount":2,"totalArtists":1,"totalAlbums":1,"totalSongs":0,"totalPlaylists":0,
               "artists":[{"id":"artist-1","name":"Dune"}],
               "albums":[{"id":"album-1","name":"Dune"}],
               "songs":[],"playlists":[]}}
        """))
        val result = api.search(origin, "token", "Dune", 20)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/search", request.path)
        assertTrue(request.body.readUtf8().contains("\"query\":\"Dune\""))
        assertEquals(1, result.data.artists.size)
        assertEquals(1, result.data.albums.size)
    }

    @Test fun `401 is recoverable while 403 is a permission failure`() = runTest {
        server.enqueue(json("""{"code":"access_token_expired","message":"expired"}""", 401))
        var unauthorized = false
        try { api.albums(origin, "old") } catch (error: UnauthorizedApiException) { unauthorized = true }
        assertTrue(unauthorized)

        server.enqueue(json("""{"code":"forbidden","message":"not allowed"}""", 403))
        var permission = false
        try { api.albums(origin, "token") } catch (error: ApiException) { permission = error !is UnauthorizedApiException }
        assertTrue(permission)
    }

    @Test fun `artist and album like mutations use their native routes`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.setAlbumStarred(origin, "token", "album-1", true)
        assertEquals("/api/v1/albums/starred/album-1/true", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(200))
        api.setArtistStarred(origin, "token", "artist-1", false)
        assertEquals("/api/v1/artists/starred/artist-1/false", server.takeRequest().path)
    }

    @Test fun `rating mutations use setrating routes and accept clear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.setAlbumRating(origin, "token", "album-1", 5)
        assertEquals("/api/v1/albums/setrating/album-1/5", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(200))
        api.setArtistRating(origin, "token", "artist-1", 0)
        assertEquals("/api/v1/artists/setrating/artist-1/0", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(200))
        api.setSongRating(origin, "token", "track-1", 3)
        assertEquals("/api/v1/songs/setrating/track-1/3", server.takeRequest().path)
    }

    @Test fun `stream URL must stay on the configured origin`() = runTest {
        server.enqueue(json("""{"id":"track-1","streamUrl":"/song/stream/track-1/user-1/token"}"""))
        val url = api.streamUrl(origin, "token", "track-1")
        assertTrue(url.toString().contains("/song/stream/track-1/user-1/token"))

        server.enqueue(json("""{"id":"track-1","streamUrl":"https://evil.example/song"}"""))
        var rejected = false
        try { api.streamUrl(origin, "token", "track-1") } catch (_: ApiProtocolException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun `refresh classifies a rejection and marks the request as written`() = runTest {
        server.enqueue(json("""{"code":"refresh_token_replayed","message":"revoked"}""", 401))
        val result = api.refreshToken(origin, "refresh-old", "device")
        assertTrue(result is RefreshCallResult.Failure)
        result as RefreshCallResult.Failure
        assertEquals(401, result.statusCode)
        assertTrue(result.requestWritten)
    }

    @Test fun `refresh classifies a pre-send network failure as retryable`() = runTest {
        server.shutdown()
        val result = api.refreshToken(origin, "refresh-old", "device")
        assertTrue(result is RefreshCallResult.Network)
        result as RefreshCallResult.Network
        assertFalse(result.requestWritten)
    }

    @Test fun `origin policy rejects cleartext and credential-bearing origins`() {
        assertTrue(OriginPolicy.normalize("http://music.example", allowInsecureLoopback = false).isFailure)
        assertTrue(OriginPolicy.normalize("https://user:pass@music.example", allowInsecureLoopback = false).isFailure)
        val local = OriginPolicy.normalize("http://127.0.0.1:8080/", allowInsecureLoopback = true).getOrThrow()
        assertEquals("http://127.0.0.1:8080", local.id)
        assertNotNull(OriginPolicy.resolveSameOrigin(local, "/images/cover"))
    }

    @Test fun `origin-only input drives the native api path`() {
        val origin = OriginPolicy.normalize("https://music.example.test/", allowInsecureLoopback = false).getOrThrow()
        assertEquals("https://music.example.test", origin.id)
        assertEquals("https://music.example.test/", origin.baseUrl.toString())
        assertEquals(
            "https://music.example.test/api/v1/albums",
            OriginPolicy.resolveSameOrigin(origin, "/api/v1/albums").toString(),
        )
        assertTrue(OriginPolicy.normalize("https://music.example.test/api/v1", allowInsecureLoopback = false).isFailure)
    }

    private fun json(body: String, status: Int = 200): MockResponse = MockResponse()
        .setResponseCode(status)
        .addHeader("Content-Type", "application/json")
        .setBody(body.trimIndent())
}
