package dev.teyd.justintv.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.teyd.justintv.feature.streams.StreamsScreen

private const val ROUTE_STREAMS = "streams"

@Composable
fun JustintvApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ROUTE_STREAMS,
    ) {
        composable(ROUTE_STREAMS) {
            StreamsScreen()
        }
    }
}
