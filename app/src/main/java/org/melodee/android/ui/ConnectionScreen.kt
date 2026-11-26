package org.melodee.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import org.melodee.android.live.auth.LiveLoginResult
import org.melodee.android.playback.PlayerViewModel

@Composable
fun ConnectionScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    var origin by remember { mutableStateOf("https://") }
    var userName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var showGooglePlaceholder by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        PageHeader("Connect", "Use your Melodee server account.", onBack)
        Text("Melodee connects directly to your server. Use an HTTPS origin; the app never stores your password.",
            color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = origin, onValueChange = { origin = it; error = null },
            label = { Text("Server address") }, singleLine = true,
            placeholder = { Text("https://music.example.com") },
            leadingIcon = { Icon(Icons.Rounded.Cloud, null) },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp), enabled = !busy,
        )
        OutlinedTextField(
            value = userName, onValueChange = { userName = it; error = null },
            label = { Text("Username") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Person, null) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !busy,
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it; error = null },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !busy,
        )
        error?.let {
            Surface(color = Raised, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp))
            }
        }
        if (success) {
            Surface(color = Raised, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Signed in. Your Melodee library will load now.", color = SoftWhite,
                    modifier = Modifier.padding(14.dp))
            }
        }
        Button(
            onClick = {
                busy = true
                error = null
                success = false
                vm.login(origin, userName, password) { result ->
                    busy = false
                    when (result) {
                        is LiveLoginResult.Success -> { success = true; password = "" }
                        is LiveLoginResult.Failure -> error = result.message
                    }
                }
            },
            enabled = !busy && origin.isNotBlank() && userName.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Ink, strokeWidth = 2.dp)
            else Icon(Icons.AutoMirrored.Rounded.Login, null)
            Spacer(Modifier.width(10.dp))
            Text(if (busy) "Connecting…" else "Connect")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp), color = Raised)
        OutlinedButton(
            onClick = { showGooglePlaceholder = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("G", style = MaterialTheme.typography.titleMedium, color = Accent)
            Spacer(Modifier.width(10.dp))
            Text("Continue with Google")
        }
        Text("Google sign-in is being set up. Use your Melodee username and password for now.",
            color = Muted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy) {
            Text("Not now")
        }
        Spacer(Modifier.height(24.dp))
        Text("Why HTTPS? Melodee sends credentials and signed stream URLs. Cleartext is accepted only for debug loopback builds.",
            color = Muted, style = MaterialTheme.typography.labelSmall)
    }
    if (showGooglePlaceholder) {
        AlertDialog(
            onDismissRequest = { showGooglePlaceholder = false },
            title = { Text("Google sign-in") },
            text = {
                Text("Google sign-in will be available after the Google OAuth app and Melodee server configuration are complete. For now, sign in with your Melodee username and password.")
            },
            confirmButton = {
                TextButton(onClick = { showGooglePlaceholder = false }) { Text("OK") }
            },
        )
    }
}
