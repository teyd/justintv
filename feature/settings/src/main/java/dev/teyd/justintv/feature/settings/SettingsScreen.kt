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

internal enum class SettingsPage(
    val title: String,
) {
    Hub("Settings"),
    Account("Account"),
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
    val accountViewModel: AccountViewModel = hiltViewModel()
    val account by accountViewModel.state.collectAsStateWithLifecycle()
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
            SettingsPage.Hub -> {
                SettingsHub(
                    account = account,
                    onOpen = { page = it },
                    modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                    contentPadding = padding,
                )
            }

            SettingsPage.Account -> {
                AccountPage(
                    state = account,
                    onStart = accountViewModel::start,
                    onLogout = accountViewModel::logout,
                    modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                    contentPadding = padding,
                )
            }

            SettingsPage.Playback -> {
                PlaybackPage(
                    state = state,
                    onBackground = viewModel::setBackgroundPlayback,
                    onPictureInPicture = viewModel::setPictureInPicture,
                    onKeepScreenOn = viewModel::setKeepScreenOn,
                    modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                    contentPadding = padding,
                )
            }

            SettingsPage.AdBlock -> {
                AdBlockPage(
                    state = state,
                    onAdBlock = viewModel::setAdBlockEnabled,
                    onToggleProxy = viewModel::setProxyEnabled,
                    onCheck = viewModel::checkProxies,
                    modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                    contentPadding = padding,
                )
            }

            SettingsPage.Chat -> {
                ChatPage(
                    state = state,
                    onRecent = viewModel::setRecentMessages,
                    onLimit = viewModel::setRecentMessageLimit,
                    onSevenTv = viewModel::setSevenTv,
                    onBttv = viewModel::setBttv,
                    onFfz = viewModel::setFfz,
                    onShowInput = viewModel::setShowChatInput,
                    onColoredUsernames = viewModel::setColoredUsernames,
                    onChatTextSize = viewModel::setChatTextSize,
                    onShowTimestamps = viewModel::setShowTimestamps,
                    onTimeFormat = viewModel::setTimeFormat,
                    onBadge = viewModel::setBadgeSource,
                    modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                    contentPadding = padding,
                )
            }

            SettingsPage.Appearance -> {
                AppearancePage(
                    state = state,
                    onTheme = viewModel::setThemeMode,
                    onDynamic = viewModel::setDynamicColor,
                    modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                    contentPadding = padding,
                )
            }

            SettingsPage.About -> {
                AboutPage(
                    versionName = version,
                    modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                    contentPadding = padding,
                )
            }
        }
    }
}

@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return androidx.compose.runtime.remember {
        context.packageManager
            .getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            ).versionName ?: "0.1.0"
    }
}
