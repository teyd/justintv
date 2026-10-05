package dev.teyd.justintv.feature.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import coil3.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.teyd.justintv.core.chat.Emote
import dev.teyd.justintv.core.chat.EmoteSource

/** What the composer is allowed to do. The draft itself stays in the composable. */
data class ComposerState(
    val visible: Boolean = false,
    val canSend: Boolean = false,
    val needsChatPermission: Boolean = false,
    val approvalCode: String? = null,
    val error: String? = null,
)

/** Inserts an emote name at the cursor, with spaces so it stays a whole word. */
fun insertEmote(text: String, cursor: Int, name: String): Pair<String, Int> {
    val at = cursor.coerceIn(0, text.length)
    val before = text.substring(0, at)
    val after = text.substring(at)
    val prefix = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
    val suffix = if (after.isEmpty() || after.first().isWhitespace()) " " else ""
    val inserted = prefix + name + suffix
    return before + inserted + after to (before.length + inserted.length)
}

fun filterEmotes(emotes: List<Emote>, source: EmoteSource?, query: String): List<Emote> {
    val needle = query.trim()
    return emotes.filter { emote ->
        (source == null || emote.source == source) &&
            (needle.isEmpty() || emote.name.contains(needle, ignoreCase = true))
    }
}

/**
 * The message box under chat.
 *
 * Draft, cursor and the picker stay here. Sending, and asking for the chat permission, go
 * out through callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatComposer(
    state: ComposerState,
    emotes: List<Emote>,
    onSend: (String) -> Unit,
    onAllowChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val focus = androidx.compose.runtime.remember { FocusRequester() }

    Column(modifier = modifier.fillMaxWidth()) {
        state.approvalCode?.let { code ->
            Text(
                text = "Enter $code on Twitch to allow chat.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { pickerOpen = true }) {
                    Icon(Icons.Filled.Mood, contentDescription = "Emotes")
                }
                BasicTextField(
                    value = field,
                    onValueChange = { field = it.copy(text = it.text.take(500)) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .focusRequester(focus),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { submit(field.text, state, onSend, onAllowChat) { field = TextFieldValue() } },
                    ),
                    maxLines = 4,
                    decorationBox = { inner ->
                        if (field.text.isEmpty()) {
                            Text(
                                text = if (state.needsChatPermission) "Allow chat to send" else "Send a message",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                if (state.needsChatPermission && state.approvalCode == null) {
                    TextButton(onClick = onAllowChat) { Text("Allow") }
                } else {
                    IconButton(
                        onClick = { submit(field.text, state, onSend, onAllowChat) { field = TextFieldValue() } },
                        enabled = field.text.isNotBlank() && state.canSend,
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        EmotePicker(
            emotes = emotes,
            onPick = { name ->
                val (next, nextCursor) = insertEmote(field.text, field.selection.end, name)
                val clipped = next.take(500)
                field = TextFieldValue(clipped, TextRange(nextCursor.coerceAtMost(clipped.length)))
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

private fun submit(
    draft: String,
    state: ComposerState,
    onSend: (String) -> Unit,
    onAllowChat: () -> Unit,
    clear: () -> Unit,
) {
    if (state.needsChatPermission) {
        onAllowChat()
        return
    }
    val text = draft.trim()
    if (text.isEmpty() || !state.canSend) return
    onSend(text)
    clear()
}
