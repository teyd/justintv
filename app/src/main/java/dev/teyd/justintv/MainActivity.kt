package dev.teyd.justintv

import android.content.BroadcastReceiver
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.teyd.justintv.core.designsystem.theme.JustintvTheme
import dev.teyd.justintv.core.player.PlaybackGate
import dev.teyd.justintv.ui.JustintvApp
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playbackGate: PlaybackGate

    private var pipReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JustintvTheme {
                JustintvApp()
            }
        }
        val receiver = Pip.receiver(
            onPlay = { playbackGate.holder?.resume() },
            onPause = { playbackGate.holder?.pause() },
        )
        Pip.register(this, receiver)
        pipReceiver = receiver
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipHooks.onLeave?.invoke()
    }

    override fun onDestroy() {
        pipReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }
}
