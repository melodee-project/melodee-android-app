package org.melodee.android.live.api

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Result of the rotating refresh call, including whether bytes may have reached the server. */
sealed interface RefreshCallResult {
    data class Success(val authentication: AuthenticationDto) : RefreshCallResult
    data class Failure(
        val statusCode: Int,
        val code: String,
        val message: String,
        val requestWritten: Boolean,
    ) : RefreshCallResult
    data class Network(val error: IOException, val requestWritten: Boolean) : RefreshCallResult
}

class MelodeeApi(
    private val client: OkHttpClient,
    private val json: kotlinx.serialization.json.Json = MelodeeJson,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun authenticate(origin: ServerOrigin, userName: String, password: String): AuthenticationDto {
        val body = json.encodeToString(LoginRequestDto(userName = userName, password = password))
        val request = Request.Builder()
            .url(origin.baseUrl.newBuilder().addPathSegments("api/v1/auth/authenticate").build())
            .post(body.toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()
        return jsonResponse(request, AuthenticationDto.serializer())
    }

    suspend fun refreshToken(
        origin: ServerOrigin,
        refreshToken: String,
        deviceId: String?,
    ): RefreshCallResult {
        val tracking = WriteTrackingListener()
        val refreshClient = client.newBuilder().eventListener(tracking).build()
        val body = json.encodeToString(RefreshTokenRequestDto(refreshToken, deviceId))
        val request = Request.Builder()
            .url(origin.baseUrl.newBuilder().addPathSegments("api/v1/auth/refresh-token").build())
            .post(body.toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()
        return withContext(Dispatchers.IO) {
            val response = try {
                refreshClient.newCall(request).await()
            } catch (error: IOException) {
                return@withContext RefreshCallResult.Network(error, tracking.requestWritten)
            }
            response.use {
                val text = it.body.string()
                if (it.isSuccessful) {
                    try {
                        RefreshCallResult.Success(json.decodeFromString(AuthenticationDto.serializer(), text))
                    } catch (error: SerializationException) {
                        RefreshCallResult.Network(IOException("Invalid refresh response", error), true)
                    }
                } else {
                    val error = parseError(it.code, text)
                    RefreshCallResult.Failure(
                        statusCode = it.code,
                        code = error.code,
                        message = error.message,
                        requestWritten = tracking.requestWritten,
                    )
                }
            }
        }
    }

    suspend fun revoke(origin: ServerOrigin, refreshToken: String, deviceId: String?) {
        val body = json.encodeToString(RefreshTokenRequestDto(refreshToken, deviceId))
        val request = Request.Builder()
            .url(origin.baseUrl.newBuilder().addPathSegments("api/v1/auth/revoke").build())
            .post(body.toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()
        executeNoContent(request)
    }

    suspend fun logout(origin: ServerOrigin, accessToken: String) {
        val request = authorized(
            origin, accessToken,
            origin.baseUrl.newBuilder().addPathSegments("api/v1/auth/logout").build(),
        ).post(ByteArray(0).toRequestBody(null)).build()
        executeNoContent(request)
    }

    suspend fun me(origin: ServerOrigin, accessToken: String): UserDto =
        jsonResponse(authorizedGet(origin, accessToken, "api/v1/user/me"), UserDto.serializer())

    suspend fun albums(
        origin: ServerOrigin,
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 50,
        query: String? = null,
    ): PagedDto<AlbumDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/albums") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
            addQueryParameter("orderBy", "Name")
            addQueryParameter("orderDirection", "asc")
            if (!query.isNullOrBlank()) addQueryParameter("q", query)
        },
        PagedDto.serializer(AlbumDto.serializer()),
    )

    suspend fun album(origin: ServerOrigin, accessToken: String, id: String): AlbumDto =
        jsonResponse(authorizedGet(origin, accessToken, "api/v1/albums/$id"), AlbumDto.serializer())

    suspend fun albumSongs(
        origin: ServerOrigin,
        accessToken: String,
        id: String,
        page: Int = 1,
        pageSize: Int = 100,
    ): PagedDto<SongDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/albums/$id/songs") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
        },
        PagedDto.serializer(SongDto.serializer()),
    )

    suspend fun artists(
        origin: ServerOrigin,
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 50,
        query: String? = null,
    ): PagedDto<ArtistDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/artists") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
            if (!query.isNullOrBlank()) addQueryParameter("q", query)
        },
        PagedDto.serializer(ArtistDto.serializer()),
    )

    suspend fun artist(origin: ServerOrigin, accessToken: String, id: String): ArtistDto =
        jsonResponse(authorizedGet(origin, accessToken, "api/v1/artists/$id"), ArtistDto.serializer())

    suspend fun artistAlbums(
        origin: ServerOrigin,
        accessToken: String,
        id: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): PagedDto<AlbumDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/artists/$id/albums") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
        },
        PagedDto.serializer(AlbumDto.serializer()),
    )

    suspend fun artistSongs(
        origin: ServerOrigin,
        accessToken: String,
        id: String,
        page: Int = 1,
        pageSize: Int = 100,
    ): PagedDto<SongDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/artists/$id/songs") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
        },
        PagedDto.serializer(SongDto.serializer()),
    )

    suspend fun songs(
        origin: ServerOrigin,
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 50,
        query: String? = null,
    ): PagedDto<SongDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/songs") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
            if (!query.isNullOrBlank()) addQueryParameter("q", query)
        },
        PagedDto.serializer(SongDto.serializer()),
    )

    suspend fun song(origin: ServerOrigin, accessToken: String, id: String): SongDto =
        jsonResponse(authorizedGet(origin, accessToken, "api/v1/songs/$id"), SongDto.serializer())

    suspend fun search(
        origin: ServerOrigin,
        accessToken: String,
        query: String,
        pageSize: Int = 20,
    ): SearchResponseDto {
        val body = json.encodeToString(
            SearchRequestDto(query = query, pageSize = pageSize, artistPage = 1, albumPage = 1, songPage = 1),
        )
        val request = authorized(
            origin, accessToken,
            origin.baseUrl.newBuilder().addPathSegments("api/v1/search").build(),
        ).post(body.toRequestBody(jsonMediaType)).build()
        return jsonResponse(request, SearchResponseDto.serializer())
    }

    suspend fun playlists(
        origin: ServerOrigin,
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): PagedDto<PlaylistDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/playlists") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
        },
        PagedDto.serializer(PlaylistDto.serializer()),
    )

    suspend fun playlist(origin: ServerOrigin, accessToken: String, id: String): PlaylistDto =
        jsonResponse(authorizedGet(origin, accessToken, "api/v1/playlists/$id"), PlaylistDto.serializer())

    suspend fun playlistSongs(
        origin: ServerOrigin,
        accessToken: String,
        id: String,
        page: Int = 1,
        pageSize: Int = 100,
    ): PagedDto<SongDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/playlists/$id/songs") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
        },
        PagedDto.serializer(SongDto.serializer()),
    )

    suspend fun setSongStarred(origin: ServerOrigin, accessToken: String, id: String, starred: Boolean) {
        val request = authorized(
            origin, accessToken,
            origin.baseUrl.newBuilder().addPathSegments("api/v1/songs/starred/$id/$starred").build(),
        ).post(ByteArray(0).toRequestBody(null)).build()
        executeNoContent(request)
    }

    suspend fun likedSongs(
        origin: ServerOrigin,
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 50,
    ): PagedDto<SongDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/user/songs/liked") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
        },
        PagedDto.serializer(SongDto.serializer()),
    )

    suspend fun recentlyPlayed(
        origin: ServerOrigin,
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): PagedDto<SongDto> = jsonResponse(
        authorizedGet(origin, accessToken, "api/v1/user/last-played") {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", pageSize.toString())
        },
        PagedDto.serializer(SongDto.serializer()),
    )

    /** Resolve the current signed transport URL. Never persist or log the returned value. */
    suspend fun streamUrl(origin: ServerOrigin, accessToken: String, trackId: String): HttpUrl {
        val song = song(origin, accessToken, trackId)
        return OriginPolicy.resolveSameOrigin(origin, song.streamUrl)
            ?: throw ApiProtocolException("Server returned a stream URL outside the configured origin")
    }

    suspend fun contentLength(url: HttpUrl, timeoutSeconds: Long = 10): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
        val scoped = client.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        scoped.newCall(request).await().use { response ->
            if (!response.isSuccessful) null
            else response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
        }
    }

    private fun authorizedGet(
        origin: ServerOrigin,
        accessToken: String,
        path: String,
        query: HttpUrl.Builder.() -> Unit = {},
    ): Request {
        val url = origin.baseUrl.newBuilder().addPathSegments(path).apply(query).build()
        return authorized(origin, accessToken, url).get().build()
    }

    private fun authorized(origin: ServerOrigin, accessToken: String, url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")

    private suspend fun <T> jsonResponse(
        request: Request,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        val response = client.newCall(request).await()
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) throw parseError(it.code, text)
            try {
                json.decodeFromString(deserializer, text)
            } catch (error: SerializationException) {
                throw ApiProtocolException("Could not parse Melodee response", error)
            }
        }
    }

    private suspend fun executeNoContent(request: Request) = withContext(Dispatchers.IO) {
        client.newCall(request).await().use {
            if (!it.isSuccessful) throw parseError(it.code, it.body.string())
        }
    }

    private fun parseError(status: Int, body: String): ApiException {
        val error = try {
            json.decodeFromString(ApiErrorDto.serializer(), body)
        } catch (_: SerializationException) {
            ApiErrorDto(code = if (status == 401) "unauthorized" else "http_$status", message = "Request failed")
        }
        return if (status == 401) {
            UnauthorizedApiException(status, error.code, error.message, error.correlationId)
        } else {
            // 403 is a permission decision, not proof that the access token is old.
            ApiException(status, error.code, error.message, error.correlationId)
        }
    }

    private class WriteTrackingListener : EventListener() {
        @Volatile var requestWritten = false

        override fun requestHeadersStart(call: Call) {
            requestWritten = true
        }

        override fun requestBodyStart(call: Call) {
            requestWritten = true
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
