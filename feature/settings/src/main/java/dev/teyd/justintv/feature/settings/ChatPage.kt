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
import dev.teyd.justintv.core.data.ChatTextSize
import dev.teyd.justintv.core.data.ChatTimeFormat
import dev.teyd.justintv.core.data.ThemeMode
import dev.teyd.justintv.core.model.ChatBadgeSource
import dev.teyd.justintv.core.network.AuthState

@Composable
internal fun ChatPage(
    state: SettingsUiState,
    onRecent: (Boolean) -> Unit,
    onLimit: (Int) -> Unit,
    onSevenTv: (Boolean) -> Unit,
    onBttv: (Boolean) -> Unit,
    onFfz: (Boolean) -> Unit,
    onShowInput: (Boolean) -> Unit,
    onColoredUsernames: (Boolean) -> Unit,
    onChatTextSize: (ChatTextSize) -> Unit,
    onShowTimestamps: (Boolean) -> Unit,
    onTimeFormat: (ChatTimeFormat) -> Unit,
    onBadge: (ChatBadgeSource, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item { SectionHeader("Messages") }
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
                headlineContent = { Text("Timestamps") },
                supportingContent = { Text("Show the time each message was sent, in your local time.") },
                trailingContent = {
                    Switch(checked = state.showTimestamps, onCheckedChange = onShowTimestamps)
                },
            )
        }
        item {
            TimeFormatRow(
                format = state.timeFormat,
                enabled = state.showTimestamps,
                onSelect = onTimeFormat,
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Chat input") },
                supportingContent = {
                    Text("Show the message box while you're signed in. Sending needs a separate chat permission.")
                },
                trailingContent = {
                    Switch(checked = state.showChatInput, onCheckedChange = onShowInput)
                },
            )
        }
        item { SectionHeader("Names") }
        item {
            ListItem(
                headlineContent = { Text("Colored usernames") },
                supportingContent = {
                    Text("Draw each name in its Twitch color. Off keeps names bold in the default text color.")
                },
                trailingContent = {
                    Switch(checked = state.coloredUsernames, onCheckedChange = onColoredUsernames)
                },
            )
        }
        item {
            ChatTextSizeRow(
                size = state.chatTextSize,
                onSelect = onChatTextSize,
            )
        }
        item { SectionHeader("Emotes") }
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
        item { SectionHeader("Badges") }
        items(BADGE_ROWS) { (source, label) ->
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = {
                    Switch(checked = source in state.badges, onCheckedChange = { onBadge(source, it) })
                },
            )
        }
    }
}

/** Independently configurable badge sources; none requires signing in. */
private val BADGE_ROWS =
    listOf(
        ChatBadgeSource.Twitch to "Twitch badges",
        ChatBadgeSource.Chatterino to "Chatterino badges",
        ChatBadgeSource.SevenTv to "7TV badges",
        ChatBadgeSource.Ffz to "FFZ badges",
        ChatBadgeSource.Bttv to "BTTV badges",
    )

@Composable
private fun TimeFormatRow(
    format: ChatTimeFormat,
    enabled: Boolean,
    onSelect: (ChatTimeFormat) -> Unit,
) {
    val options = ChatTimeFormat.entries
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Time format",
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { onSelect(option) },
                    selected = option == format,
                    enabled = enabled,
                ) {
                    Text(
                        when (option) {
                            ChatTimeFormat.System -> "System"
                            ChatTimeFormat.Hour12 -> "12-hour"
                            ChatTimeFormat.Hour24 -> "24-hour"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatTextSizeRow(
    size: ChatTextSize,
    onSelect: (ChatTextSize) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = "Text size", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChatTextSize.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    shape =
                        SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ChatTextSize.entries.size,
                        ),
                    onClick = { onSelect(option) },
                    selected = option == size,
                ) {
                    Text(
                        when (option) {
                            ChatTextSize.Small -> "Small"
                            ChatTextSize.Default -> "Default"
                            ChatTextSize.Large -> "Large"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageLimitRow(
    limit: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
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
                    shape =
                        SegmentedButtonDefaults.itemShape(
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
