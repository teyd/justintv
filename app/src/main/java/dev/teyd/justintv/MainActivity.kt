package dev.teyd.justintv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.teyd.justintv.core.designsystem.theme.JustintvTheme
import dev.teyd.justintv.ui.JustintvApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JustintvTheme {
                JustintvApp()
            }
        }
    }
}
