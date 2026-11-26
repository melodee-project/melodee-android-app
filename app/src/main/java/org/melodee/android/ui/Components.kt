package org.melodee.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodee.android.data.DemoCatalog
import org.melodee.android.data.DemoTrack
import org.melodee.android.playback.PlaybackUiState

@Composable
fun Cover(@DrawableRes art: Int, modifier: Modifier = Modifier, description: String? = null) {
    Image(painterResource(art), description, modifier.clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
}
@Composable
fun PageHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp))
        }
        action?.invoke()
    }
}
@Composable
fun SectionHeader(title: String, actionLabel: String? = null, action: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actionLabel != null) TextButton(onClick = action) { Text(actionLabel, color = Accent) }
    }
}
@Composable
fun PlayCircle(onClick: () -> Unit, playing: Boolean = false, size: Int = 52, enabled: Boolean = true) {
    FilledIconButton(onClick, modifier = Modifier.size(size.dp), enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoftWhite, contentColor = Ink)) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pause" else "Play", modifier = Modifier.size((size / 2).dp))
    }
}
@Composable
fun TrackRow(track: DemoTrack, catalog: DemoCatalog, onClick: () -> Unit,
    playing: Boolean = false, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(catalog.album(track.albumId)!!.artwork, Modifier.size(54.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = if (playing) Accent else SoftWhite,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
        else Icon(if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
            null, tint = if (playing) Accent else Muted, modifier = Modifier.size(24.dp))
    }
}
@Composable
fun MiniPlayer(state: PlaybackUiState, catalog: DemoCatalog, onOpen: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit) {
    val track = state.current ?: return
    Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp).clip(RoundedCornerShape(18.dp))
        .background(Raised).clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Cover(catalog.album(track.albumId)!!.artwork, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(track.artist, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            IconButton(onClick = onPlay) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next track") }
        }
        LinearProgressIndicator(
            progress = { if (state.durationMs == 0L) 0f else (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Color.Transparent,
        )
    }
}
@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null) {
    val modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
    Row((if (onClick != null) modifier.clickable(onClick = onClick) else modifier)
        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
        }
        trailing?.invoke()
    }
}
fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

