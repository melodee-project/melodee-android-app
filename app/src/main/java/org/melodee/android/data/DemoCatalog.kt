package org.melodee.android.data

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.melodee.android.R

data class DemoTrack(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val durationMs: Long = 32_000L,
)
data class DemoAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val description: String,
    @DrawableRes val artwork: Int,
    val trackIds: List<String>,
)
data class DemoPlaylist(val id: String, val title: String, val subtitle: String, val trackIds: List<String>)

/** Original, bundled preview fixtures. No production music or server credentials are shipped. */
class DemoCatalog(private val context: Context) {
    val tracks = listOf(
        DemoTrack("first-light", "First Light", "Melodee Sessions", "afterglow"),
        DemoTrack("open-road", "Open Road", "Melodee Sessions", "afterglow"),
        DemoTrack("afterglow", "Afterglow", "Melodee Sessions", "afterglow"),
        DemoTrack("blue-hour", "Blue Hour", "Melodee Sessions", "nightfall"),
        DemoTrack("slow-motion", "Slow Motion", "Melodee Sessions", "nightfall"),
        DemoTrack("homeward", "Homeward", "Melodee Sessions", "nightfall"),
    )
    val albums = listOf(
        DemoAlbum("afterglow", "Afterglow", "Melodee Sessions", "Warm textures. Wide open spaces.",
            R.drawable.art_afterglow, tracks.take(3).map { it.id }),
        DemoAlbum("nightfall", "Nightfall", "Melodee Sessions", "A softer soundtrack for the long way home.",
            R.drawable.art_nightfall, tracks.drop(3).map { it.id }),
    )
    val playlists = listOf(
        DemoPlaylist("daily-mix", "Daily Mix", "A little of everything you love", tracks.map { it.id }),
        DemoPlaylist("chill", "Easy Evenings", "Take the scenic route", listOf("slow-motion", "afterglow", "homeward", "blue-hour")),
    )
    fun track(id: String) = tracks.firstOrNull { it.id == id }
    fun album(id: String) = albums.firstOrNull { it.id == id }
    fun playlist(id: String) = playlists.firstOrNull { it.id == id }
    fun search(query: String): List<DemoTrack> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return tracks.filter { track ->
            val text = "${track.title} ${track.artist} ${album(track.albumId)?.title}".lowercase()
            words.all(text::contains)
        }
    }
    fun artworkUri(albumId: String): Uri = Uri.parse("content://${context.packageName}.artwork/$albumId")
    fun mediaItem(track: DemoTrack): MediaItem = MediaItem.Builder()
        .setMediaId(track.id)
        .setUri("asset:///audio/${track.id}.ogg")
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(album(track.albumId)?.title)
            .setArtworkUri(artworkUri(track.albumId))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build())
        .build()
    fun items(ids: List<String>) = ids.mapNotNull(::track).map(::mediaItem)
    fun node(id: String, title: String, subtitle: String? = null, art: String? = null): MediaItem =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title).setSubtitle(subtitle).setIsBrowsable(true).setIsPlayable(false)
            .setArtworkUri(art?.let(::artworkUri))
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

    fun children(parent: String): List<MediaItem> = when {
        parent == "root" -> listOf(node("home", "Home"), node("albums", "Albums"), node("playlists", "Playlists"))
        parent == "home" -> albums.map { node("album:${it.id}", it.title, it.artist, it.id) }
        parent == "albums" -> albums.map { node("album:${it.id}", it.title, it.artist, it.id) }
        parent == "playlists" -> playlists.map { node("playlist:${it.id}", it.title, it.subtitle, "afterglow") }
        parent.startsWith("album:") -> items(album(parent.substringAfter(':'))?.trackIds.orEmpty())
        parent.startsWith("playlist:") -> items(playlist(parent.substringAfter(':'))?.trackIds.orEmpty())
        else -> emptyList()
    }
}

