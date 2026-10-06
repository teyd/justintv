package dev.teyd.justintv.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.teyd.justintv.core.model.ChannelHit
import dev.teyd.justintv.core.model.ChannelPresence
import dev.teyd.justintv.core.model.LiveStream
import dev.teyd.justintv.core.player.VideoPlayer
import dev.teyd.justintv.feature.settings.SettingsScreen
import dev.teyd.justintv.feature.streams.GAME_ARG_NAME
import dev.teyd.justintv.feature.streams.GameScreen
import dev.teyd.justintv.feature.streams.HomeScreen
import dev.teyd.justintv.feature.watch.PlaybackDockHeight
import dev.teyd.justintv.feature.watch.PlaybackOverlay
import dev.teyd.justintv.feature.watch.PlayerChrome
import dev.teyd.justintv.feature.watch.WATCH_ARG_LOGIN
import dev.teyd.justintv.feature.watch.WatchScreen
import dev.teyd.justintv.feature.watch.activityChat
import dev.teyd.justintv.feature.watch.activityPlayback

private const val ROUTE_HOME = "home"
private const val ROUTE_GAME = "game"
private const val ROUTE_WATCH = "watch"
private const val ROUTE_SETTINGS = "settings"

private const val ENTER_MS = 180
private const val EXIT_MS = 90
private const val SLIDE_FRACTION = 24

@Composable
fun JustintvApp() {
    val navController = rememberNavController()
    val playback = activityPlayback()
    val chat = activityChat()
    val chrome by playback.chrome.collectAsStateWithLifecycle()
    LaunchedEffect(chrome) {
        if (chrome == PlayerChrome.Hidden) chat.close()
    }
    val playing by playback.state.collectAsStateWithLifecycle()
    val settings = hiltViewModel<PipSettingsViewModel>()
    val inPip = rememberInPip()
    PipBinding(playback, settings.store)
    val keepScreenOn by settings.store.keepScreenOn.collectAsStateWithLifecycle(initialValue = true)
    KeepAwake(enabled = chrome == PlayerChrome.Expanded && !inPip && keepScreenOn)

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
            // The default is a 700 ms crossfade, which made every tap feel late: the list
            // faded out slowly under a video slot that had already appeared. These are short
            // and the incoming screen rises a few pixels, so it reads as one movement.
            enterTransition = {
                fadeIn(tween(ENTER_MS, delayMillis = EXIT_MS)) +
                    slideInVertically(tween(ENTER_MS + EXIT_MS)) { it / SLIDE_FRACTION }
            },
            exitTransition = { fadeOut(tween(EXIT_MS)) },
            popEnterTransition = { fadeIn(tween(ENTER_MS)) },
            popExitTransition = {
                fadeOut(tween(EXIT_MS)) + slideOutVertically(tween(ENTER_MS)) { it / SLIDE_FRACTION }
            },
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onWatch = { stream -> watch(playback, chat, navController, stream) },
                    onOpenChannel = { hit -> watchChannel(playback, chat, navController, hit) },
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
                    onWatch = { stream -> watch(playback, chat, navController, stream) },
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
    chat: dev.teyd.justintv.feature.watch.ChatViewModel,
    navController: androidx.navigation.NavHostController,
    stream: LiveStream,
) {
    // Playback and chat both start on the tap, not when the watch screen has finished
    // composing, so neither waits on the navigation animation.
    playback.open(
        channel = stream.login,
        displayName = stream.displayName,
        title = stream.title,
        previewUrl = stream.previewUrl,
        viewers = stream.viewerCount,
        startedAt = stream.startedAt,
    )
    chat.open(stream.login)
    navController.navigate("$ROUTE_WATCH/${Uri.encode(stream.login)}") { launchSingleTop = true }
}

private fun watchChannel(
    playback: dev.teyd.justintv.feature.watch.WatchViewModel,
    chat: dev.teyd.justintv.feature.watch.ChatViewModel,
    navController: androidx.navigation.NavHostController,
    hit: ChannelHit,
) {
    val title =
        when (val presence = hit.presence) {
            is ChannelPresence.Live -> presence.title
            ChannelPresence.Offline -> ""
        }
    val viewers =
        when (val presence = hit.presence) {
            is ChannelPresence.Live -> presence.viewerCount
            ChannelPresence.Offline -> null
        }
    playback.open(
        channel = hit.login,
        displayName = hit.displayName,
        title = title,
        viewers = viewers,
    )
    chat.open(hit.login)
    navController.navigate("$ROUTE_WATCH/${Uri.encode(hit.login)}") { launchSingleTop = true }
}
