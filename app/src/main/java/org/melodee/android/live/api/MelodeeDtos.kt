package org.melodee.android.live.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
private fun Decoder.element(): JsonElement? =
    (this as? JsonDecoder)?.decodeJsonElement()

private fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.contentOrNull

/**
 * The Melodee contract renders several numeric fields as either JSON numbers or
 * strings. Accept both instead of failing a whole catalog response for a
 * formatting quirk.
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MelodeeFlexibleInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        val element = decoder.element() ?: return decoder.decodeInt()
        return element.asText()?.toDoubleOrNull()?.toInt() ?: 0
    }
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MelodeeFlexibleLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long {
        val element = decoder.element() ?: return decoder.decodeLong()
        return element.asText()?.toDoubleOrNull()?.toLong() ?: 0L
    }
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MelodeeFlexibleDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double {
        val element = decoder.element() ?: return decoder.decodeDouble()
        return element.asText()?.toDoubleOrNull() ?: 0.0
    }
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

@Serializable
data class LoginRequestDto(
    val userName: String,
    val password: String,
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
    val deviceId: String? = null,
)

@Serializable
data class SearchRequestDto(
    val query: String,
    @Serializable(with = FlexibleIntSerializer::class) val pageSize: Int = 10,
    @Serializable(with = FlexibleIntSerializer::class) val artistPage: Int = 1,
    @Serializable(with = FlexibleIntSerializer::class) val albumPage: Int = 1,
    @Serializable(with = FlexibleIntSerializer::class) val songPage: Int = 1,
)

@Serializable
data class UserDto(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val isAdmin: Boolean = false,
    val isEditor: Boolean = false,
    val roles: List<String> = emptyList(),
    @Serializable(with = FlexibleIntSerializer::class) val songsPlayed: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val songsLiked: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class ArtistDto(
    val id: String = "",
    val thumbnailUrl: String = "",
    val imageUrl: String = "",
    val name: String = "",
    val userStarred: Boolean = false,
    @Serializable(with = FlexibleIntSerializer::class) val userRating: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val albumCount: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val songCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val biography: String? = null,
    val genres: List<String>? = null,
)

@Serializable
data class AlbumDto(
    val id: String = "",
    val artist: ArtistDto = ArtistDto(),
    val thumbnailUrl: String = "",
    val imageUrl: String = "",
    val name: String = "",
    @Serializable(with = FlexibleIntSerializer::class) val releaseYear: Int = 0,
    val userStarred: Boolean = false,
    @Serializable(with = FlexibleIntSerializer::class) val userRating: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val songCount: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class) val durationMs: Double = 0.0,
    val durationFormatted: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val description: String? = null,
    val genre: String? = null,
)

@Serializable
data class SongDto(
    val id: String = "",
    val artist: ArtistDto = ArtistDto(),
    val album: AlbumDto = AlbumDto(),
    val streamUrl: String = "",
    val thumbnailUrl: String = "",
    val imageUrl: String = "",
    val title: String = "",
    @Serializable(with = FlexibleDoubleSerializer::class) val durationMs: Double = 0.0,
    val durationFormatted: String = "",
    val userStarred: Boolean = false,
    @Serializable(with = FlexibleIntSerializer::class) val userRating: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val songNumber: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val bitrate: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val playCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val genre: String? = null,
)

@Serializable
data class PlaylistDto(
    val id: String = "",
    val thumbnailUrl: String = "",
    val imageUrl: String = "",
    val name: String = "",
    val description: String = "",
    @Serializable(with = FlexibleDoubleSerializer::class) val durationMs: Double = 0.0,
    val durationFormatted: String = "",
    @Serializable(with = FlexibleIntSerializer::class) val songCount: Int = 0,
    val isPublic: Boolean = false,
    val owner: UserDto? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class PaginationMetaDto(
    @Serializable(with = FlexibleIntSerializer::class) val totalCount: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val pageSize: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class) val currentPage: Int = 1,
    @Serializable(with = FlexibleIntSerializer::class) val totalPages: Int = 1,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

@Serializable
data class PagedDto<T>(
    val meta: PaginationMetaDto = PaginationMetaDto(),
    val data: List<T> = emptyList(),
)

@Serializable
data class SearchResultDto(
    @Serializable(with = FlexibleIntSerializer::class) val totalCount: Int = 0,
    val artists: List<ArtistDto> = emptyList(),
    @Serializable(with = FlexibleIntSerializer::class) val totalArtists: Int = 0,
    val albums: List<AlbumDto> = emptyList(),
    @Serializable(with = FlexibleIntSerializer::class) val totalAlbums: Int = 0,
    val songs: List<SongDto> = emptyList(),
    @Serializable(with = FlexibleIntSerializer::class) val totalSongs: Int = 0,
    val playlists: List<PlaylistDto> = emptyList(),
    @Serializable(with = FlexibleIntSerializer::class) val totalPlaylists: Int = 0,
)

@Serializable
data class SearchResponseDto(
    val meta: PaginationMetaDto = PaginationMetaDto(),
    val data: SearchResultDto = SearchResultDto(),
)

@Serializable
data class AuthenticationDto(
    val user: UserDto = UserDto(),
    val serverVersion: String = "",
    val token: String = "",
    val expiresAt: String = "",
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String? = null,
) {
    override fun toString(): String =
        "AuthenticationDto(userId=${user.id}, serverVersion=$serverVersion, credentials=<redacted>)"
}

@Serializable
data class ApiErrorDto(
    val code: String = "unknown",
    val message: String = "Request failed",
    val correlationId: String? = null,
) {
    override fun toString(): String = "ApiErrorDto(code=$code, correlationId=$correlationId)"
}

open class ApiException(
    val statusCode: Int,
    val code: String,
    override val message: String,
    val correlationId: String? = null,
) : Exception(message)

class UnauthorizedApiException(
    statusCode: Int,
    code: String,
    message: String,
    correlationId: String? = null,
) : ApiException(statusCode, code, message, correlationId)

class ApiProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
