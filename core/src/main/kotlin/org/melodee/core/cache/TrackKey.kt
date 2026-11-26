package org.melodee.core.cache

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Identifies audio bytes, independently of the expiring URL used to retrieve them.
 *
 * [serverId] and [accountId] must be stable identifiers, never access tokens or URLs
 * containing credentials. [revision] identifies the actual audio asset; [rendition]
 * separates, for example, an original FLAC from a transcoded AAC representation.
 */
data class TrackKey(
    val serverId: String,
    val accountId: String,
    val trackId: String,
    val revision: String,
    val rendition: String = "original",
) {
    init {
        require(listOf(serverId, accountId, trackId, revision, rendition).all(String::isNotBlank))
    }

    /** Length framing prevents ambiguous field concatenation. No secrets enter the key. */
    fun cacheKey(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf("melodee-audio-v1", serverId, accountId, trackId, revision, rendition).forEach {
            val bytes = it.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return "audio-v1-" + digest.digest().joinToString("") { "%02x".format(it) }
    }
}
