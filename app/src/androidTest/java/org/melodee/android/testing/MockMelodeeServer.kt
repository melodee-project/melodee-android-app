package org.melodee.android.testing

import android.content.Context
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/** Deterministic native-v1 fixture server for instrumentation tests; no credentials are real. */
class MockMelodeeServer(context: Context) : AutoCloseable {
    private val audioBytes = context.assets.open("audio/first-light.ogg").use { it.readBytes() }
    private val server = MockWebServer().apply {
        dispatcher = Dispatcher()
        start()
    }

    val url: String get() = server.url("/").toString()

    override fun close() {
        server.shutdown()
    }

    private inner class Dispatcher : okhttp3.mockwebserver.Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl?.encodedPath ?: return error("missing path")
            val method = request.method
            return when {
                method == "POST" && path == "/api/v1/auth/authenticate" -> json(authJson())
                method == "POST" && path == "/api/v1/auth/revoke" -> MockResponse().setResponseCode(200)
                method == "POST" && path == "/api/v1/songs/starred/track-1/true" -> json(songJson("track-1", "Solar Wind", starred = true))
                method == "POST" && path == "/api/v1/songs/starred/track-1/false" -> json(songJson("track-1", "Solar Wind", starred = false))
                path == "/api/v1/albums" -> json(albumsJson())
                path == "/api/v1/albums/album-1" -> json(albumJson())
                path == "/api/v1/albums/album-1/songs" -> json(songsJson())
                path == "/api/v1/songs/track-1" -> json(songJson("track-1", "Solar Wind"))
                path == "/api/v1/songs/track-2" -> json(songJson("track-2", "Aurora"))
                path == "/api/v1/songs" -> json(songsJson())
                path == "/api/v1/artists" -> json(emptyPage())
                path == "/api/v1/playlists" -> json(emptyPage())
                path == "/api/v1/user/songs/liked" -> json(likedJson())
                path == "/api/v1/user/last-played" -> json(songsJson())
                path == "/api/v1/search" -> json("""{"meta":{},"data":{"artists":[],"albums":[],"songs":[],"playlists":[]}}""")
                path.startsWith("/images/") -> MockResponse().setResponseCode(404)
                path.startsWith("/song/stream/") -> audio()
                else -> error("unexpected $method $path")
            }
        }

        private fun audio(): MockResponse = MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "audio/ogg")
            .addHeader("Accept-Ranges", "bytes")
            .setBody(Buffer().write(audioBytes))

        private fun error(message: String): MockResponse = MockResponse()
            .setResponseCode(404)
            .addHeader("Content-Type", "application/json")
            .setBody("""{"code":"not_found","message":"$message"}""")

        private fun json(body: String): MockResponse = MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(body.trimIndent())

        private fun emptyPage() = """{"meta":{"totalCount":0,"pageSize":50,"currentPage":1,"totalPages":0,"hasNext":false},"data":[]}"""
        private fun likedJson() = """{"meta":{"totalCount":0,"pageSize":50,"currentPage":1,"totalPages":0,"hasNext":false},"data":[]}"""

        private fun authJson() = """{"user":{"id":"user-1","username":"alice"},"serverVersion":"1",
            "token":"access-1","expiresAt":"2030-01-01T00:00:00Z","refreshToken":"refresh-1"}"""

        private fun albumsJson() = """{"meta":{"totalCount":1,"pageSize":50,"currentPage":1,"totalPages":1,"hasNext":false},
            "data":[{"id":"album-1","name":"Northern Lights","releaseYear":2021,
            "artist":{"id":"artist-1","name":"Aurora"},"imageUrl":"${url}images/album_1/640",
            "songCount":2,"durationMs":5000,"updatedAt":"2026-01-01 00:00:00"}]}"""

        private fun albumJson() = """{"id":"album-1","name":"Northern Lights","releaseYear":2021,
            "artist":{"id":"artist-1","name":"Aurora"},"imageUrl":"${url}images/album_1/640",
            "songCount":2,"durationMs":5000,"updatedAt":"2026-01-01 00:00:00"}"""

        private fun songsJson() = """{"meta":{"totalCount":2,"pageSize":100,"currentPage":1,"totalPages":1,"hasNext":false},
            "data":[${songJson("track-1", "Solar Wind")},${songJson("track-2", "Aurora")}]}"""

        private fun songJson(id: String, title: String, starred: Boolean = false) = """{"id":"$id","title":"$title","durationMs":32000,
            "songNumber":1,"bitrate":320,"artist":{"id":"artist-1","name":"Aurora"},
            "album":{"id":"album-1","name":"Northern Lights","artist":{"id":"artist-1","name":"Aurora"}},
            "imageUrl":"${url}images/song_$id/640","userStarred":$starred,
            "streamUrl":"/song/stream/$id/user-1/token","updatedAt":"2026-01-01 00:00:00"}"""
    }
}
