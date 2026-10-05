package dev.teyd.justintv.feature.streams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.teyd.justintv.core.model.StreamLanguages

/** What the chip says. Empty means no filter, not "zero languages". */
internal fun languageFilterLabel(selected: Set<String>): String {
    val labels = StreamLanguages.ALL.filter { it.code in selected }.map { it.label }
    return when (labels.size) {
        0 -> "Languages"
        1 -> labels[0]
        else -> "${labels.size} languages"
    }
}

/**
 * Filter chip for the stream list it actually narrows.
 *
 * Not an app-bar action: search and settings apply to the whole page, this does not.
 * Callers place it next to that list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageFilterAction(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    FilterChip(
        selected = selected.isNotEmpty(),
        onClick = { open = true },
        label = { Text(languageFilterLabel(selected)) },
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        },
    )

    if (open) {
        LanguageFilterDialog(
            selected = selected,
            onDismiss = { open = false },
            onApply = {
                onChange(it)
                open = false
            },
        )
    }
}

@Composable
private fun LanguageFilterDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var draft by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Languages") },
        text = {
            Column {
                Text(
                    text = if (draft.isEmpty()) "Showing all languages" else "${draft.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(StreamLanguages.ALL, key = { it.code }) { language ->
                        val checked = language.code in draft
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        draft = if (checked) draft - language.code else draft + language.code
                                    },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(text = language.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { draft = emptySet() }, enabled = draft.isNotEmpty()) {
                    Text("Clear")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
