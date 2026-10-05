package dev.teyd.justintv.feature.streams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * M1 entry point: type a channel name and watch it.
 *
 * The proxy list doubles as a diagnostic: when every proxy is offline the app still plays,
 * it just plays directly, and this screen is where that is visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamsScreen(
    onWatch: (String) -> Unit,
    viewModel: StreamsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var channel by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("JustinTV") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it.trim().removePrefix("#") },
                    label = { Text("Channel") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { if (channel.isNotBlank()) onWatch(channel) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onWatch(channel) },
                    enabled = channel.isNotBlank(),
                ) {
                    Text("Watch")
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ad-free proxies",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.isCheckingProxies) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = { viewModel.checkProxies() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Check proxies")
                }
            }

            LazyColumn {
                items(state.proxyStatuses, key = { it.proxy.host }) { status ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = status.proxy.host,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(status.proxy.note.orEmpty())
                        },
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
}
