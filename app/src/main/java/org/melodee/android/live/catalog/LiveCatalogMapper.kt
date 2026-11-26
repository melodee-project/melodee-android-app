package org.melodee.android.live.catalog

import org.melodee.android.R
import org.melodee.android.live.api.AlbumDto
import org.melodee.android.live.api.ArtistDto
import org.melodee.android.live.api.OriginPolicy
import org.melodee.android.live.api.PlaylistDto
import org.melodee.android.live.api.SearchResponseDto
import org.melodee.android.live.api.ServerOrigin
import org.melodee.android.live.api.SongDto
import org.melodee.android.model.Album
import org.melodee.android.model.Artwork
import org.melodee.android.model.Artist
import org.melodee.android.model.CatalogMode
import org.melodee.android.model.Playlist
import org.melodee.android.model.SearchResults
import org.melodee.android.model.Track

internal fun artwork(origin: ServerOrigin, imageUrl: String, thumbnailUrl: String): Artwork {
    val remote = OriginPolicy.absoluteSameOrigin(origin, imageUrl.ifBlank { thumbnailUrl })
        ?: OriginPolicy.absoluteSameOrigin(origin, thumbnailUrl)
    return remote?.let(Artwork::Remote) ?: Artwork.Local(R.drawable.art_afterglow)
}

internal fun revisionOf(updatedAt: String, createdAt: String, bitrate: Int): String {
    val stable = updatedAt.takeUnless { it.isBlank() || it == "---" }
        ?: createdAt.takeUnless { it.isBlank() || it == "---" }
        ?: "unknown"
    return "$stable|bitrate=$bitrate"
}

internal fun ArtistDto.toDomain(origin: ServerOrigin): Artist = Artist(
    id = id,
    name = name.ifBlank { "Unknown artist" },
    artwork = artwork(origin, imageUrl, thumbnailUrl),
    albumCount = albumCount,
    songCount = songCount,
    biography = biography,
    mode = CatalogMode.LIVE,
)

internal fun AlbumDto.toDomain(origin: ServerOrigin, trackIds: List<String> = emptyList()): Album = Album(
    id = id,
    title = name.ifBlank { "Unknown album" },
    artist = artist.name.ifBlank { "Unknown artist" },
    artistId = artist.id,
    description = description,
    releaseYear = releaseYear,
    artwork = artwork(origin, imageUrl, thumbnailUrl),
    songCount = songCount,
    trackIds = trackIds,
    mode = CatalogMode.LIVE,
    updatedAt = revisionOf(updatedAt, createdAt, 0),
)

internal fun SongDto.toDomain(origin: ServerOrigin): Track = Track(
    id = id,
    title = title.ifBlank { "Unknown track" },
    artist = artist.name.ifBlank { album.artist.name }.ifBlank { "Unknown artist" },
    albumId = album.id,
    albumTitle = album.name.ifBlank { "Unknown album" },
    durationMs = durationMs.toLong().coerceAtLeast(0),
    trackNumber = songNumber,
    bitrateKbps = bitrate,
    artwork = artwork(origin, imageUrl, thumbnailUrl),
    mode = CatalogMode.LIVE,
    revision = revisionOf(updatedAt, createdAt, bitrate),
    updatedAt = updatedAt,
)

internal fun PlaylistDto.toDomain(origin: ServerOrigin, trackIds: List<String> = emptyList()): Playlist = Playlist(
    id = id,
    title = name.ifBlank { "Untitled playlist" },
    description = description,
    artwork = artwork(origin, imageUrl, thumbnailUrl),
    songCount = songCount,
    trackIds = trackIds,
    isPublic = isPublic,
    ownerName = owner?.username.orEmpty(),
    mode = CatalogMode.LIVE,
)

internal fun SearchResponseDto.toDomain(origin: ServerOrigin): SearchResults = SearchResults(
    tracks = data.songs.map { it.toDomain(origin) },
    albums = data.albums.map { it.toDomain(origin) },
    artists = data.artists.map { it.toDomain(origin) },
    playlists = data.playlists.map { it.toDomain(origin) },
)
