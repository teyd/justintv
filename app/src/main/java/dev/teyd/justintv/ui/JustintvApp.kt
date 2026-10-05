package dev.teyd.justintv.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.teyd.justintv.feature.streams.StreamsScreen
import dev.teyd.justintv.feature.watch.WATCH_ARG_LOGIN
import dev.teyd.justintv.feature.watch.WatchScreen

private const val ROUTE_STREAMS = "streams"
private const val ROUTE_WATCH = "watch"
private const val ARG_LOGIN = WATCH_ARG_LOGIN

@Composable
fun JustintvApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ROUTE_STREAMS,
    ) {
        composable(ROUTE_STREAMS) {
            StreamsScreen(
                onWatch = { login ->
                    navController.navigate("$ROUTE_WATCH/$login")
                },
            )
        }
        composable(
            route = "$ROUTE_WATCH/{$ARG_LOGIN}",
            arguments = listOf(navArgument(ARG_LOGIN) { type = NavType.StringType }),
        ) {
            WatchScreen(onBack = { navController.popBackStack() })
        }
    }
}
