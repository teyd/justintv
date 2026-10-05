package dev.teyd.justintv.feature.settings

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.teyd.justintv.core.data.ThemeMode

private enum class SettingsPage(val title: String) {
    Hub("Settings"),
    Playback("Playback"),
    AdBlock("Ad blocking"),
    Chat("Chat"),
    Appearance("Appearance"),
    About("About"),
}

/** Settings hub. Each section is its own page so the proxy list does not bury the rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(SettingsPage.Hub) }
    val version = rememberVersionName()

    BackHandler(enabled = page != SettingsPage.Hub) { page = SettingsPage.Hub }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page.title) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (page == SettingsPage.Hub) onBack() else page = SettingsPage.Hub },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (page) {
            SettingsPage.Hub -> SettingsHub(
                onOpen = { page = it },
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = padding,
            )
            SettingsPage.Playback -> PlaybackPage(
                state = state,
                onBackground = viewModel::setBackgroundPlayback,
                onPictureInPicture = viewModel::setPictureInPicture,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = padding,
            )
            SettingsPage.AdBlock -> AdBlockPage(
                state = state,
                onAdBlock = viewModel::setAdBlockEnabled,
                onToggleProxy = viewModel::setProxyEnabled,
                onCheck = viewModel::checkProxies,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = padding,
            )
            SettingsPage.Chat -> ChatPage(
                state = state,
                onRecent = viewModel::setRecentMessages,
                onLimit = viewModel::setRecentMessageLimit,
                onSevenTv = viewModel::setSevenTv,
                onBttv = viewModel::setBttv,
                onFfz = viewModel::setFfz,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = padding,
            )
            SettingsPage.Appearance -> AppearancePage(
                state = state,
                onTheme = viewModel::setThemeMode,
                onDynamic = viewModel::setDynamicColor,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = padding,
            )
            SettingsPage.About -> AboutPage(
                versionName = version,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return androidx.compose.runtime.remember {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        ).versionName ?: "0.1.0"
    }
}

/** Account, then one row per section. Callable with no app dependencies. */
@Composable
private fun SettingsHub(
    onOpen: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            ListItem(
                leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                headlineContent = { Text("Not signed in") },
                supportingContent = {
                    Text("Sign in to see the channels you follow. That is the next piece of work.")
                },
            )
        }
        item { SectionHeader("Preferences") }
        item {
            HubRow(Icons.Filled.PlayArrow, "Playback", "Background play and picture in picture") {
                onOpen(SettingsPage.Playback)
            }
        }
        item {
            HubRow(Icons.Filled.Block, "Ad blocking", "Proxies and whether ads are skipped") {
                onOpen(SettingsPage.AdBlock)
            }
        }
        item {
            HubRow(Icons.AutoMirrored.Filled.Chat, "Chat", "History and emote providers") {
                onOpen(SettingsPage.Chat)
            }
        }
        item {
            HubRow(Icons.Filled.DarkMode, "Appearance", "Light, dark, or follow the system") {
                onOpen(SettingsPage.Appearance)
            }
        }
        item {
            HubRow(Icons.Filled.Info, "About", "Version and what this app is") {
                onOpen(SettingsPage.About)
            }
        }
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
    )
}

@Composable
private fun PlaybackPage(
    state: SettingsUiState,
    onBackground: (Boolean) -> Unit,
    onPictureInPicture: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            ListItem(
                headlineContent = { Text("Picture in picture") },
                supportingContent = {
                    Text("When you leave the app, the video keeps playing in a small window over other apps.")
                },
                trailingContent = {
                    Switch(checked = state.pictureInPicture, onCheckedChange = onPictureInPicture)
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Play in background") },
                supportingContent = {
                    Text("Keep the stream going when you leave the app. Off keeps playback inside the mini player.")
                },
                trailingContent = {
                    Switch(checked = state.backgroundPlayback, onCheckedChange = onBackground)
                },
            )
        }
    }
}

