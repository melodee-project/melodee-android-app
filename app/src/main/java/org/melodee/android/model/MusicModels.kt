package org.melodee.android.model

sealed interface Artwork {
    data class Local(val drawableRes: Int) : Artwork
    data class Remote(val url: String) : Artwork
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val albumTitle: String,
    val durationMs: Long,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val bitrateKbps: Int = 0,
    val artwork: Artwork,
    /** Immutable server revision when available; used for cache identity/revalidation. */
    val revision: String = "",
    val updatedAt: String = "",
    val rating: Int = 0,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(rating in 0..5)
        require(durationMs >= 0)
    }
}

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val description: String?,
    val releaseYear: Int,
    val artwork: Artwork,
    val songCount: Int,
    val trackIds: List<String>,
    val updatedAt: String = "",
    val isStarred: Boolean = false,
    val rating: Int = 0,
)

data class Artist(
    val id: String,
    val name: String,
    val artwork: Artwork,
    val albumCount: Int,
    val songCount: Int,
    val biography: String?,
    val isStarred: Boolean = false,
    val rating: Int = 0,
)

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val artwork: Artwork,
    val songCount: Int,
    val trackIds: List<String>,
    val isPublic: Boolean,
    val ownerName: String,
)

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val totalCount: Int = 0,
) {
    val total: Int get() = if (totalCount > 0) totalCount else tracks.size + albums.size + artists.size + playlists.size
}

data class HomeFeed(
    val recentAlbums: List<Album> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

/** A queue entry is distinct from a track because the same track can appear twice. */
data class QueueEntry(
    val entryId: String,
    val track: Track,
)

enum class QueueContext { ALBUM, PLAYLIST, ARTIST, RADIO, MANUAL }

data class PlaybackSnapshot(
    val entries: List<QueueEntry>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val shuffleOrder: List<Int>,
    val context: QueueContext = QueueContext.MANUAL,
    val contextId: String? = null,
    val updatedAtMillis: Long = 0,
)
