package dev.teyd.justintv.feature.watch

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import dev.teyd.justintv.core.chat.Emote
import dev.teyd.justintv.core.chat.EmoteSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Display order. The enum's own order is lookup priority, not what people expect to see. */
internal val PickerSourceOrder =
    listOf(EmoteSource.Twitch, EmoteSource.SevenTv, EmoteSource.Bttv, EmoteSource.Ffz)

/** One provider's emotes, alphabetical. */
internal data class EmoteSection(
    val source: EmoteSource,
    val emotes: List<Emote>,
)

internal fun sectionsOf(emotes: List<Emote>): List<EmoteSection> {
    val bySource = emotes.groupBy { it.source }
    return PickerSourceOrder.mapNotNull { source ->
        bySource[source]?.takeIf { it.isNotEmpty() }?.let { list ->
            EmoteSection(source, list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
        }
    }
}

/** Where each section's header sits in the grid. A section is a header and its emotes. */
internal fun headerIndices(sections: List<EmoteSection>): List<Int> {
    var next = 0
    return sections.map { section -> next.also { next += 1 + section.emotes.size } }
}

/**
 * Which section the grid is showing at the top. Null right at the very top, which is the
 * All tab itself; the first scroll puts the first provider's tab under the highlight.
 */
internal fun sectionAtTop(
    headers: List<Int>,
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
): Int? {
    if (headers.isEmpty() || (firstVisibleIndex == 0 && firstVisibleOffset == 0)) return null
    return headers.indexOfLast { it <= firstVisibleIndex }.coerceAtLeast(0)
}

internal fun EmoteSource.pickerLabel(): String =
    when (this) {
        EmoteSource.Twitch -> "Twitch"
        EmoteSource.SevenTv -> "7TV"
        EmoteSource.Bttv -> "BTTV"
        EmoteSource.Ffz -> "FFZ"
    }

internal fun emoteKey(emote: Emote): String = "${emote.source}:${emote.name}"

/**
 * The emote sheet.
 *
 * One grid, not a pager of grids. A pager composed a second lazy grid and nested a horizontal
 * scroller inside the sheet, which is what made a fling hitch. Tabs jump to a section. The tab
 * row is the only composable that reads the scroll position, so the grid can skip while the
 * highlight moves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmotePicker(
    emotes: List<Emote>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sections = remember(emotes, query) { sectionsOf(filterEmotes(emotes, null, query)) }
    // Tabs come from what exists, not from what the search matches, so they do not jump around.
    val tabs =
        remember(emotes) {
            listOf<EmoteSource?>(null) + PickerSourceOrder.filter { source -> emotes.any { it.source == source } }
        }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val headers = remember(sections) { headerIndices(sections) }
    val context = LocalContext.current

    LaunchedEffect(sections) {
        val ordered = sections.flatMap { it.emotes }
        if (ordered.isEmpty()) return@LaunchedEffect
        var queued = 0
        var warming: Job? = null
        snapshotFlow {
            val last =
                grid.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            (last - sections.size).coerceAtLeast(0)
        }.collect { last ->
            val range = prefetchRange(queued, last, ordered.size)
            if (range.isEmpty()) return@collect
            // A fling left the in-flight window behind. Drop it instead of decoding images
            // that have already gone past.
            if (range.first > queued) warming?.cancel()
            queued = range.last + 1
            val slice = ordered.subList(range.first, range.last + 1)
            warming = launch { prefetchEmoteThumbnails(context, slice) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(LocalConfiguration.current.screenHeightDp.dp * SHEET_HEIGHT_FRACTION)
                    .navigationBarsPadding(),
        ) {
            SearchField(query = query, onQueryChange = { query = it })

            if (emotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Loading emotes…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            SectionTabs(
                tabs = tabs,
                sections = sections,
                headers = headers,
                grid = grid,
                onSelect = { source ->
                    scope.launch {
                        val section = sections.indexOfFirst { it.source == source }
                        when {
                            source == null -> grid.scrollToItem(0)
                            section >= 0 -> grid.scrollToItem(headers[section])
                        }
                    }
                },
            )

            EmoteGrid(
                sections = sections,
                state = grid,
                onPick = onPick,
            )
        }
    }
}

/**
 * Reads the grid's scroll position itself. If the parent read it, every section change would
 * restart the grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionTabs(
    tabs: List<EmoteSource?>,
    sections: List<EmoteSection>,
    headers: List<Int>,
    grid: LazyGridState,
    onSelect: (EmoteSource?) -> Unit,
) {
    val selected by remember(headers, sections, tabs, grid) {
        derivedStateOf {
            val section = sectionAtTop(headers, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
            val source = section?.let { sections.getOrNull(it)?.source }
            tabs.indexOf(source).coerceAtLeast(0)
        }
    }
    PrimaryScrollableTabRow(selectedTabIndex = selected) {
        tabs.forEachIndexed { index, source ->
            Tab(
                selected = index == selected,
                onClick = { onSelect(source) },
                text = { Text(source?.pickerLabel() ?: "All") },
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search emotes",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        }
    }
}

@Composable
private fun EmoteGrid(
    sections: List<EmoteSection>,
    state: LazyGridState,
    onPick: (String) -> Unit,
) {
    if (sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No emotes match",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CELL_SIZE),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sections.forEach { section ->
            stickyHeader(
                key = "header-${section.source}",
                contentType = "header",
            ) {
                Text(
                    text = "${section.source.pickerLabel()} · ${section.emotes.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(
                section.emotes,
                // Names collide across providers. The source keeps the key unique without
                // allocating on every scroll frame: the string is the item key, built once.
                key = { emoteKey(it) },
                contentType = { "emote" },
            ) { emote ->
                EmoteCell(emote = emote, onPick = onPick)
            }
        }
    }
}

/**
 * The request for one grid thumbnail. The prefetcher builds the same one, so the decoded image
 * is already in Coil's memory cache by the time the cell scrolls into view.
 *
 * An animated file can be hundreds of kilobytes. The grid wants the still frame at thumbnail
 * size.
 */
internal fun thumbnailRequest(
    context: Context,
    emote: Emote,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(emote.stillUrl ?: emote.url)
        .size(THUMBNAIL_PX, THUMBNAIL_PX)
        .precision(Precision.INEXACT)
        .build()

@Composable
private fun EmoteCell(
    emote: Emote,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    val request = remember(emote.stillUrl, emote.url) { thumbnailRequest(context, emote) }
    // No ripple and no clip: they cost a layer per cell while scrolling, and picking an emote
    // closes the sheet, which is feedback enough.
    Box(
        modifier =
            Modifier
                .size(CELL_SIZE)
                .clickable(interactionSource = null, indication = null, onClickLabel = emote.name) {
                    onPick(emote.name)
                },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = emote.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(36.dp),
        )
    }
}

private val CELL_SIZE = 52.dp

/** 36dp at 3x, so a thumbnail never decodes at full emote size. */
internal const val THUMBNAIL_PX = 108

private const val SHEET_HEIGHT_FRACTION = 0.62f