@Composable
private fun AdBlockPage(
    state: SettingsUiState,
    onAdBlock: (Boolean) -> Unit,
    onToggleProxy: (dev.teyd.justintv.core.adfree.ProxyEndpoint, Boolean) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            ListItem(
                headlineContent = { Text("Ad blocking") },
                supportingContent = {
                    Text("Check the stream through m3u8 proxies and skip server-side ads. Off plays the plain direct stream.")
                },
                trailingContent = {
                    Switch(checked = state.adBlockEnabled, onCheckedChange = onAdBlock)
                },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ad-free sources",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.isCheckingProxies) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = onCheck, enabled = !state.isCheckingProxies) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Check again")
                }
            }
        }
        items(state.proxyStatuses, key = { it.proxy.host }) { status ->
            ProxyRow(
                status = status,
                masterEnabled = state.adBlockEnabled,
                onToggle = { onToggleProxy(status.proxy, it) },
            )
        }
    }
}

@Composable
private fun ChatPage(
    state: SettingsUiState,
    onRecent: (Boolean) -> Unit,
    onLimit: (Int) -> Unit,
    onSevenTv: (Boolean) -> Unit,
    onBttv: (Boolean) -> Unit,
    onFfz: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            ListItem(
                headlineContent = { Text("Recent messages") },
                supportingContent = {
                    Text("Fill chat with the last messages from recent-messages.robotty.de when you open a channel.")
                },
                trailingContent = {
                    Switch(checked = state.recentMessages, onCheckedChange = onRecent)
                },
            )
        }
        item {
            MessageLimitRow(
                limit = state.recentMessageLimit,
                enabled = state.recentMessages,
                onSelect = onLimit,
            )
        }
        item {
            ListItem(
                headlineContent = { Text("7TV emotes") },
                trailingContent = { Switch(checked = state.sevenTv, onCheckedChange = onSevenTv) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("BTTV emotes") },
                trailingContent = { Switch(checked = state.bttv, onCheckedChange = onBttv) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("FFZ emotes") },
                trailingContent = { Switch(checked = state.ffz, onCheckedChange = onFfz) },
            )
        }
    }
}

@Composable
private fun AppearancePage(
    state: SettingsUiState,
    onTheme: (ThemeMode) -> Unit,
    onDynamic: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val modes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item { SectionHeader("Theme") }
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        onClick = { onTheme(mode) },
                        selected = state.themeMode == mode,
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.System -> "System"
                                ThemeMode.Light -> "Light"
                                ThemeMode.Dark -> "Dark"
                            },
                        )
                    }
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Dynamic color") },
                supportingContent = {
                    Text("Use the wallpaper palette instead of the purple accent.")
                },
                trailingContent = {
                    Switch(checked = state.dynamicColor, onCheckedChange = onDynamic)
                },
            )
        }
    }
}

@Composable
private fun AboutPage(
    versionName: String,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    Column(
        modifier = modifier.padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("JustinTV", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Version $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "A distraction-free Twitch client. Live streams and, once you sign in, the channels you follow. No recommendations, no clips, no analytics.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Playback never sends an account token through a proxy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProxyRow(
    status: ProxyStatus,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(status.proxy.host) },
        supportingContent = { Text(status.proxy.note.orEmpty()) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (status.online) {
                        true -> "Online"
                        false -> "Offline"
                        null -> "Checking…"
                    },
                    color = when (status.online) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(8.dp))
                Switch(
                    checked = status.enabled,
                    onCheckedChange = onToggle,
                    enabled = masterEnabled,
                )
            }
        },
    )
}

@Composable
private fun MessageLimitRow(
    limit: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Messages to load",
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SettingsViewModel.MESSAGE_LIMIT_OPTIONS.forEachIndexed { index, option ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SettingsViewModel.MESSAGE_LIMIT_OPTIONS.size,
                    ),
                    onClick = { onSelect(option) },
                    selected = option == limit,
                    enabled = enabled,
                ) {
                    Text("$option")
                }
            }
        }
    }
}
