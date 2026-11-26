package org.melodee.android.catalog

import android.net.Uri
import androidx.media3.common.MediaItem
import org.melodee.android.data.DemoAlbum
import org.melodee.android.data.DemoCatalog
import org.melodee.android.data.DemoPlaylist
import org.melodee.android.data.DemoTrack
import org.melodee.android.model.Album
import org.melodee.android.model.Artwork
import org.melodee.android.model.Artist
import org.melodee.android.model.CatalogMode
import org.melodee.android.model.HomeFeed
import org.melodee.android.model.Playlist
import org.melodee.android.model.SearchResults
import org.melodee.android.model.Track

/** Adapter for the original bundled fixtures; no network or credentials. */
class DemoMusicCatalog(private val demo: DemoCatalog) : MusicCatalog {
    override val mode = CatalogMode.DEMO

    private fun DemoTrack.toModel(): Track {
        val album = demo.album(albumId)
        return Track(
            id = id,
            title = title,
            artist = artist,
            albumId = albumId,
            albumTitle = album?.title ?: "",
            durationMs = durationMs,
            trackNumber = album?.trackIds?.indexOf(id)?.plus(1) ?: 0,
            artwork = Artwork.Local(album?.artwork ?: org.melodee.android.R.drawable.art_afterglow),
            mode = CatalogMode.DEMO,
            revision = "demo-v1",
        )
    }

    private fun DemoAlbum.toModel() = Album(
        id = id,
        title = title,
        artist = artist,
        artistId = "demo-artist",
        description = description,
        releaseYear = 0,
        artwork = Artwork.Local(artwork),
        songCount = trackIds.size,
        trackIds = trackIds,
        mode = CatalogMode.DEMO,
        updatedAt = "demo-v1",
    )

    private fun DemoPlaylist.toModel() = Playlist(
        id = id,
        title = title,
        description = subtitle,
        artwork = Artwork.Local(org.melodee.android.R.drawable.art_afterglow),
        songCount = trackIds.size,
        trackIds = trackIds,
        isPublic = true,
        ownerName = "Melodee",
        mode = CatalogMode.DEMO,
    )

    private fun trackIds(mediaId: String): List<String>? = when {
        mediaId.startsWith("album:") -> demo.album(mediaId.substringAfter(':') )?.trackIds
        mediaId.startsWith("playlist:") -> demo.playlist(mediaId.substringAfter(':'))?.trackIds
        demo.track(mediaId) != null -> demo.track(mediaId)?.albumId?.let { demo.album(it)?.trackIds }
        else -> null
    }

    override fun trackFromMediaItem(item: MediaItem): Track? =
        demo.track(item.mediaId)?.toModel()

    override suspend fun root(): MediaItem = demo.node("root", "Melodee")

    override suspend fun item(mediaId: String): MediaItem? =
        demo.track(mediaId)?.let(demo::mediaItem) ?: when {
            mediaId == "root" -> demo.node("root", "Melodee")
            mediaId == "home" -> demo.node("home", "Home")
            mediaId == "albums" -> demo.node("albums", "Albums")
            mediaId == "playlists" -> demo.node("playlists", "Playlists")
            mediaId.startsWith("album:") -> demo.album(mediaId.substringAfter(':'))?.let {
                demo.node(mediaId, it.title, it.artist, it.id)
            }
            mediaId.startsWith("playlist:") -> demo.playlist(mediaId.substringAfter(':'))?.let {
                demo.node(mediaId, it.title, it.subtitle, "afterglow")
            }
            else -> null
        }

    override suspend fun children(mediaId: String, page: Int, pageSize: Int): List<MediaItem> =
        demo.children(mediaId).drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize)

    override suspend fun search(query: String, page: Int, pageSize: Int): List<MediaItem> =
        searchItems(query).drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize)

    override suspend fun searchCount(query: String): Int = searchItems(query).size

    /** Preserve the starter behaviour: an exact playlist title resolves to its tracks. */
    private fun searchItems(query: String): List<MediaItem> {
        val playlist = demo.playlists.firstOrNull { it.title.equals(query.trim(), ignoreCase = true) }
        return playlist?.let { demo.items(it.trackIds) } ?: searchDomainItems(query)
    }

    private fun searchDomainItems(query: String): List<MediaItem> {
        val results = searchModel(query)
        return results.tracks.mapNotNull { demo.track(it.id)?.let(demo::mediaItem) } +
            results.albums.mapNotNull { demo.album(it.id)?.let { album -> demo.node("album:${album.id}", album.title, album.artist, album.id) } } +
            results.playlists.mapNotNull { demo.playlist(it.id)?.let { playlist -> demo.node("playlist:${playlist.id}", playlist.title, playlist.subtitle, "afterglow") } }
    }

    private fun searchModel(query: String): SearchResults {
        val tracks = demo.search(query).map { it.toModel() }
        val albums = demo.albums.filter { it.title.contains(query, true) || it.artist.contains(query, true) }.map { it.toModel() }
        val playlists = demo.playlists.filter { it.title.contains(query, true) }.map { it.toModel() }
        return SearchResults(tracks = tracks, albums = albums, playlists = playlists)
    }

    override suspend fun mediaItems(trackIds: List<String>): List<MediaItem> =
        trackIds.mapNotNull { demo.track(it)?.let(demo::mediaItem) }

    override suspend fun expandSelection(mediaId: String): List<String>? = trackIds(mediaId)

    override suspend fun home(): HomeFeed = HomeFeed(
        recentAlbums = demo.albums.map { it.toModel() },
        recentTracks = demo.tracks.map { it.toModel() },
        playlists = demo.playlists.map { it.toModel() },
    )

    override suspend fun albums(page: Int, pageSize: Int): List<Album> =
        demo.albums.map { it.toModel() }.drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize)

    override suspend fun album(id: String): Album? = demo.album(id)?.toModel()

    override suspend fun albumTracks(id: String): List<Track> =
        demo.album(id)?.trackIds.orEmpty().mapNotNull { demo.track(it)?.toModel() }

    override suspend fun artists(page: Int, pageSize: Int): List<Artist> = emptyList()

    override suspend fun artist(id: String): Artist? = null

    override suspend fun artistAlbums(id: String): List<Album> = emptyList()

    override suspend fun artistTracks(id: String): List<Track> = emptyList()

    override suspend fun playlists(page: Int, pageSize: Int): List<Playlist> =
        demo.playlists.map { it.toModel() }.drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize)

    override suspend fun playlist(id: String): Playlist? = demo.playlist(id)?.toModel()

    override suspend fun playlistTracks(id: String): List<Track> =
        demo.playlist(id)?.trackIds.orEmpty().mapNotNull { demo.track(it)?.toModel() }

    override suspend fun tracks(page: Int, pageSize: Int): List<Track> =
        demo.tracks.map { it.toModel() }.drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize)

    override suspend fun favorites(): List<Track> = emptyList()

    override suspend fun setFavorite(trackId: String, favorite: Boolean) = Unit

    override suspend fun searchDomain(query: String): SearchResults = searchModel(query)

    @Suppress("unused")
    private fun uriPath(id: String): String = Uri.encode(id)
}
