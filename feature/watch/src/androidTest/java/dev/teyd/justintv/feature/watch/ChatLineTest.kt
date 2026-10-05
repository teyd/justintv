package dev.teyd.justintv.feature.watch

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.teyd.justintv.core.model.ChatMessage
import dev.teyd.justintv.core.model.ChatSegment
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatLineTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingVisibleLinkOpensOnlyThatDestination() {
        val opened = mutableListOf<String>()
        val message =
            ChatMessage(
                id = "message",
                user = "alice",
                color = "#123456",
                segments = listOf(ChatSegment.Text("visit twitch.tv/channel now")),
            )
        compose.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides
                    object : UriHandler {
                        override fun openUri(uri: String) {
                            opened += uri
                        }
                    },
            ) {
                MaterialTheme { ChatLine(message) }
            }
        }
        val displayed = "alice: visit twitch.tv/channel now"
        val node = compose.onNodeWithText(displayed).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val linkBounds = layout.getBoundingBox(displayed.indexOf("twitch.tv") + 2)
        node.performTouchInput { click(linkBounds.center) }
        compose.runOnIdle { assertEquals(listOf("https://twitch.tv/channel"), opened) }

        val plainBounds = layout.getBoundingBox(displayed.indexOf("visit"))
        node.performTouchInput { click(plainBounds.center) }
        compose.runOnIdle { assertEquals(listOf("https://twitch.tv/channel"), opened) }
    }
}
