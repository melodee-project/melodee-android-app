package org.melodee.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.melodee.android.BuildConfig
import org.melodee.android.R
import org.melodee.android.data.UserSettings
import org.melodee.android.playback.PlayerViewModel

@Composable
fun SettingsScreen(vm: PlayerViewModel, settings: UserSettings) {
    var showAuto by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Settings", "Make yourself at home.") }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.melodee_logo), "Melodee logo", Modifier.size(72.dp))
                Column(Modifier.padding(start = 18.dp)) {
                    Text("Melodee", style = MaterialTheme.typography.titleLarge)
                    Text("Your music, everywhere.", color = Muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { SectionHeader("Your listening experience") }
        item {
            SettingRow(Icons.Rounded.History, "Remember my place", "Restore the queue when you return.",
                trailing = { Switch(checked = settings.rememberPosition, onCheckedChange = vm::rememberPosition) })
        }
        item {
            SettingRow(Icons.Rounded.Headphones, "Pause on disconnect", "Pause when headphones disconnect.",
                trailing = { Switch(checked = settings.pauseOnDisconnect, onCheckedChange = vm::pauseOnDisconnect) })
        }
        item { SectionHeader("Connected experiences") }
        item {
            SettingRow(Icons.Rounded.DirectionsCar, "Android Auto", "Browse and play from your car.", { showAuto = true },
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = Muted) })
        }
        item { SectionHeader("About") }
        item {
            SettingRow(Icons.Rounded.Info, "About Melodee", "Version ${BuildConfig.VERSION_NAME}", { showAbout = true },
                trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = Muted) })
        }
        item {
            Surface(color = Raised, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(18.dp)) {
                    Text("You're exploring the demo library", style = MaterialTheme.typography.titleMedium)
                    Text("Enjoy six original previews. Connecting your Melodee server is the next step for this starter.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    if (showAuto) AlertDialog(
        onDismissRequest = { showAuto = false },
        icon = { Icon(Icons.Rounded.DirectionsCar, null, tint = Accent) },
        title = { Text("Music for the road") },
        text = { Text("Connect your phone to Android Auto, then select Melodee in the car's media apps. Your phone and car share one player and queue.\n\nThis starter exposes the demo albums, playlists and search. Vehicle availability and automatic resumption depend on Android Auto and your car's settings.") },
        confirmButton = { TextButton(onClick = { showAuto = false }) { Text("Got it") } },
    )
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        title = { Text("Melodee") },
        text = { Text("A fresh start for your music.\n\nNative Android with a shared background player, original demo music and your new Melodee identity.\n\nThis build contains no sign-in credentials, telemetry or user-managed downloads.") },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
    )
}

