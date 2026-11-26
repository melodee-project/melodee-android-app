package org.melodee.android.catalog

import androidx.media3.common.MediaItem
import org.melodee.android.model.Album
import org.melodee.android.model.Artist
import org.melodee.android.model.CatalogMode
import org.melodee.android.model.HomeFeed
import org.melodee.android.model.Playlist
import org.melodee.android.model.SearchResults
import org.melodee.android.model.Track

/**
 * The single catalog boundary used by the playback service, Android Auto and
 * phone UI. Demo and live implementations differ only in data source; both
 * expose the same stable IDs and Media3 metadata contract.
 */
interface MusicCatalog {
    val mode: CatalogMode

    /** Synchronous metadata reconstruction for controller-owned queue/player items. */
    fun trackFromMediaItem(item: MediaItem): Track?

    suspend fun root(): MediaItem
    suspend fun item(mediaId: String): MediaItem?
    suspend fun children(mediaId: String, page: Int, pageSize: Int): List<MediaItem>
    suspend fun search(query: String, page: Int, pageSize: Int): List<MediaItem>
    suspend fun searchCount(query: String): Int

    /** Build fresh MediaItems for raw track IDs. Never persists signed URLs. */
    suspend fun mediaItems(trackIds: List<String>): List<MediaItem>

    /** Raw track IDs for a browsable selection, or null if the ID is a single track. */
    suspend fun expandSelection(mediaId: String): List<String>?

    suspend fun home(): HomeFeed
    suspend fun albums(page: Int = 1, pageSize: Int = 50): List<Album>
    suspend fun album(id: String): Album?
    suspend fun albumTracks(id: String): List<Track>
    suspend fun artists(page: Int = 1, pageSize: Int = 50): List<Artist>
    suspend fun artist(id: String): Artist?
    suspend fun artistAlbums(id: String, page: Int = 1, pageSize: Int = 50): List<Album>
    suspend fun artistTracks(id: String, page: Int = 1, pageSize: Int = 100): List<Track>
    suspend fun playlists(page: Int = 1, pageSize: Int = 50): List<Playlist>
    suspend fun playlist(id: String): Playlist?
    suspend fun playlistTracks(id: String, page: Int = 1, pageSize: Int = 100): List<Track>
    suspend fun tracks(page: Int = 1, pageSize: Int = 50): List<Track>
    suspend fun favorites(page: Int = 1, pageSize: Int = 50): List<Track>

    /** Idempotent favorite mutations for the authenticated account. */
    suspend fun setFavorite(trackId: String, favorite: Boolean)
    suspend fun setAlbumFavorite(albumId: String, favorite: Boolean)
    suspend fun setArtistFavorite(artistId: String, favorite: Boolean)
    suspend fun searchDomain(query: String, page: Int = 1, pageSize: Int = 50): SearchResults
}
