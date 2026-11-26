package org.melodee.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name = "melodee_preferences")
data class UserSettings(
    val rememberPosition: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val favorites: Set<String> = emptySet(),
)
class UserPreferences(private val context: Context) {
    private val remember = booleanPreferencesKey("remember_position")
    private val pause = booleanPreferencesKey("pause_on_disconnect")
    private val favorites = stringSetPreferencesKey("favorites")
    val settings = context.preferences.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        UserSettings(values[remember] ?: true, values[pause] ?: true, values[favorites].orEmpty())
    }
    suspend fun rememberPosition(value: Boolean) { context.preferences.edit { it[remember] = value } }
    suspend fun pauseOnDisconnect(value: Boolean) { context.preferences.edit { it[pause] = value } }
    suspend fun toggleFavorite(id: String) {
        context.preferences.edit { values ->
            val current = values[favorites].orEmpty()
            values[favorites] = if (id in current) current - id else current + id
        }
    }
}

