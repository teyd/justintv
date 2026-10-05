package dev.teyd.justintv.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.teyd.justintv.feature.settings.SettingsScreen
import dev.teyd.justintv.feature.streams.GAME_ARG_NAME
import dev.teyd.justintv.feature.streams.GameScreen
import dev.teyd.justintv.feature.streams.HomeScreen
import dev.teyd.justintv.core.player.VideoPlayer
import androidx.compose.ui.unit.dp
import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.feature.watch.PlaybackDockHeight
import dev.teyd.justintv.feature.watch.PlaybackOverlay
import dev.teyd.justintv.feature.watch.PlayerChrome
import dev.teyd.justintv.feature.watch.WATCH_ARG_LOGIN
import dev.teyd.justintv.feature.watch.WatchScreen
import dev.teyd.justintv.feature.watch.activityPlayback

private const val ROUTE_HOME = "home"
private const val ROUTE_GAME = "game"
private const val ROUTE_WATCH = "watch"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun JustintvApp() {
    val navController = rememberNavController()
    val playback = activityPlayback()
    val chrome by playback.chrome.collectAsStateWithLifecycle()
    val playing by playback.state.collectAsStateWithLifecycle()
    val settings = hiltViewModel<PipSettingsViewModel>()
    val inPip = rememberInPip()
    PipBinding(playback, settings.store)

    if (inPip && chrome != PlayerChrome.Hidden) {
        VideoPlayer(player = playback.playerHolder, modifier = Modifier.fillMaxSize())
        return
    }

    val docked = chrome == PlayerChrome.Mini && playing.channelLogin.isNotBlank()
    val dockPadding = if (docked) PlaybackDockHeight else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onWatch = { stream -> watch(playback, navController, stream) },
                    onOpenGame = { name -> navController.navigate("$ROUTE_GAME/${Uri.encode(name)}") { launchSingleTop = true } },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                    extraBottomPadding = dockPadding,
                )
            }
            composable(
                route = "$ROUTE_GAME/{$GAME_ARG_NAME}",
                arguments = listOf(navArgument(GAME_ARG_NAME) { type = NavType.StringType }),
            ) {
                GameScreen(
                    onBack = { navController.popBackStack() },
                    onWatch = { stream -> watch(playback, navController, stream) },
                    extraBottomPadding = dockPadding,
                )
            }
            composable(
                route = "$ROUTE_WATCH/{$WATCH_ARG_LOGIN}",
                arguments = listOf(navArgument(WATCH_ARG_LOGIN) { type = NavType.StringType }),
            ) { entry ->
                val login = entry.arguments?.getString(WATCH_ARG_LOGIN).orEmpty()
                WatchScreen(
                    channelLogin = login,
                    onMinimize = { minimize(playback, navController) },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }

        PlaybackOverlay(
            viewModel = playback,
            onExpand = {
                playback.expand()
                navController.navigate("$ROUTE_WATCH/${Uri.encode(playing.channelLogin)}") {
                    launchSingleTop = true
                }
            },
            onMinimize = { minimize(playback, navController) },
            modifier = Modifier.zIndex(1f),
        )
    }
}

private fun minimize(
    playback: dev.teyd.justintv.feature.watch.WatchViewModel,
    navController: androidx.navigation.NavHostController,
) {
    playback.minimize()
    navController.popBackStack()
}

private fun watch(
    playback: dev.teyd.justintv.feature.watch.WatchViewModel,
    navController: androidx.navigation.NavHostController,
    stream: LiveStream,
) {
    playback.open(stream.login, stream.displayName, stream.title)
    navController.navigate("$ROUTE_WATCH/${Uri.encode(stream.login)}") { launchSingleTop = true }
}
