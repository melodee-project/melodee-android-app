package org.melodee.android.live.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.melodee.android.live.api.MelodeeJson
import org.melodee.core.auth.AccountScope
import org.melodee.core.auth.PersistedRefreshState
import org.melodee.core.auth.SessionStore
import org.melodee.core.auth.StoredSession
import org.melodee.core.auth.TokenBundle

/**
 * Whole-record encrypted session storage. The AES key lives in Android Keystore;
 * only ciphertext is written to app-private, no-backup storage. The full record is
 * replaced with an atomic rename after an fsync, so a process death cannot expose
 * a half-written credential pair.
 */
class KeystoreSessionStore(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val fileName: String = "session.bin",
) : SessionStore {
    private val directory = context.noBackupFilesDir
    private val file = File(directory, fileName)

    override suspend fun read(): StoredSession? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val bytes = file.readBytes()
            val decoded = decode(bytes)
            val dto = MelodeeJson.decodeFromString(StoredSessionDto.serializer(), decoded.decodeToString())
            dto.toDomain()
        } catch (error: Exception) {
            // A deleted/invalidated Keystore key or corrupt record must fail closed.
            when (error) {
                is GeneralSecurityException, is SerializationException, is IllegalArgumentException,
                is java.io.IOException -> {
                    Log.w(TAG, "Stored session could not be read; clearing it (${error.javaClass.simpleName})")
                    file.delete()
                    null
                }
                else -> throw error
            }
        }
    }

    override suspend fun write(session: StoredSession?) = withContext(Dispatchers.IO) {
        if (session == null) {
            file.delete()
            File(directory, "$fileName.tmp").delete()
            return@withContext
        }
        val plaintext = MelodeeJson.encodeToString(StoredSessionDto.fromDomain(session)).toByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size in 1..255) { "Unexpected GCM IV length" }
        val output = ByteArray(MAGIC.size + 1 + 1 + iv.size + ciphertext.size)
        var offset = 0
        MAGIC.copyInto(output, offset); offset += MAGIC.size
        output[offset++] = FORMAT_VERSION
        output[offset++] = iv.size.toByte()
        iv.copyInto(output, offset); offset += iv.size
        ciphertext.copyInto(output, offset)

        directory.mkdirs()
        val temporary = File(directory, "$fileName.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(output)
            stream.flush()
            stream.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun decode(bytes: ByteArray): ByteArray {
        if (bytes.size < MAGIC.size + 2 || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IllegalArgumentException("Unknown session file format")
        }
        var offset = MAGIC.size
        if (bytes[offset++] != FORMAT_VERSION) throw IllegalArgumentException("Unsupported session version")
        val ivLength = bytes[offset++].toInt() and 0xff
        if (ivLength == 0 || offset + ivLength >= bytes.size) throw IllegalArgumentException("Invalid session IV")
        val iv = bytes.copyOfRange(offset, offset + ivLength)
        offset += ivLength
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(bytes.copyOfRange(offset, bytes.size))
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    internal fun deleteForTest() {
        file.delete()
        File(directory, "$fileName.tmp").delete()
    }

    private companion object {
        const val TAG = "MelodeeSession"
        const val DEFAULT_KEY_ALIAS = "melodee.session.v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        val MAGIC = byteArrayOf(0x4d, 0x4c, 0x44, 0x53) // MLDS
    }
}

@Serializable
internal data class StoredSessionDto(
    val tokens: TokenBundleDto,
    val refreshState: String,
) {
    fun toDomain(): StoredSession = StoredSession(
        tokens = tokens.toDomain(),
        refreshState = runCatching { PersistedRefreshState.valueOf(refreshState) }
            .getOrDefault(PersistedRefreshState.OUTCOME_UNKNOWN),
    )

    companion object {
        fun fromDomain(session: StoredSession) = StoredSessionDto(
            tokens = TokenBundleDto.fromDomain(session.tokens),
            refreshState = session.refreshState.name,
        )
    }
}

@Serializable
internal data class TokenBundleDto(
    val serverId: String,
    val accountId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val generation: Long,
) {
    fun toDomain() = TokenBundle(
        account = AccountScope(serverId, accountId),
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = expiresAtMillis,
        generation = generation,
    )

    companion object {
        fun fromDomain(tokens: TokenBundle) = TokenBundleDto(
            serverId = tokens.account.serverId,
            accountId = tokens.account.accountId,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresAtMillis = tokens.expiresAtMillis,
            generation = tokens.generation,
        )
    }
}
