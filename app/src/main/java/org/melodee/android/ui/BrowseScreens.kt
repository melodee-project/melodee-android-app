package org.melodee.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodee.android.R
import org.melodee.android.data.UserSettings
import org.melodee.android.playback.PlaybackUiState
import org.melodee.android.playback.PlayerViewModel

@Composable
fun HomeScreen(vm: PlayerViewModel, playback: PlaybackUiState, onAlbum: (String) -> Unit,
    onPlaylist: (String) -> Unit, onSettings: () -> Unit, onLibrary: () -> Unit) {
    val catalog = vm.catalog
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Image(painterResource(R.drawable.melodee_logo), null, Modifier.size(38.dp))
                Text("Melodee", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Surface(color = Raised, shape = RoundedCornerShape(30.dp)) {
                    Text("Demo library", style = MaterialTheme.typography.labelSmall, color = Muted,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.AccountCircle, "Open settings", tint = Muted) }
            }
        }
        item {
            Column(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                Text("Find your moment.", style = MaterialTheme.typography.headlineLarge)
                Text("Your music, wherever life takes you.", color = Muted, modifier = Modifier.padding(top = 7.dp))
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(242.dp).clip(RoundedCornerShape(24.dp)).clickable { onAlbum("afterglow") }) {
                Image(painterResource(R.drawable.art_afterglow), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xCC121020)), startY = 100f)))
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                    Text("FEATURED ALBUM", color = Color(0xFFE7DCF6), fontSize = 10.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    Text("Afterglow", color = Color.White, style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 6.dp))
                    Text("Melodee Sessions", color = Color(0xFFEEE1F2), modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                    PlayCircle({ vm.playAlbum("afterglow") }, enabled = playback.connected)
                }
            }
        }
        item {
            SectionHeader("Made for the moment")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("daily-mix" to listOf(Color(0xFF176B80), Color(0xFF123B59)),
                    "chill" to listOf(Color(0xFF7847AF), Color(0xFF352049))).forEach { (id, colors) ->
                    val playlist = catalog.playlist(id)!!
                    Box(Modifier.weight(1f).height(136.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(colors)).clickable { onPlaylist(id) }) {
                        Icon(if (id == "daily-mix") Icons.Rounded.GraphicEq else Icons.Rounded.NightsStay,
                            null, tint = Color.White.copy(alpha = .12f),
                            modifier = Modifier.size(100.dp).align(Alignment.TopEnd).offset(x = 20.dp, y = (-10).dp))
                        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            Text(if (id == "daily-mix") "YOUR DAILY SOUND" else "SLOW IT DOWN", color = Color.White.copy(alpha = .7f),
                                fontSize = 9.sp, letterSpacing = 1.sp)
                            Text(playlist.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("A little more to love", "Library", onLibrary)
            TrackRow(catalog.tracks[3], catalog, { vm.playAlbum("nightfall") },
                playing = playback.current?.id == catalog.tracks[3].id)
            TrackRow(catalog.tracks[5], catalog, { vm.playAlbum("nightfall", 2) },
                playing = playback.current?.id == catalog.tracks[5].id)
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
fun SearchScreen(vm: PlayerViewModel, playback: PlaybackUiState) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { vm.catalog.search(query) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            PageHeader("Search", "Find your next favorite.")
            OutlinedTextField(value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = true,
                placeholder = { Text("Songs, artists, albums", color = Muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                shape = RoundedCornerShape(18.dp),
            )
        }
        if (query.isBlank()) {
            item {
                SectionHeader("Browse")
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BrowseTile("Afterglow", "Warm & expansive", Color(0xFF8E4875), Modifier.weight(1f)) { query = "Afterglow" }
                    BrowseTile("Nightfall", "Quiet & reflective", Color(0xFF304E80), Modifier.weight(1f)) { query = "Nightfall" }
                }
            }
            item { SectionHeader("Quick picks") }
        } else {
            item { Text("${results.size} ${if (results.size == 1) "result" else "results"}", color = Muted) }
        }
        if (results.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.SearchOff, null, tint = Muted, modifier = Modifier.size(44.dp))
                    Text("No matches yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                    Text("Try a song, album or artist name.", color = Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            items(results, key = { it.id }) { track ->
                TrackRow(track, vm.catalog, { vm.play(results.map { it.id }, results.indexOf(track)) },
                    playback.current?.id == track.id)
            }
        }
    }
}

@Composable
private fun BrowseTile(title: String, subtitle: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(130.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(color, color.copy(alpha = .35f))))
        .clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(Icons.Rounded.Album, null, tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(28.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = .7f))
        }
    }
}

