package org.melodee.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import org.melodee.android.playback.PlaybackUiState
import org.melodee.android.playback.PlayerViewModel

@Composable
fun NowPlayingScreen(vm: PlayerViewModel, state: PlaybackUiState, favoriteIds: Set<String>, onBack: () -> Unit, onQueue: () -> Unit) {
    val track = state.current
    if (track == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.Headphones, null, tint = Accent, modifier = Modifier.size(60.dp))
            Text("Find your soundtrack", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
            Text("Choose an album or song to begin.", color = Muted, modifier = Modifier.padding(top = 10.dp))
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 18.dp)) { Text("Explore your music") }
        }
        return
    }
    var scrubPosition by remember(track.id) { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF33213E), Ink), endY = 1500f)).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", modifier = Modifier.size(30.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ALBUM", fontSize = 9.sp, letterSpacing = 2.sp, color = Muted)
                Text(track.albumTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Open queue") }
        }
        Spacer(Modifier.height(18.dp))
        Cover(track.artwork, Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f), "${track.albumTitle} album artwork")
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.headlineMedium)
                Text(track.artist, color = Muted, modifier = Modifier.padding(top = 5.dp))
            }
            val liked = track.id in favoriteIds
            IconButton(onClick = { vm.favorite(track.id) }) {
                Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (liked) "Remove from favorites" else "Add to favorites", tint = if (liked) Accent else SoftWhite)
            }
        }
        Spacer(Modifier.height(14.dp))
        Slider(value = scrubPosition ?: state.positionMs.toFloat(),
            onValueChange = { scrubPosition = it },
            valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
            onValueChangeFinished = { scrubPosition?.let { vm.seek(it.toLong()) }; scrubPosition = null },
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(formatTime(scrubPosition?.toLong() ?: state.positionMs), color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(formatTime(state.durationMs), color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::shuffle) {
                Icon(Icons.Rounded.Shuffle, if (state.shuffle) "Disable shuffle" else "Enable shuffle",
                    tint = if (state.shuffle) Accent else Muted)
            }
            IconButton(onClick = vm::previous, modifier = Modifier.size(54.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous track", modifier = Modifier.size(34.dp))
            }
            if (state.isBuffering) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(52.dp))
                }
            } else PlayCircle(vm::togglePlay, playing = state.isPlaying, size = 76)
            IconButton(onClick = vm::next, modifier = Modifier.size(54.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next track", modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = vm::repeat) {
                Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat: ${when (state.repeatMode) { Player.REPEAT_MODE_ONE -> "one"; Player.REPEAT_MODE_ALL -> "all"; else -> "off" }}",
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Muted else Accent)
            }
        }
        FilledTonalButton(onClick = onQueue, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null)
            Spacer(Modifier.width(10.dp))
            Text("Up next")
            Spacer(Modifier.weight(1f))
            Text("${state.queue.size} tracks", color = Muted)
        }
        Text("Streaming from your Melodee server",
            color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp), textAlign = TextAlign.Center)
    }
}

@Composable
fun QueueScreen(vm: PlayerViewModel, state: PlaybackUiState, onBack: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PageHeader("Play queue", "${state.queue.size} tracks${if (state.shuffle) " · Shuffle on" else ""}", onBack)
        }
        if (state.queue.isEmpty()) {
            item {
                Text("Your next great listen starts here.", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 28.dp))
                Text("Play an album or playlist to build a queue.", color = Muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        items(state.queueOrder) { index ->
            val track = state.queue.getOrNull(index) ?: return@items
            TrackRow(track, { vm.selectQueue(index) }, playing = index == state.currentIndex,
                trailing = {
                    Row {
                        if (index > 0 && !state.shuffle) {
                            IconButton(onClick = { vm.moveUp(index) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Move ${track.title} earlier", tint = Muted) }
                        }
                        IconButton(onClick = { vm.removeQueue(index) }) {
                            Icon(Icons.Rounded.Close, "Remove ${track.title} from queue", tint = Muted, modifier = Modifier.size(20.dp))
                        }
                    }
                })
        }
        if (state.queue.isNotEmpty()) {
            item {
                TextButton(onClick = vm::clearQueue, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Stop playback and clear queue")
                }
            }
        }
    }
}
