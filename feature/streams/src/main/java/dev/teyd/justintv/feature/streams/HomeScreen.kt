package dev.teyd.justintv.feature.streams

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class HomeTab(val title: String) {
    Following("Following"),
    Live("Live"),
    Categories("Categories"),
}

/**
 * The front page.
 *
 * Three tabs, nothing else: the channels you follow, what is live now, and categories to
 * browse. The language filter applies to the live and category lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onWatch: (String) -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(HomeTab.Live.ordinal) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JustinTV") },
                actions = {
                    LanguageFilterAction(selected = state.languages, onChange = viewModel::setLanguages)
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                HomeTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab.ordinal,
                        onClick = { selectedTab = tab.ordinal },
                        text = { Text(tab.title) },
                    )
                }
            }

            when (HomeTab.entries[selectedTab]) {
                HomeTab.Following -> FollowingTab()

                HomeTab.Live -> StreamList(
                    state = state.live,
                    emptyText = "Nobody is live for this filter",
                    onRefresh = viewModel::refreshLive,
                    onWatch = onWatch,
                )

                HomeTab.Categories -> CategoriesTab(
                    state = state.games,
                    onRefresh = viewModel::refreshGames,
                    onOpenGame = onOpenGame,
                )
            }
        }
    }
}

@Composable
private fun FollowingTab() {
    CenteredMessage(
        message = "Log in with Twitch to see the channels you follow that are live right now.",
        actionLabel = null,
        onAction = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesTab(
    state: LoadState<dev.teyd.justintv.core.model.Game>,
    onRefresh: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading && state.items.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.items.isEmpty() && state.isLoading -> CenteredLoading()

            state.items.isEmpty() ->
                CenteredMessage(
                    message = state.error ?: "No categories found",
                    actionLabel = "Try again",
                    onAction = onRefresh,
                )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            ) {
                items(state.items, key = { it.id }) { game ->
                    GameCard(game = game, onClick = { onOpenGame(game.name) })
                }
            }
        }
    }
}
