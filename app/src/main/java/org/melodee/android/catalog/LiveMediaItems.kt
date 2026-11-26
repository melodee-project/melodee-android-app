package org.melodee.android.catalog

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.melodee.android.R
import org.melodee.android.model.Artwork
import org.melodee.android.model.Track

/** Stable media/queue metadata for live tracks. Transport URLs are resolved at read time. */
object LiveMediaItems {
    private const val KEY_REVISION = "melodee.revision"
    private const val KEY_BITRATE = "melodee.bitrate"
    private const val KEY_ALBUM_ID = "melodee.albumId"
    private const val KEY_DURATION = "melodee.durationMs"
    private const val KEY_UPDATED = "melodee.updatedAt"
    private const val KEY_RATING = "melodee.rating"

    fun mediaItem(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(track.mediaId())
        .setUri(trackUri(track))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.albumTitle)
                .setArtworkUri((track.artwork as? Artwork.Remote)?.url?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply {
                    putString(KEY_REVISION, track.revision)
                    putInt(KEY_BITRATE, track.bitrateKbps)
                    putString(KEY_ALBUM_ID, track.albumId)
                    putLong(KEY_DURATION, track.durationMs)
                    putString(KEY_UPDATED, track.updatedAt)
                    putInt(KEY_RATING, track.rating)
                })
                .build(),
        )
        .build()

    fun trackFrom(item: MediaItem): Track? {
        val mediaId = item.mediaId
        if (!mediaId.startsWith("live:track:")) return null
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        val id = mediaId.removePrefix("live:track:")
        return Track(
            id = id,
            title = metadata.title?.toString().orEmpty().ifBlank { "Unknown track" },
            artist = metadata.artist?.toString().orEmpty().ifBlank { "Unknown artist" },
            albumId = extras?.getString(KEY_ALBUM_ID).orEmpty(),
            albumTitle = metadata.albumTitle?.toString().orEmpty(),
            durationMs = extras?.getLong(KEY_DURATION, 0L) ?: 0L,
            bitrateKbps = extras?.getInt(KEY_BITRATE, 0) ?: 0,
            artwork = metadata.artworkUri?.let { Artwork.Remote(it.toString()) }
                ?: Artwork.Local(R.drawable.art_placeholder),
            revision = extras?.getString(KEY_REVISION).orEmpty().ifBlank { "unknown" },
            updatedAt = extras?.getString(KEY_UPDATED).orEmpty(),
            rating = extras?.getInt(KEY_RATING, 0) ?: 0,
        )
    }

    fun trackUri(track: Track): String =
        "melodee://track/${Uri.encode(track.id)}?rev=${Uri.encode(track.revision)}&bitrate=${track.bitrateKbps}"

    private fun Track.mediaId(): String = "live:track:$id"
}
