package org.melodee.android.model

/** Whether data comes from original bundled fixtures or an authenticated Melodee server. */
enum class CatalogMode { DEMO, LIVE }

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
    val mode: CatalogMode,
    /** Immutable server revision when available; used for cache identity/revalidation. */
    val revision: String = "",
    val updatedAt: String = "",
) {
    init {
        require(id.isNotBlank() && title.isNotBlank())
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
    val mode: CatalogMode,
    val updatedAt: String = "",
)

data class Artist(
    val id: String,
    val name: String,
    val artwork: Artwork,
    val albumCount: Int,
    val songCount: Int,
    val biography: String?,
    val mode: CatalogMode,
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
    val mode: CatalogMode,
)

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val total: Int get() = tracks.size + albums.size + artists.size + playlists.size
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
