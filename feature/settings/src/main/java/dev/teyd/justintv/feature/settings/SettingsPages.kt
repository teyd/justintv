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

/** Account, then one row per section. Callable with no app dependencies. */
@Composable
internal fun SettingsHub(
    account: AuthState,
    onOpen: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout
            .PaddingValues(),
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            ListItem(
                modifier = Modifier.clickable { onOpen(SettingsPage.Account) },
                leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                headlineContent = { Text(accountHeadline(account)) },
                supportingContent = { Text(accountSupporting(account)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
            )
        }
        item { SectionHeader("Preferences") }
        item {
            HubRow(Icons.Filled.PlayArrow, "Playback", "Background play, picture in picture, and screen wake") {
                onOpen(SettingsPage.Playback)
            }
        }
        item {
            HubRow(Icons.Filled.Block, "Ad blocking", "Proxies and whether ads are skipped") {
                onOpen(SettingsPage.AdBlock)
            }
        }
        item {
            HubRow(Icons.AutoMirrored.Filled.Chat, "Chat", "History, names, and emote providers") {
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
internal fun AccountPage(
    state: AuthState,
    onStart: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(contentPadding).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(12.dp),
    ) {
        when (state) {
            AuthState.LoggedOut -> {
                Text("Not signed in", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Sign in to see the channels you follow. Twitch shows a code; you approve it on their site. " +
                        "The password is never typed here.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onStart) { Text("Sign in") }
            }

            is AuthState.Pending -> {
                Text("Enter this code on Twitch", style = MaterialTheme.typography.titleLarge)
                Text(state.userCode, style = MaterialTheme.typography.displaySmall)
                Text("Waiting for approval. This page updates on its own.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(state.verificationUri)))
                }) { Text("Open Twitch") }
            }

            is AuthState.LoggedIn -> {
                Text(state.displayName, style = MaterialTheme.typography.titleLarge)
                Text(state.login, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onLogout) { Text("Log out") }
            }

            is AuthState.Failed -> {
                Text("Could not sign in", style = MaterialTheme.typography.titleLarge)
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onStart) { Text("Try again") }
            }
        }
    }
}

private fun accountHeadline(state: AuthState): String =
    when (state) {
        is AuthState.LoggedIn -> state.displayName
        is AuthState.Pending -> "Waiting for Twitch"
        is AuthState.Failed -> "Sign-in failed"
        AuthState.LoggedOut -> "Not signed in"
    }

private fun accountSupporting(state: AuthState): String =
    when (state) {
        is AuthState.LoggedIn -> "Signed in as ${state.login}"
        is AuthState.Pending -> "Code ${state.userCode}"
        is AuthState.Failed -> state.message
        AuthState.LoggedOut -> "Sign in to see the channels you follow."
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
internal fun PlaybackPage(
    state: SettingsUiState,
    onBackground: (Boolean) -> Unit,
    onPictureInPicture: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
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
        item {
            ListItem(
                headlineContent = { Text("Keep screen awake") },
                supportingContent = {
                    Text("Hold the screen on while you watch full-size. The mini player never holds it.")
                },
                trailingContent = {
                    Switch(checked = state.keepScreenOn, onCheckedChange = onKeepScreenOn)
                },
            )
        }
    }
}

@Composable
internal fun AppearancePage(
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
                modifier =
                    Modifier
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
internal fun AboutPage(
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
            text =
                "A distraction-free Twitch client. Live streams and, once you sign in, the channels you follow. " +
                    "No recommendations, no clips, no analytics.",
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
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
