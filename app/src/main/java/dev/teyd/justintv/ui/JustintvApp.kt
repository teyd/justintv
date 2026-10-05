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
import dev.teyd.justintv.feature.watch.MiniPlayer
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

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onWatch = { login -> navController.navigate("$ROUTE_WATCH/${Uri.encode(login)}") },
                    onOpenGame = { name -> navController.navigate("$ROUTE_GAME/${Uri.encode(name)}") },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                )
            }
            composable(
                route = "$ROUTE_GAME/{$GAME_ARG_NAME}",
                arguments = listOf(navArgument(GAME_ARG_NAME) { type = NavType.StringType }),
            ) {
                GameScreen(
                    onBack = { navController.popBackStack() },
                    onWatch = { login -> navController.navigate("$ROUTE_WATCH/${Uri.encode(login)}") },
                )
            }
            composable(
                route = "$ROUTE_WATCH/{$WATCH_ARG_LOGIN}",
                arguments = listOf(navArgument(WATCH_ARG_LOGIN) { type = NavType.StringType }),
            ) { entry ->
                val login = entry.arguments?.getString(WATCH_ARG_LOGIN).orEmpty()
                WatchScreen(
                    channelLogin = login,
                    onMinimize = {
                        playback.minimize()
                        navController.popBackStack()
                    },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }

        if (chrome == PlayerChrome.Mini && playing.channelLogin.isNotBlank()) {
            MiniPlayer(
                modifier = Modifier.zIndex(1f),
                viewModel = playback,
                onExpand = {
                    playback.expand()
                    navController.navigate("$ROUTE_WATCH/${Uri.encode(playing.channelLogin)}")
                },
            )
        }
    }
}
