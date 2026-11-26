package org.melodee.android.live.api

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

val MelodeeJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

data class ServerOrigin(val baseUrl: HttpUrl, val id: String) {
    override fun toString(): String = id
}

object OriginPolicy {
    /**
     * Requires HTTPS. Cleartext loopback is permitted only for debug builds so
     * emulator/MockWebServer integration tests can exercise the real transport.
     * Never accept credentials, query strings or fragments as part of an origin.
     */
    fun normalize(input: String, allowInsecureLoopback: Boolean): Result<ServerOrigin> {
        val raw = input.trim()
        if (raw.isEmpty()) return Result.failure(IllegalArgumentException("Enter a server address"))
        val url = raw.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a valid HTTPS server address"))
        if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null
        ) {
            return Result.failure(IllegalArgumentException("Enter only the server origin, without credentials, query or fragment"))
        }
        val loopback = url.host == "127.0.0.1" || url.host == "localhost" || url.host == "::1"
        if (url.scheme != "https" && !(allowInsecureLoopback && loopback && url.scheme == "http")) {
            return Result.failure(IllegalArgumentException("Melodee requires an HTTPS server address"))
        }
        val origin = url.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
        val id = buildString {
            append(origin.scheme).append("://").append(origin.host)
            if (origin.port != HttpUrl.defaultPort(origin.scheme)) append(':').append(origin.port)
        }
        return Result.success(ServerOrigin(origin, id))
    }

    /** Resolve an API or artwork URL only when it remains on the configured origin. */
    fun resolveSameOrigin(origin: ServerOrigin, value: String): HttpUrl? {
        val url = if (value.startsWith("http://") || value.startsWith("https://")) {
            value.toHttpUrlOrNull()
        } else {
            origin.baseUrl.resolve(value.trimStart('/'))
        } ?: return null
        if (url.scheme != origin.baseUrl.scheme || url.host != origin.baseUrl.host || url.port != origin.baseUrl.port) {
            return null
        }
        return url
    }

    /** Absolute remote artwork/stream URLs may be relative to the configured server. */
    fun absoluteSameOrigin(origin: ServerOrigin, value: String): String? =
        resolveSameOrigin(origin, value)?.toString()
}
