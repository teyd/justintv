package dev.teyd.justintv.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.teyd.justintv.feature.settings.SettingsScreen
import dev.teyd.justintv.feature.streams.GAME_ARG_NAME
import dev.teyd.justintv.feature.streams.GameScreen
import dev.teyd.justintv.feature.streams.HomeScreen
import dev.teyd.justintv.feature.watch.WATCH_ARG_LOGIN
import dev.teyd.justintv.feature.watch.WatchScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_GAME = "game"
private const val ROUTE_WATCH = "watch"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun JustintvApp() {
    val navController = rememberNavController()
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
        ) {
            WatchScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
