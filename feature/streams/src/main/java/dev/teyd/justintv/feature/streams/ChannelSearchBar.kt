package dev.teyd.justintv.feature.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.teyd.justintv.core.model.ChannelHit
import dev.teyd.justintv.core.model.formatViewers

/**
 * The field in the top bar. Focus moves here when it enters composition, which is when
 * search is opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focus),
        placeholder = { Text("Search channels") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
    )
}

/**
 * Autocomplete under the field.
 *
 * The first row is always the channel currently typed. Live rows show viewers and the
 * stream title; offline rows show [ChannelSearch.OFFLINE_LABEL] in that slot.
 */
@Composable
fun ChannelSearchResults(
    query: String,
    hits: List<ChannelHit>,
    isSearching: Boolean,
    error: String?,
    onOpen: (ChannelHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = ChannelSearch.rows(query, hits)
    LazyColumn(modifier = modifier) {
        if (isSearching) {
            item(key = "searching") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        items(rows, key = { it.login.lowercase() }) { hit ->
            ChannelSearchRow(hit = hit, onClick = { onOpen(hit) })
        }
        if (error != null) {
            item(key = "error") {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelSearchRow(
    hit: ChannelHit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewers = ChannelSearch.viewers(hit)
    ListItem(
        headlineContent = {
            Text(
                text = hit.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = ChannelSearch.subtitle(hit),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { ChannelIcon(url = hit.avatarUrl) },
        trailingContent =
            viewers?.let { count ->
                {
                    Text(
                        text = formatViewers(count),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ChannelIcon(url: String?) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
