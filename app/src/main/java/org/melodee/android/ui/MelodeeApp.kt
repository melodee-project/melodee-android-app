package org.melodee.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import org.melodee.android.playback.PlayerViewModel

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("home", "Home", Icons.Rounded.Home),
    Destination("search", "Search", Icons.Rounded.Search),
    Destination("library", "Library", Icons.Rounded.LibraryMusic),
    Destination("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun MelodeeApp(vm: PlayerViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val playback by vm.state.collectAsStateWithLifecycle()
    val preferences by vm.settings.collectAsStateWithLifecycle()
    val fullPlayer = route == "player" || route == "queue"
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(playback.error) {
        playback.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    fun navigate(destination: String) {
        nav.navigate(destination) { launchSingleTop = true }
    }
    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!fullPlayer) {
                Column(Modifier.fillMaxWidth()) {
                    MiniPlayer(playback, vm.catalog, { navigate("player") }, vm::togglePlay, vm::next)
                    NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                        destinations.forEach { item ->
                            NavigationBarItem(
                                selected = route == item.route || (item.route == "library" &&
                                    (route.startsWith("album") || route.startsWith("playlist"))),
                                onClick = {
                                    nav.navigate(item.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent, selectedTextColor = Accent,
                                    indicatorColor = Color(0xFF30213F), unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding)) {
            composable("home") {
                HomeScreen(vm, playback, { navigate("album/$it") }, { navigate("playlist/$it") },
                    { navigate("settings") }, { navigate("library") })
            }
            composable("search") { SearchScreen(vm, playback) }
            composable("library") {
                LibraryScreen(vm, preferences, playback, { navigate("album/$it") }, { navigate("playlist/$it") })
            }
            composable("settings") { SettingsScreen(vm, preferences) }
            composable("album/{id}") { entry ->
                CollectionScreen(vm, playback, entry.arguments?.getString("id").orEmpty(), false) { nav.popBackStack() }
            }
            composable("playlist/{id}") { entry ->
                CollectionScreen(vm, playback, entry.arguments?.getString("id").orEmpty(), true) { nav.popBackStack() }
            }
            composable("player") {
                NowPlayingScreen(vm, playback, preferences, { nav.popBackStack() }, { navigate("queue") })
            }
            composable("queue") { QueueScreen(vm, playback) { nav.popBackStack() } }
        }
    }
}

