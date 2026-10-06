package dev.teyd.justintv.feature.settings

import android.content.Intent
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
import androidx.compose.material3.Button
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
import dev.teyd.justintv.core.network.AuthState

@Composable
internal fun AdBlockPage(
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
                modifier =
                    Modifier
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
                    text =
                        when (status.online) {
                            true -> "Online"
                            false -> "Offline"
                            null -> "Checking…"
                        },
                    color =
                        when (status.online) {
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
