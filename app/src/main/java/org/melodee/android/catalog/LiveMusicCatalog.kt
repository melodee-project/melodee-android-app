package org.melodee.android.catalog

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.melodee.android.R
import org.melodee.android.live.api.AlbumDto
import org.melodee.android.live.api.ArtistDto
import org.melodee.android.live.api.MelodeeApi
import org.melodee.android.live.api.OriginPolicy
import org.melodee.android.live.api.PlaylistDto
import org.melodee.android.live.api.ServerOrigin
import org.melodee.android.live.api.SongDto
import org.melodee.android.live.auth.LiveSessionManager
import org.melodee.android.live.auth.NotSignedInException
import org.melodee.android.live.catalog.toDomain
import org.melodee.android.live.data.LiveCatalogCache
import org.melodee.android.model.Album
import org.melodee.android.model.Artwork
import org.melodee.android.model.Artist
import org.melodee.android.model.CatalogMode
import org.melodee.android.model.HomeFeed
import org.melodee.android.model.Playlist
import org.melodee.android.model.SearchResults
import org.melodee.android.model.Track
import org.melodee.core.auth.AccountScope

/**
 * Authenticated native-v1 catalog. Metadata is cached per server/account; signed
 * stream URLs are never stored. Browse IDs are namespaced so a demo ID can never
 * be mistaken for a live ID.
 */
