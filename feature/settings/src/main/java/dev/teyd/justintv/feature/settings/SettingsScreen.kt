package dev.teyd.justintv.feature.settings

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Playback, ad-blocking and chat preferences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
            contentPadding = padding,
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Picture in picture") },
                    supportingContent = {
                        Text("When you leave the app, the video keeps playing in a small window over other apps.")
                    },
                    trailingContent = {
                        Switch(
                            checked = state.pictureInPicture,
                            onCheckedChange = viewModel::setPictureInPicture,
                        )
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
                        Switch(
                            checked = state.backgroundPlayback,
                            onCheckedChange = viewModel::setBackgroundPlayback,
                        )
                    },
                )
            }

            item { SectionHeader("Ad blocking") }
            item {
                ListItem(
                    headlineContent = { Text("Ad blocking") },
                    supportingContent = {
                        Text("Check the stream through m3u8 proxies and skip server-side ads. Off plays the plain direct stream.")
                    },
                    trailingContent = {
                        Switch(
                            checked = state.adBlockEnabled,
                            onCheckedChange = viewModel::setAdBlockEnabled,
                        )
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
                    IconButton(onClick = viewModel::checkProxies, enabled = !state.isCheckingProxies) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Check again")
                    }
                }
            }
            items(state.proxyStatuses, key = { it.proxy.host }) { status ->
                ProxyRow(
                    status = status,
                    masterEnabled = state.adBlockEnabled,
                    onToggle = { viewModel.setProxyEnabled(status.proxy, it) },
                )
            }

            item { SectionHeader("Chat") }
            item {
                ListItem(
                    headlineContent = { Text("Recent messages") },
                    supportingContent = {
                        Text("Fill chat with the last messages from recent-messages.robotty.de when you open a channel.")
                    },
                    trailingContent = {
                        Switch(
                            checked = state.recentMessages,
                            onCheckedChange = viewModel::setRecentMessages,
                        )
                    },
                )
            }
            item {
                MessageLimitRow(
                    limit = state.recentMessageLimit,
                    enabled = state.recentMessages,
                    onSelect = viewModel::setRecentMessageLimit,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("7TV emotes") },
                    trailingContent = {
                        Switch(checked = state.sevenTv, onCheckedChange = viewModel::setSevenTv)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("BTTV emotes") },
                    trailingContent = {
                        Switch(checked = state.bttv, onCheckedChange = viewModel::setBttv)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("FFZ emotes") },
                    trailingContent = {
                        Switch(checked = state.ffz, onCheckedChange = viewModel::setFfz)
                    },
                )
            }
        }
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