@Composable
fun LibraryScreen(vm: PlayerViewModel, settings: UserSettings, playback: PlaybackUiState,
    onAlbum: (String) -> Unit, onPlaylist: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("Albums") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Your Library", "A collection that feels like you.") }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF362547), Surface)))
                .clickable { selected = "Favorites" }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFEFB1E5), modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Liked songs", style = MaterialTheme.typography.titleMedium)
                    Text("${settings.favorites.size} saved favorites", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Albums", "Playlists", "Songs").forEach { tab ->
                    FilterChip(selected = selected == tab, onClick = { selected = tab }, label = { Text(tab) })
                }
            }
        }
        when (selected) {
            "Albums" -> item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    vm.catalog.albums.forEach { album ->
                        Column(Modifier.weight(1f).clickable { onAlbum(album.id) }) {
                            Cover(album.artwork, Modifier.fillMaxWidth().aspectRatio(1f), album.title)
                            Text(album.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                            Text(album.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            "Playlists" -> items(vm.catalog.playlists, key = { it.id }) { playlist ->
                SettingRow(Icons.Rounded.QueueMusic, playlist.title, "${playlist.trackIds.size} tracks",
                    onClick = { onPlaylist(playlist.id) }, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = Muted) })
            }
            else -> {
                val tracks = if (selected == "Favorites") vm.catalog.tracks.filter { it.id in settings.favorites } else vm.catalog.tracks
                if (tracks.isEmpty()) item {
                    Text("Your favorites start here.", style = MaterialTheme.typography.titleLarge)
                    Text("Tap the heart on the player to save a song.", color = Muted, modifier = Modifier.padding(top = 8.dp))
                }
                items(tracks, key = { it.id }) { track ->
                    TrackRow(track, vm.catalog, { vm.play(tracks.map { it.id }, tracks.indexOf(track)) },
                        playback.current?.id == track.id)
                }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Text("DEMO COLLECTION", color = Muted, letterSpacing = 2.sp, fontSize = 10.sp)
            Text("Six original previews to explore the Melodee experience.", color = Muted,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun CollectionScreen(vm: PlayerViewModel, playback: PlaybackUiState, id: String, playlist: Boolean, onBack: () -> Unit) {
    val album = if (!playlist) vm.catalog.album(id) else null
    val mix = if (playlist) vm.catalog.playlist(id) else null
    val ids = album?.trackIds ?: mix?.trackIds.orEmpty()
    val title = album?.title ?: mix?.title ?: "Collection"
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader(if (playlist) "Playlist" else "Album", onBack = onBack) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Cover(album?.artwork ?: R.drawable.art_afterglow, Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f), title)
            }
        }
        item {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 10.dp))
            Text(album?.artist ?: "Curated by Melodee", color = Muted, modifier = Modifier.padding(top = 6.dp))
            Text(album?.description ?: mix?.subtitle.orEmpty(), color = Muted,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${ids.size} tracks · Demo previews", color = Muted, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Button(onClick = { vm.play(ids) }, enabled = playback.connected) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (playlist) "Play playlist" else "Play album")
                }
            }
        }
        items(ids) { trackId ->
            val track = vm.catalog.track(trackId)!!
            TrackRow(track, vm.catalog, { vm.play(ids, ids.indexOf(trackId)) }, playback.current?.id == track.id)
        }
    }
}

