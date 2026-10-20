package app.simpleaudiobooks.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.ui.book.BookScreen
import app.simpleaudiobooks.ui.components.LocalApp
import app.simpleaudiobooks.ui.components.LocalBottomInset
import app.simpleaudiobooks.ui.library.LibraryScreen
import app.simpleaudiobooks.ui.player.MiniPlayer
import app.simpleaudiobooks.ui.player.MiniPlayerHeight
import app.simpleaudiobooks.ui.player.PlayerScreen
import app.simpleaudiobooks.ui.settings.SettingsScreen
import app.simpleaudiobooks.ui.stats.StatsScreen

@Composable
fun AppRoot(openPlayerSignal: Int) {
    val app = LocalApp.current
    val nav = rememberNavController()
    val entries by app.library.entries.collectAsStateWithLifecycle()
    val snap by app.playback.snapshot.collectAsStateWithLifecycle()
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var handledSignal by rememberSaveable { mutableIntStateOf(0) }

    val current: Entry? = remember(entries, snap.bookId) {
        entries.firstOrNull { it.id == snap.bookId }
            ?: entries.filter { it.state.lastPlayedAt > 0 && !it.state.finished }.maxByOrNull { it.state.lastPlayedAt }
    }

    LaunchedEffect(openPlayerSignal, current != null) {
        if (openPlayerSignal > handledSignal && current != null) {
            handledSignal = openPlayerSignal
            playerOpen = true
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = if (current != null) MiniPlayerHeight + navBottom + 12.dp else navBottom

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompositionLocalProvider(LocalBottomInset provides bottomInset) {
            NavHost(
                modifier = if (playerOpen) Modifier.clearAndSetSemantics {} else Modifier,
                navController = nav,
                startDestination = "library",
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 8 } },
            ) {
                composable("library") { entry ->
                    LibraryScreen(
                        onOpenBook = { id -> nav.navigateFrom(entry, "book/$id") },
                        onOpenSettings = { nav.navigateFrom(entry, "settings") },
                    )
                }
                composable("book/{id}") { entry ->
                    BookScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        onBack = { nav.backFrom(entry) },
                        onOpenPlayer = { playerOpen = true },
                    )
                }
                composable("settings") { entry ->
                    SettingsScreen(onBack = { nav.backFrom(entry) }, onOpenStats = { nav.navigateFrom(entry, "stats") })
                }
                composable("stats") { entry -> StatsScreen(onBack = { nav.backFrom(entry) }) }
            }
        }

        if (current != null && !playerOpen) {
            MiniPlayer(
                current,
                snap,
                onOpen = { playerOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(
            visible = playerOpen && current != null,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            val e = current
            if (e != null) {
                PlayerScreen(
                    e,
                    onClose = { playerOpen = false },
                    onOpenBook = {
                        playerOpen = false
                        nav.navigate("book/${e.id}") { launchSingleTop = true }
                    },
                )
            }
        }
    }
}

/**
 * Navigiert nur, wenn die auslösende Seite noch oben liegt. Während eines Übergangs ist die
 * verschwindende Seite noch antippbar; ihre Tipps dürfen nichts mehr auslösen.
 */
private fun NavController.navigateFrom(entry: NavBackStackEntry, route: String) {
    if (currentBackStackEntry?.id != entry.id) return
    navigate(route) { launchSingleTop = true }
}

/**
 * Zurück nur von der obersten Seite und nie über die Bibliothek hinaus. Sonst nimmt ein schneller
 * zweiter Tipp auf den Pfeil auch die Bibliothek vom Stapel, und es bleibt ein leerer Bildschirm,
 * auf dem kein Zurück mehr wirkt.
 */
private fun NavController.backFrom(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id != entry.id || previousBackStackEntry == null) return
    popBackStack()
}
