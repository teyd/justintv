package dev.teyd.justintv.feature.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import dev.teyd.justintv.core.player.VideoPlayer

/**
 * Watch one channel.
 *
 * Deliberately plain: the video, the channel name, and one honest line about how the stream
 * is being served. Chat joins this screen in M2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    onBack: () -> Unit,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.playerHolder.exoPlayer.pause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (state.method.isNotEmpty()) {
            viewModel.playerHolder.exoPlayer.play()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.channelLogin) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                VideoPlayer(
                    player = viewModel.playerHolder,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.isLoading) {
                    CircularProgressIndicator()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.method.isNotEmpty()) {
                    Text(text = state.method, style = MaterialTheme.typography.titleMedium)
                }
                if (state.status.isNotEmpty()) {
                    Text(
                        text = state.status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.adBreakDetected) {
                    Text(
                        text = "Ad break detected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.error != null) {
                    Button(onClick = { viewModel.playAnotherSource() }) {
                        Text("Retry")
                    }
                }
                if (state.method.isNotEmpty() && state.error == null) {
                    Button(onClick = { viewModel.playAnotherSource() }) {
                        Text("Play another source")
                    }
                }
            }
        }
    }
}
