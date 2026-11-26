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
import org.melodee.android.playback.LibraryUiState
import org.melodee.android.playback.PlayerViewModel

@Composable
fun SettingsScreen(vm: PlayerViewModel, settings: UserSettings, library: LibraryUiState, onConnect: () -> Unit) {
    var showAuto by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
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
        item { SectionHeader("Your Melodee server") }
        if (library.signedIn) {
            item {
                SettingRow(Icons.Rounded.CloudDone, library.accountName ?: "Connected",
                    "Streaming from your Melodee server.", trailing = {
                        TextButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
                    })
            }
        } else {
            item {
                SettingRow(Icons.Rounded.Cloud, "Connect to Melodee",
                    "Sign in to stream your own library.", onConnect,
                    trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = Muted) })
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
                    Text("Connected to your Melodee library", style = MaterialTheme.typography.titleMedium)
                    Text("Music streams from your Melodee server. A temporary playback buffer may remain for uninterrupted playback; there is no download library.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    if (showAuto) AlertDialog(
        onDismissRequest = { showAuto = false },
        icon = { Icon(Icons.Rounded.DirectionsCar, null, tint = Accent) },
        title = { Text("Music for the road") },
        text = { Text("Connect your phone to Android Auto, then select Melodee in the car's media apps. Your phone and car share one player and queue.\n\nVehicle availability and automatic resumption depend on Android Auto and your car's settings. The app cannot force automatic launch.") },
        confirmButton = { TextButton(onClick = { showAuto = false }) { Text("Got it") } },
    )
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        title = { Text("Melodee") },
        text = { Text("A calm home for your music.\n\nNative Android with a shared background player, Android Auto discovery and account-scoped authentication. No telemetry, no user-managed downloads.") },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
    )
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out of Melodee?") },
        text = { Text("Credentials and temporary account audio will be removed. Your queue identity and position are cleared for this account.") },
        confirmButton = {
            TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") }
        },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
    )
}
