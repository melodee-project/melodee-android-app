package org.melodee.android.live.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import org.melodee.android.live.api.AlbumDto
import org.melodee.android.live.api.MelodeeJson
import org.melodee.android.live.api.PlaylistDto
import org.melodee.android.live.api.SongDto
import org.melodee.android.data.CachedCatalogEntity
import org.melodee.android.data.CatalogCacheDao
import org.melodee.core.auth.AccountScope

/** Account-scoped metadata cache. Audio bytes are never stored here. */
class LiveCatalogCache(
    private val dao: CatalogCacheDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun saveAlbum(account: AccountScope, album: AlbumDto) =
        dao.save(entity(account, "album", album.id, MelodeeJson.encodeToString(album)))

    suspend fun saveSongs(account: AccountScope, songs: List<SongDto>) {
        songs.forEach { dao.save(entity(account, "song", it.id, MelodeeJson.encodeToString(it))) }
    }

    suspend fun savePlaylist(account: AccountScope, playlist: PlaylistDto) =
        dao.save(entity(account, "playlist", playlist.id, MelodeeJson.encodeToString(playlist)))

    suspend fun loadAlbums(account: AccountScope): List<AlbumDto> =
        dao.loadKind(accountKey(account), "album").mapNotNull {
            runCatching { MelodeeJson.decodeFromString(AlbumDto.serializer(), it.payload) }.getOrNull()
        }

    suspend fun loadAlbum(account: AccountScope, id: String): AlbumDto? =
        dao.load(accountKey(account), "album", id)?.let {
            runCatching { MelodeeJson.decodeFromString(AlbumDto.serializer(), it.payload) }.getOrNull()
        }

    suspend fun loadSongs(account: AccountScope): List<SongDto> =
        dao.loadKind(accountKey(account), "song").mapNotNull {
            runCatching { MelodeeJson.decodeFromString(SongDto.serializer(), it.payload) }.getOrNull()
        }

    suspend fun loadSong(account: AccountScope, id: String): SongDto? =
        dao.load(accountKey(account), "song", id)?.let {
            runCatching { MelodeeJson.decodeFromString(SongDto.serializer(), it.payload) }.getOrNull()
        }

    suspend fun loadPlaylists(account: AccountScope): List<PlaylistDto> =
        dao.loadKind(accountKey(account), "playlist").mapNotNull {
            runCatching { MelodeeJson.decodeFromString(PlaylistDto.serializer(), it.payload) }.getOrNull()
        }

    suspend fun saveTrackIds(account: AccountScope, kind: String, id: String, trackIds: List<String>) =
        dao.save(entity(account, kind, id, MelodeeJson.encodeToString(trackIds)))

    suspend fun loadTrackIds(account: AccountScope, kind: String, id: String): List<String> =
        dao.load(accountKey(account), kind, id)?.let {
            runCatching { MelodeeJson.decodeFromString(ListSerializer(String.serializer()), it.payload) }.getOrNull()
        }.orEmpty()

    suspend fun clearAccount(account: AccountScope) = dao.deleteAccount(accountKey(account))

    suspend fun clearAll() = dao.clear()

    private fun entity(account: AccountScope, kind: String, id: String, payload: String) =
        CachedCatalogEntity(accountKey(account), kind, id, payload, now())

    companion object {
        /** No tokens, email or signed URLs enter this key. */
        fun accountKey(account: AccountScope): String = "${account.serverId}|${account.accountId}"
    }
}
