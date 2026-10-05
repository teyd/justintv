package dev.teyd.justintv.feature.streams

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.core.model.Game
import kotlinx.coroutines.launch

enum class HomeTab(val title: String) {
    Following("Following"),
    Live("Live"),
    Categories("Categories"),
}

/**
 * The tabs the front page shows, in order.
 *
 * Following only exists for a logged-in viewer. Showing it to everyone else would be a tab
 * that can only say "log in", so it is left out instead.
 */
fun homeTabs(isLoggedIn: Boolean): List<HomeTab> =
    if (isLoggedIn) {
        listOf(HomeTab.Following, HomeTab.Live, HomeTab.Categories)
    } else {
        listOf(HomeTab.Live, HomeTab.Categories)
    }

/**
 * The front page.
 *
 * Swipe between tabs or tap them: what is live now, categories to browse, and, when logged
 * in, the channels you follow. The language filter applies to the live and category lists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onWatch: (String) -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = remember(state.isLoggedIn) { homeTabs(state.isLoggedIn) }
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(HomeTab.Live).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()

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
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex)) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (tabs.getOrNull(page)) {
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

                    null -> Unit
                }
            }
        }
    }
}

/** Placeholder until following lands with login: only reachable when logged in. */
@Composable
private fun FollowingTab() {
    CenteredMessage(
        message = "Channels you follow that are live will appear here.",
        actionLabel = null,
        onAction = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesTab(
    state: LoadState<Game>,
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
                contentPadding = PaddingValues(8.dp),
            ) {
                items(state.items, key = { it.id }) { game ->
                    GameCard(game = game, onClick = { onOpenGame(game.name) })
                }
            }
        }
    }
}
