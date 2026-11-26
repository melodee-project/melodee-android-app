package org.melodee.android.live.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.liveAccountDataStore by preferencesDataStore(name = "melodee_live_account")

data class LiveAccount(
    val serverId: String,
    val accountId: String,
    val username: String,
)

interface AccountMetadataStore {
    val accountFlow: Flow<LiveAccount?>
    suspend fun current(): LiveAccount?
    suspend fun save(metadata: LiveAccount)
    suspend fun clear()
    suspend fun deviceId(): String
    suspend fun lastServer(): String?
    suspend fun saveLastServer(serverId: String)
}

/**
 * App-private, non-secret account metadata for cold-start UI and device identity.
 * Tokens live only in the Keystore-backed session store.
 */
class LiveAccountStore(private val context: Context) : AccountMetadataStore {
    private val server = stringPreferencesKey("server_id")
    private val account = stringPreferencesKey("account_id")
    private val username = stringPreferencesKey("username")
    private val deviceId = stringPreferencesKey("device_id")
    private val lastServer = stringPreferencesKey("last_server")

    override val accountFlow: Flow<LiveAccount?> = context.liveAccountDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { values ->
            val serverId = values[server]
            val accountId = values[account]
            val name = values[username]
            if (serverId.isNullOrBlank() || accountId.isNullOrBlank() || name.isNullOrBlank()) null
            else LiveAccount(serverId, accountId, name)
        }

    override suspend fun current(): LiveAccount? = accountFlow.first()

    override suspend fun lastServer(): String? = context.liveAccountDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .first()
        .let { values -> values[lastServer]?.takeIf(String::isNotBlank) ?: values[server] }

    override suspend fun saveLastServer(serverId: String) {
        context.liveAccountDataStore.edit { values -> values[lastServer] = serverId }
    }

    override suspend fun save(metadata: LiveAccount) {
        context.liveAccountDataStore.edit { values ->
            values[server] = metadata.serverId
            values[account] = metadata.accountId
            values[username] = metadata.username
        }
    }

    override suspend fun clear() {
        context.liveAccountDataStore.edit { values ->
            values.remove(server)
            values.remove(account)
            values.remove(username)
        }
    }

    /** Stable per-install identifier for refresh-token family binding. Not an advertising ID. */
    override suspend fun deviceId(): String {
        var existing: String? = null
        context.liveAccountDataStore.edit { values ->
            existing = values[deviceId]
            if (existing.isNullOrBlank()) {
                val generated = UUID.randomUUID().toString()
                values[deviceId] = generated
                existing = generated
            }
        }
        return existing ?: error("Device ID could not be persisted")
    }
}
