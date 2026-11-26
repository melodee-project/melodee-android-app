package org.melodee.android.live.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.melodee.android.R
import org.melodee.android.data.QueueSnapshotDao
import org.melodee.android.data.QueueSnapshotEntity
import org.melodee.android.live.api.MelodeeJson
import org.melodee.android.model.Artwork
import org.melodee.android.model.CatalogMode
import org.melodee.android.model.PlaybackSnapshot
import org.melodee.android.model.QueueContext
import org.melodee.android.model.QueueEntry
import org.melodee.android.model.Track
import org.melodee.core.auth.AccountScope

/**
 * Account-scoped durable queue. Signed transport URLs are never stored; they are
 * resolved again by the stream layer when bytes are needed.
 */
class LiveQueueStore(
    private val dao: QueueSnapshotDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun save(account: AccountScope, snapshot: PlaybackSnapshot) {
        val payload = MelodeeJson.encodeToString(snapshot.toDto(now()))
        dao.save(QueueSnapshotEntity(LiveCatalogCache.accountKey(account), payload, now()))
    }

    suspend fun load(account: AccountScope): PlaybackSnapshot? {
        val entity = dao.load(LiveCatalogCache.accountKey(account)) ?: return null
        return runCatching {
            val dto = MelodeeJson.decodeFromString(QueueSnapshotDto.serializer(), entity.payload)
            dto.toDomain()
        }.getOrNull()
    }

    suspend fun clear(account: AccountScope) = dao.delete(LiveCatalogCache.accountKey(account))

    suspend fun clearAll() = dao.clear()
}

@Serializable
private data class QueueSnapshotDto(
    val entries: List<QueueEntryDto>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val shuffleOrder: List<Int>,
    val context: String,
    val contextId: String? = null,
    val updatedAtMillis: Long,
) {
    fun toDomain(): PlaybackSnapshot? {
        if (entries.isEmpty()) return null
        val tracks = entries.map { QueueEntry(it.entryId, it.track.toDomain()) }
        val index = currentIndex.coerceIn(tracks.indices)
        return PlaybackSnapshot(
            entries = tracks,
            currentIndex = index,
            positionMs = positionMs.coerceAtLeast(0),
            shuffle = shuffle,
            repeatMode = repeatMode,
            shuffleOrder = shuffleOrder.takeIf { it.size == tracks.size && it.toSet() == tracks.indices.toSet() }
                ?: tracks.indices.toList(),
            context = runCatching { QueueContext.valueOf(context) }.getOrDefault(QueueContext.MANUAL),
            contextId = contextId,
            updatedAtMillis = updatedAtMillis,
        )
    }
}

@Serializable
private data class QueueEntryDto(
    val entryId: String,
    val track: SnapshotTrackDto,
)

@Serializable
private data class SnapshotTrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val albumTitle: String,
    val durationMs: Long,
    val trackNumber: Int,
    val bitrateKbps: Int,
    val artworkUrl: String?,
    val revision: String,
    val updatedAt: String,
) {
    fun toDomain() = Track(
        id = id,
        title = title,
        artist = artist,
        albumId = albumId,
        albumTitle = albumTitle,
        durationMs = durationMs,
        trackNumber = trackNumber,
        bitrateKbps = bitrateKbps,
        artwork = artworkUrl?.let(Artwork::Remote) ?: Artwork.Local(R.drawable.art_placeholder),
        mode = CatalogMode.LIVE,
        revision = revision,
        updatedAt = updatedAt,
    )
}

private fun PlaybackSnapshot.toDto(timestamp: Long) = QueueSnapshotDto(
    entries = entries.map { entry ->
        QueueEntryDto(
            entryId = entry.entryId,
            track = entry.track.toSnapshot(),
        )
    },
    currentIndex = currentIndex,
    positionMs = positionMs,
    shuffle = shuffle,
    repeatMode = repeatMode,
    shuffleOrder = shuffleOrder,
    context = context.name,
    contextId = contextId,
    updatedAtMillis = timestamp,
)

private fun Track.toSnapshot() = SnapshotTrackDto(
    id = id,
    title = title,
    artist = artist,
    albumId = albumId,
    albumTitle = albumTitle,
    durationMs = durationMs,
    trackNumber = trackNumber,
    bitrateKbps = bitrateKbps,
    artworkUrl = (artwork as? Artwork.Remote)?.url,
    revision = revision,
    updatedAt = updatedAt,
)
