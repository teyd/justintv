package dev.teyd.justintv.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Bare-minimum settings. Today that is the ad-free proxy list and whether each is reachable. */
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
                .padding(padding),
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
                ListItem(
                    headlineContent = { Text(status.proxy.host) },
                    supportingContent = { Text(status.proxy.note.orEmpty()) },
                    trailingContent = {
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
                    },
                )
            }
        }
    }
}
