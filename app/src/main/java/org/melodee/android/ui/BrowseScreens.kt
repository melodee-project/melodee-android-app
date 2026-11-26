package org.melodee.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodee.android.model.Album
import org.melodee.android.model.CatalogMode
import org.melodee.android.model.Playlist
import org.melodee.android.model.Track
import org.melodee.android.playback.LibraryUiState
import org.melodee.android.playback.PlaybackUiState
import org.melodee.android.playback.PlayerViewModel

@Composable
fun HomeScreen(vm: PlayerViewModel, playback: PlaybackUiState, library: LibraryUiState,
    onAlbum: (String) -> Unit, onPlaylist: (String) -> Unit, onSettings: () -> Unit, onLibrary: () -> Unit) {
    val featured = library.home.recentAlbums.firstOrNull() ?: library.albums.firstOrNull()
    val recent = library.home.recentTracks.ifEmpty { library.tracks }.take(3)
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(org.melodee.android.R.drawable.melodee_logo),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                )
                Text("Melodee", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Surface(color = Raised, shape = RoundedCornerShape(30.dp)) {
                    Text(
                        if (library.mode == CatalogMode.DEMO) "Demo library" else library.accountName.orEmpty().ifBlank { "Connected" },
                        style = MaterialTheme.typography.labelSmall, color = Muted,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.AccountCircle, "Open settings", tint = Muted) }
            }
        }
        item {
            Column(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                Text("Find your moment.", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (library.mode == CatalogMode.DEMO) "Your music, wherever life takes you."
                    else "Your library, ready for the road.",
                    color = Muted, modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
        if (library.loading && featured == null) {
            item { LoadingPanel("Loading your library…") }
        }
        if (library.error != null && featured == null) {
            item { ErrorPanel(library.error, vm::refresh) }
        }
        if (featured != null) {
            item {
                Box(Modifier.fillMaxWidth().height(242.dp).clip(RoundedCornerShape(24.dp))
                    .clickable { onAlbum(featured.id) }) {
                    Cover(featured.artwork, Modifier.fillMaxSize(), "${featured.title} album artwork")
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC121020)), startY = 100f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Text(if (library.mode == CatalogMode.DEMO) "FEATURED ALBUM" else "FROM YOUR LIBRARY",
                            color = Color(0xFFE7DCF6), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                        Text(featured.title, color = Color.White, style = MaterialTheme.typography.headlineLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp))
                        Text(featured.artist, color = Color(0xFFEEE1F2), modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                        PlayCircle({ vm.playAlbum(featured.id) }, enabled = playback.connected)
                    }
                }
            }
        }
        val playlists = library.home.playlists.ifEmpty { library.playlists }.take(2)
        if (playlists.isNotEmpty()) {
            item {
                SectionHeader("Made for the moment")
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    playlists.forEachIndexed { index, playlist ->
                        val colors = if (index == 0) listOf(Color(0xFF176B80), Color(0xFF123B59))
                        else listOf(Color(0xFF7847AF), Color(0xFF352049))
                        PlaylistTile(playlist, colors, library.mode, Modifier.weight(1f)) { onPlaylist(playlist.id) }
                    }
                    if (playlists.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                SectionHeader("A little more to love", "Library", onLibrary)
                recent.forEach { track ->
                    TrackRow(track, { vm.play(listOf(track.id)) },
                        playing = playback.current?.id == track.id)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun PlaylistTile(playlist: Playlist, colors: List<Color>, mode: CatalogMode,
    modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(136.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(colors))
        .clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(if (playlist.id == "daily-mix") Icons.Rounded.GraphicEq else Icons.Rounded.NightsStay,
            null, tint = Color.White.copy(alpha = .12f), modifier = Modifier.size(72.dp))
        Column {
            Text(
                if (mode == CatalogMode.DEMO) {
                    if (playlist.id == "daily-mix") "YOUR DAILY SOUND" else "SLOW IT DOWN"
                } else "PLAYLIST",
                color = Color.White.copy(alpha = .7f), fontSize = 9.sp, letterSpacing = 1.sp,
            )
            Text(playlist.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Text(playlist.description.ifBlank { "${playlist.songCount} tracks" },
                color = Color.White.copy(alpha = .7f), fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SearchScreen(vm: PlayerViewModel, playback: PlaybackUiState, library: LibraryUiState) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { vm.search(query) }
    val results = library.search
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            PageHeader("Search", if (library.mode == CatalogMode.DEMO) "Find your next favorite." else "Search your Melodee library.")
            OutlinedTextField(value = query, onValueChange = { query = it; },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp), singleLine = true,
                placeholder = { Text("Songs, artists, albums", color = Muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                },
                shape = RoundedCornerShape(18.dp),
            )
        }
        if (library.searchLoading) {
            item { LoadingPanel("Searching…") }
        } else if (query.isBlank()) {
            item {
                SectionHeader("Browse")
                val albums = library.albums.take(2)
                if (albums.isEmpty()) {
                    Text("Connect your server to browse your library.", color = Muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        albums.forEach { album ->
                            Column(Modifier.weight(1f).clickable { query = album.title }) {
                                Cover(album.artwork, Modifier.fillMaxWidth().aspectRatio(1f), "${album.title} artwork")
                                Text(album.title, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                Text(album.artist, color = Muted, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (albums.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            val quick = library.home.recentTracks.ifEmpty { library.tracks }.take(6)
            if (quick.isNotEmpty()) {
                item { SectionHeader("Quick picks") }
                items(quick, key = { it.id }) { track ->
                    TrackRow(track, { vm.play(listOf(track.id)) }, playback.current?.id == track.id)
                }
            }
        } else if (results.total == 0) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.SearchOff, null, tint = Muted, modifier = Modifier.size(44.dp))
                    Text("No matches yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                    Text("Try a song, album, artist or playlist name.", color = Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            if (results.tracks.isNotEmpty()) {
                item { SectionHeader("Songs") }
                items(results.tracks, key = { it.id }) { track ->
                    TrackRow(track, { vm.play(results.tracks.map(Track::id), results.tracks.indexOf(track)) },
                        playback.current?.id == track.id)
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                items(results.albums, key = { it.id }) { album ->
                    AlbumRow(album) { vm.playAlbum(album.id) }
                }
            }
            if (results.playlists.isNotEmpty()) {
                item { SectionHeader("Playlists") }
                items(results.playlists, key = { it.id }) { playlist ->
                    SettingRow(Icons.AutoMirrored.Rounded.QueueMusic, playlist.title,
                        "${playlist.songCount} tracks", { vm.playPlaylist(playlist.id) },
                        trailing = { Icon(Icons.Rounded.PlayArrow, "Play ${playlist.title}", tint = Muted) })
                }
            }
            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Artists") }
                items(results.artists, key = { it.id }) { artist ->
                    SettingRow(Icons.Rounded.Person, artist.name, "${artist.albumCount} albums",
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = Muted) })
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(album.artwork, Modifier.size(54.dp), "${album.title} artwork")
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(album.title, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artist, color = Muted, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = Muted)
    }
}

@Composable
fun LibraryScreen(vm: PlayerViewModel, settings: org.melodee.android.data.UserSettings,
    playback: PlaybackUiState, library: LibraryUiState,
    onAlbum: (String) -> Unit, onPlaylist: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("Albums") }
    val demoFavorites = library.tracks.filter { it.id in settings.favorites }
    val favorites = if (library.mode == CatalogMode.DEMO) demoFavorites else library.favorites
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Your Library", if (library.mode == CatalogMode.DEMO) "A collection that feels like you." else "Your Melodee collection.") }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF362547), Surface)))
                .clickable { selected = "Favorites" }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFEFB1E5), modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Liked songs", style = MaterialTheme.typography.titleMedium)
                    Text("${favorites.size} saved favorites", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Albums", "Playlists", "Songs", "Favorites").forEach { tab ->
                    FilterChip(selected = selected == tab, onClick = { selected = tab }, label = { Text(tab) })
                }
            }
        }
        when (selected) {
            "Albums" -> {
                if (library.albums.isEmpty()) item { EmptyPanel("No albums yet", "Connect your server or sync your library.") }
                items(library.albums.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { album ->
                            Column(Modifier.weight(1f).clickable { onAlbum(album.id) }) {
                                Cover(album.artwork, Modifier.fillMaxWidth().aspectRatio(1f), "${album.title} artwork")
                                Text(album.title, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 12.dp))
                                Text(album.artist, color = Muted, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            "Playlists" -> {
                if (library.playlists.isEmpty()) item { EmptyPanel("No playlists yet", "Create or sync a playlist on your Melodee server.") }
                items(library.playlists, key = { it.id }) { playlist ->
                    SettingRow(Icons.AutoMirrored.Rounded.QueueMusic, playlist.title,
                        "${playlist.songCount} tracks", { onPlaylist(playlist.id) },
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = Muted) })
                }
            }
            "Favorites" -> {
                if (favorites.isEmpty()) item { EmptyPanel("Your favorites start here.", "Tap the heart on the player to save a song.") }
                items(favorites, key = { it.id }) { track ->
                    TrackRow(track, { vm.play(favorites.map(Track::id), favorites.indexOf(track)) },
                        playback.current?.id == track.id)
                }
            }
            else -> {
                val tracks = library.tracks
                if (tracks.isEmpty()) item { EmptyPanel("No tracks yet", "Your server library will appear here.") }
                items(tracks, key = { it.id }) { track ->
                    TrackRow(track, { vm.play(tracks.map(Track::id), tracks.indexOf(track)) },
                        playback.current?.id == track.id)
                }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Text(if (library.mode == CatalogMode.DEMO) "DEMO COLLECTION" else "MELODEE SERVER",
                color = Muted, letterSpacing = 2.sp, fontSize = 10.sp)
            Text(if (library.mode == CatalogMode.DEMO)
                "Six original previews to explore the Melodee experience."
            else "Metadata is cached for the current account; audio remains temporary.",
                color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun CollectionScreen(vm: PlayerViewModel, playback: PlaybackUiState, library: LibraryUiState,
    id: String, playlist: Boolean, onBack: () -> Unit) {
    LaunchedEffect(id, playlist) {
        if (playlist) vm.openPlaylist(id) else vm.openAlbum(id)
    }
    val album = library.selectedAlbum
    val mix = library.selectedPlaylist
    val tracks = library.selectedTracks
    val title = if (playlist) mix?.title ?: "Playlist" else album?.title ?: "Album"
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader(if (playlist) "Playlist" else "Album", onBack = onBack) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val artwork = if (playlist) mix?.artwork else album?.artwork
                if (artwork != null) Cover(artwork, Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f), "$title artwork")
                else Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                    .background(Surface), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            }
        }
        item {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 10.dp))
            Text(if (playlist) mix?.ownerName?.ifBlank { "Curated playlist" } ?: "Curated playlist"
            else album?.artist ?: "", color = Muted, modifier = Modifier.padding(top = 6.dp))
            val description = if (playlist) mix?.description else album?.description
            if (!description.isNullOrBlank()) Text(description, color = Muted,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${tracks.size} tracks${if (library.mode == CatalogMode.DEMO) " · Demo previews" else ""}",
                    color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Button(onClick = { if (playlist) vm.playPlaylist(id) else vm.playAlbum(id) },
                    enabled = playback.connected && tracks.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (playlist) "Play playlist" else "Play album")
                }
            }
        }
        if (library.error != null && tracks.isEmpty()) {
            item { ErrorPanel(library.error, vm::refresh) }
        } else if (tracks.isEmpty()) {
            item { LoadingPanel("Loading tracks…") }
        } else {
            items(tracks, key = { "${it.id}-${tracks.indexOf(it)}" }) { track ->
                TrackRow(track, { vm.play(tracks.map(Track::id), tracks.indexOf(track)) },
                    playback.current?.id == track.id)
            }
        }
    }
}

@Composable
fun LoadingPanel(message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Accent)
        Text(message, color = Muted, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Surface(color = Raised, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Something needs attention", style = MaterialTheme.typography.titleMedium)
            Text(message, color = Muted, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 6.dp)) { Text("Try again") }
        }
    }
}

@Composable
fun EmptyPanel(title: String, subtitle: String) {
    Surface(color = Surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