class LiveMusicCatalog(
    private val api: MelodeeApi,
    private val session: LiveSessionManager,
    private val cache: LiveCatalogCache,
    private val allowInsecureLoopback: Boolean,
) : MusicCatalog {
    override val mode = CatalogMode.LIVE

    private val lock = Mutex()
    private var loadedFor: String? = null
    private val albumsById = linkedMapOf<String, Album>()
    private val artistsById = linkedMapOf<String, Artist>()
    private val playlistsById = linkedMapOf<String, Playlist>()
    private val tracksById = linkedMapOf<String, Track>()
    private val albumTrackIds = mutableMapOf<String, List<String>>()
    private val playlistTrackIds = mutableMapOf<String, List<String>>()

    private data class Context(val account: AccountScope, val origin: ServerOrigin)

    private suspend fun context(): Context {
        val live = session.currentAccount() ?: throw NotSignedInException()
        val origin = OriginPolicy.normalize(live.serverId, allowInsecureLoopback).getOrElse {
            throw NotSignedInException()
        }
        val account = AccountScope(live.serverId, live.accountId)
        loadCacheOnce(account, origin)
        return Context(account, origin)
    }

    private suspend fun loadCacheOnce(account: AccountScope, origin: ServerOrigin) = lock.withLock {
        val key = LiveCatalogCache.accountKey(account)
        if (loadedFor == key) return@withLock
        albumsById.clear(); artistsById.clear(); playlistsById.clear(); tracksById.clear()
        albumTrackIds.clear(); playlistTrackIds.clear()
        cache.loadAlbums(account).forEach { dto ->
            albumsById[dto.id] = dto.toDomain(origin)
            val ids = cache.loadTrackIds(account, "album-tracks", dto.id)
            if (ids.isNotEmpty()) albumTrackIds[dto.id] = ids
        }
        cache.loadPlaylists(account).forEach { dto ->
            playlistsById[dto.id] = dto.toDomain(origin)
            val ids = cache.loadTrackIds(account, "playlist-tracks", dto.id)
            if (ids.isNotEmpty()) playlistTrackIds[dto.id] = ids
        }
        cache.loadSongs(account).forEach { tracksById[it.id] = it.toDomain(origin) }
        loadedFor = key
    }

    override fun trackFromMediaItem(item: MediaItem): Track? = LiveMediaItems.trackFrom(item)

    override suspend fun root(): MediaItem = node(ROOT, "Melodee")

    override suspend fun item(mediaId: String): MediaItem? {
        when (mediaId) {
            ROOT -> return node(ROOT, "Melodee")
            HOME -> return node(HOME, "Home")
            LIBRARY -> return node(LIBRARY, "Library")
            ALBUMS -> return node(ALBUMS, "Albums")
            ARTISTS -> return node(ARTISTS, "Artists")
            SONGS -> return node(SONGS, "Songs")
            PLAYLISTS -> return node(PLAYLISTS, "Playlists")
            FAVORITES -> return node(FAVORITES, "Favorites")
        }
        if (mediaId.startsWith(TRACK_PREFIX)) {
            val id = mediaId.removePrefix(TRACK_PREFIX)
            val track = tracksById[id] ?: runCatching { loadTrack(id) }.getOrNull() ?: return null
            return mediaItem(track)
        }
        if (mediaId.startsWith(ALBUM_PREFIX)) {
            val id = mediaId.removePrefix(ALBUM_PREFIX)
            val album = albumsById[id] ?: runCatching { album(id) }.getOrNull() ?: return null
            return albumNode(album)
        }
        if (mediaId.startsWith(ARTIST_PREFIX)) {
            val id = mediaId.removePrefix(ARTIST_PREFIX)
            val artist = artistsById[id] ?: runCatching { artists().firstOrNull { it.id == id } }.getOrNull()
                ?: return null
            return artistNode(artist)
        }
        if (mediaId.startsWith(PLAYLIST_PREFIX)) {
            val id = mediaId.removePrefix(PLAYLIST_PREFIX)
            val playlist = playlistsById[id] ?: runCatching { playlist(id) }.getOrNull() ?: return null
            return playlistNode(playlist)
        }
        return null
    }

    override suspend fun children(mediaId: String, page: Int, pageSize: Int): List<MediaItem> {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)
        return when {
            mediaId == ROOT -> listOf(node(HOME, "Home"), node(LIBRARY, "Library"),
                node(PLAYLISTS, "Playlists"), node(FAVORITES, "Favorites"))
            mediaId == HOME -> {
                val feed = home()
                feed.recentAlbums.take(6).map(::albumNode) +
                    feed.playlists.take(4).map(::playlistNode) +
                    feed.recentTracks.take(8).map(::mediaItem)
            }
            mediaId == LIBRARY -> listOf(node(ALBUMS, "Albums"), node(ARTISTS, "Artists"),
                node(SONGS, "Songs"), node(FAVORITES, "Favorites"))
            mediaId == ALBUMS -> albums(safePage, safeSize).map(::albumNode)
            mediaId == ARTISTS -> artists(safePage, safeSize).map(::artistNode)
            mediaId == SONGS -> tracks(safePage, safeSize).map(::mediaItem)
            mediaId == PLAYLISTS -> playlists(safePage, safeSize).map(::playlistNode)
            mediaId == FAVORITES -> favorites(safePage, safeSize).map(::mediaItem)
            mediaId.startsWith(ALBUM_PREFIX) -> albumTracks(mediaId.removePrefix(ALBUM_PREFIX))
                .drop((safePage - 1) * safeSize).take(safeSize).map(::mediaItem)
            mediaId.startsWith(PLAYLIST_PREFIX) -> playlistTracks(mediaId.removePrefix(PLAYLIST_PREFIX), safePage, safeSize)
                .map(::mediaItem)
            mediaId.startsWith(ARTIST_PREFIX) -> artistAlbums(mediaId.removePrefix(ARTIST_PREFIX), safePage, safeSize)
                .map(::albumNode)
            else -> emptyList()
        }
    }

    override suspend fun search(query: String, page: Int, pageSize: Int): List<MediaItem> {
        val results = searchDomain(query, page, pageSize)
        return results.tracks.map(::mediaItem) + results.albums.map(::albumNode) +
            results.artists.map(::artistNode) + results.playlists.map(::playlistNode)
    }

    override suspend fun searchCount(query: String): Int = searchDomain(query, 1, 1).total

    override suspend fun mediaItems(trackIds: List<String>): List<MediaItem> =
        trackIds.mapNotNull { id ->
            val track = tracksById[id] ?: runCatching { loadTrack(id) }.getOrNull() ?: return@mapNotNull null
            mediaItem(track)
        }

    override suspend fun expandSelection(mediaId: String): List<String>? = when {
        mediaId.startsWith(TRACK_PREFIX) -> {
            val track = tracksById[mediaId.removePrefix(TRACK_PREFIX)]
                ?: runCatching { loadTrack(mediaId.removePrefix(TRACK_PREFIX)) }.getOrNull()
            track?.let { albumTracks(it.albumId).map(Track::id) }
        }
        mediaId.startsWith(ALBUM_PREFIX) -> albumTracks(mediaId.removePrefix(ALBUM_PREFIX)).map(Track::id)
        mediaId.startsWith(PLAYLIST_PREFIX) -> playlistTracks(mediaId.removePrefix(PLAYLIST_PREFIX), 1, 100).map(Track::id)
        mediaId.startsWith(ARTIST_PREFIX) -> artistTracks(mediaId.removePrefix(ARTIST_PREFIX), 1, 100).map(Track::id)
        mediaId == FAVORITES -> favorites(1, 100).map(Track::id)
        else -> null
    }

    override suspend fun home(): HomeFeed {
        val recentAlbums = runCatching { albums(1, 8) }.getOrDefault(emptyList())
        val recentTracks = runCatching { recentlyPlayed() }.getOrDefault(emptyList())
        val playlists = runCatching { playlists(1, 8) }.getOrDefault(emptyList())
        return HomeFeed(recentAlbums, recentTracks, playlists)
    }

    override suspend fun albums(page: Int, pageSize: Int): List<Album> {
        val context = context()
        return try {
            val pageDto = session.authorized { origin, token -> api.albums(origin, token, page, pageSize) }
            pageDto.data.forEach { dto -> cache.saveAlbum(context.account, dto) }
            pageDto.data.map { dto ->
                val domain = dto.toDomain(context.origin, albumTrackIds[dto.id].orEmpty())
                albumsById[dto.id] = domain
                domain
            }.ifEmpty { cachedAlbums(page, pageSize) }
        } catch (error: Exception) {
            if (error is NotSignedInException || error is org.melodee.android.live.auth.ReauthenticationRequiredException) throw error
            cachedAlbums(page, pageSize).ifEmpty { throw error }
        }
    }

    override suspend fun album(id: String): Album? {
        albumsById[id]?.let { return it }
        val context = context()
        return try {
            val dto = session.authorized { origin, token -> api.album(origin, token, id) }
            cache.saveAlbum(context.account, dto)
            dto.toDomain(context.origin, albumTrackIds[id].orEmpty()).also { albumsById[id] = it }
        } catch (error: Exception) {
            cache.loadAlbum(context.account, id)?.toDomain(context.origin, albumTrackIds[id].orEmpty())
                ?.also { albumsById[id] = it }
        }
    }

    override suspend fun albumTracks(id: String): List<Track> {
        albumTrackIds[id]?.let { ids -> ids.mapNotNull(tracksById::get).takeIf { it.size == ids.size }?.let { return it } }
        val context = context()
        val all = mutableListOf<SongDto>()
        var page = 1
        try {
            while (page <= MAX_ALBUM_PAGES) {
                val pageDto = session.authorized { origin, token -> api.albumSongs(origin, token, id, page, 100) }
                all += pageDto.data
                if (!pageDto.meta.hasNext || pageDto.data.isEmpty()) break
                page++
            }
            if (all.isEmpty()) return emptyList()
            cache.saveSongs(context.account, all)
            val tracks = all.map { it.toDomain(context.origin) }
            tracks.forEach { tracksById[it.id] = it }
            albumTrackIds[id] = tracks.map(Track::id)
            cache.saveTrackIds(context.account, "album-tracks", id, albumTrackIds.getValue(id))
            albumsById[id]?.let { albumsById[id] = it.copy(trackIds = albumTrackIds.getValue(id)) }
            return tracks
        } catch (error: Exception) {
            val cached = cache.loadSongs(context.account).filter { it.album.id == id }.map { it.toDomain(context.origin) }
            if (cached.isNotEmpty()) {
                cached.forEach { tracksById[it.id] = it }
                albumTrackIds[id] = cached.map(Track::id)
                return cached
            }
            throw error
        }
    }

    override suspend fun artists(page: Int, pageSize: Int): List<Artist> {
        val context = context()
        return try {
            val pageDto = session.authorized { origin, token -> api.artists(origin, token, page, pageSize) }
            pageDto.data.map { it.toDomain(context.origin).also { artist -> artistsById[artist.id] = artist } }
                .ifEmpty { cachedArtists(page, pageSize) }
        } catch (error: Exception) {
            cachedArtists(page, pageSize).ifEmpty { throw error }
        }
    }

    override suspend fun artist(id: String): Artist? {
        artistsById[id]?.let { return it }
        val context = context()
        return try {
            val dto = session.authorized { origin, token -> api.artist(origin, token, id) }
            dto.toDomain(context.origin).also { artistsById[id] = it }
        } catch (error: Exception) {
            throw error
        }
    }

    override suspend fun artistAlbums(id: String, page: Int, pageSize: Int): List<Album> {
        val context = context()
        val pageDto = session.authorized { origin, token -> api.artistAlbums(origin, token, id, page, pageSize) }
        return pageDto.data.map { dto ->
            dto.toDomain(context.origin, albumTrackIds[dto.id].orEmpty()).also { albumsById[dto.id] = it }
        }
    }

    override suspend fun artistTracks(id: String, page: Int, pageSize: Int): List<Track> {
        val context = context()
        val pageDto = session.authorized { origin, token -> api.artistSongs(origin, token, id, page, pageSize) }
        return pageDto.data.map { dto ->
            dto.toDomain(context.origin).also { track -> tracksById[track.id] = track }
        }
    }

    override suspend fun playlists(page: Int, pageSize: Int): List<Playlist> {
        val context = context()
        return try {
            val pageDto = session.authorized { origin, token -> api.playlists(origin, token, page, pageSize) }
            pageDto.data.forEach { cache.savePlaylist(context.account, it) }
            pageDto.data.map { dto ->
                dto.toDomain(context.origin, playlistTrackIds[dto.id].orEmpty()).also { playlistsById[dto.id] = it }
            }.ifEmpty { cachedPlaylists(page, pageSize) }
        } catch (error: Exception) {
            cachedPlaylists(page, pageSize).ifEmpty { throw error }
        }
    }

    override suspend fun playlist(id: String): Playlist? {
        playlistsById[id]?.let { return it }
        val context = context()
        return try {
            val dto = session.authorized { origin, token -> api.playlist(origin, token, id) }
            cache.savePlaylist(context.account, dto)
            dto.toDomain(context.origin, playlistTrackIds[id].orEmpty()).also { playlistsById[id] = it }
        } catch (error: Exception) {
            null
        }
    }

    override suspend fun playlistTracks(id: String, page: Int, pageSize: Int): List<Track> {
        playlistTrackIds[id]?.let { ids ->
            ids.mapNotNull(tracksById::get).takeIf { it.size == ids.size }?.let { return it }
        }
        val context = context()
        return try {
            val pageDto = session.authorized { origin, token -> api.playlistSongs(origin, token, id, page, pageSize) }
            val tracks = pageDto.data.map { it.toDomain(context.origin) }
            tracks.forEach { tracksById[it.id] = it }
            playlistTrackIds[id] = tracks.map(Track::id)
            cache.saveTrackIds(context.account, "playlist-tracks", id, playlistTrackIds.getValue(id))
            tracks
        } catch (error: Exception) {
            val ids = cache.loadTrackIds(context.account, "playlist-tracks", id)
            val cached = cache.loadSongs(context.account).filter { it.id in ids }.map { it.toDomain(context.origin) }
            cached.drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize).ifEmpty { throw error }
        }
    }

    override suspend fun tracks(page: Int, pageSize: Int): List<Track> {
        val context = context()
        return try {
            val pageDto = session.authorized { origin, token -> api.songs(origin, token, page, pageSize) }
            pageDto.data.map { dto -> dto.toDomain(context.origin).also { tracksById[it.id] = it } }
        } catch (error: Exception) {
            cache.loadSongs(context.account).map { it.toDomain(context.origin) }
                .drop((page.coerceAtLeast(1) - 1) * pageSize).take(pageSize)
                .ifEmpty { throw error }
        }
    }

    override suspend fun favorites(page: Int, pageSize: Int): List<Track> {
        val context = context()
        val pageDto = session.authorized { origin, token -> api.likedSongs(origin, token, page, pageSize) }
        return pageDto.data.map { dto -> dto.toDomain(context.origin).also { tracksById[it.id] = it } }
    }

    override suspend fun setFavorite(trackId: String, favorite: Boolean) {
        session.authorized { origin, token -> api.setSongStarred(origin, token, trackId, favorite) }
    }

    override suspend fun searchDomain(query: String, page: Int, pageSize: Int): SearchResults {
        val context = context()
        val response = session.authorized { origin, token -> api.search(origin, token, query, pageSize, page) }
        val results = response.toDomain(context.origin)
        results.tracks.forEach { tracksById[it.id] = it }
        results.albums.forEach { albumsById[it.id] = it }
        results.artists.forEach { artistsById[it.id] = it }
        results.playlists.forEach { playlistsById[it.id] = it }
        return results
    }

    private suspend fun recentlyPlayed(): List<Track> {
        val context = context()
        val pageDto = session.authorized { origin, token -> api.recentlyPlayed(origin, token) }
        return pageDto.data.map { dto -> dto.toDomain(context.origin).also { tracksById[it.id] = it } }
    }

    private suspend fun loadTrack(id: String): Track? {
        val context = context()
        val dto = session.authorized { origin, token -> api.song(origin, token, id) }
        cache.saveSongs(context.account, listOf(dto))
        return dto.toDomain(context.origin).also { tracksById[it.id] = it }
    }

    private suspend fun cachedAlbums(page: Int, pageSize: Int): List<Album> =
        albumsById.values.toList().drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize)

    private suspend fun cachedArtists(page: Int, pageSize: Int): List<Artist> =
        artistsById.values.toList().drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize)

    private suspend fun cachedPlaylists(page: Int, pageSize: Int): List<Playlist> =
        playlistsById.values.toList().drop((page - 1).coerceAtLeast(0) * pageSize).take(pageSize)

    private fun mediaItem(track: Track): MediaItem = LiveMediaItems.mediaItem(track)

    private fun albumNode(album: Album): MediaItem = node(
        ALBUM_PREFIX + album.id, album.title, album.artist,
        (album.artwork as? Artwork.Remote)?.url,
    )

    private fun artistNode(artist: Artist): MediaItem = node(
        ARTIST_PREFIX + artist.id, artist.name, "${artist.albumCount} albums",
        (artist.artwork as? Artwork.Remote)?.url,
    )

    private fun playlistNode(playlist: Playlist): MediaItem = node(
        PLAYLIST_PREFIX + playlist.id, playlist.title, playlist.description,
        (playlist.artwork as? Artwork.Remote)?.url,
    )

    private fun node(id: String, title: String, subtitle: String? = null, artwork: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artwork?.let(Uri::parse))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            ).build()

    private companion object {
        const val ROOT = "live:root"
        const val HOME = "live:home"
        const val LIBRARY = "live:library"
        const val ALBUMS = "live:albums"
        const val ARTISTS = "live:artists"
        const val SONGS = "live:songs"
        const val PLAYLISTS = "live:playlists"
        const val FAVORITES = "live:favorites"
        const val TRACK_PREFIX = "live:track:"
        const val ALBUM_PREFIX = "live:album:"
        const val ARTIST_PREFIX = "live:artist:"
        const val PLAYLIST_PREFIX = "live:playlist:"
        const val MAX_ALBUM_PAGES = 10
            }
}
