package dev.teyd.justintv.feature.watch

import android.content.Context
import coil3.SingletonImageLoader
import dev.teyd.justintv.core.chat.Emote
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Emotes in the order the grid lists them, which is the order the viewer will scroll. */
internal fun pickerOrder(emotes: List<Emote>): List<Emote> = sectionsOf(emotes).flatMap { it.emotes }

/**
 * The next slice to decode, in list order.
 *
 * A fling jumps [lastVisible] far past what was already queued. Decoding that gap fights the
 * scroll and fills the memory cache with images that have already gone by. Skip to the new
 * viewport and only warm the short run ahead of it.
 */
internal fun prefetchRange(
    queued: Int,
    lastVisible: Int,
    total: Int,
    ahead: Int = PREFETCH_AHEAD,
): IntRange {
    if (total <= 0 || ahead <= 0) return IntRange.EMPTY
    val windowEnd = (lastVisible + ahead).coerceAtMost(total)
    val start = if (lastVisible > queued + ahead) lastVisible else queued
    return if (windowEnd <= start) IntRange.EMPTY else start until windowEnd
}

/**
 * Decodes [emotes] into Coil's memory cache, a few at a time.
 *
 * Callers pass a short window, not the catalog. A thousand 108-pixel bitmaps is tens of
 * megabytes, and filling that while the grid is flinging is what hitching to the bottom was.
 */
internal suspend fun prefetchEmoteThumbnails(
    context: Context,
    emotes: List<Emote>,
) {
    if (emotes.isEmpty()) return
    val loader = SingletonImageLoader.get(context)
    val gate = Semaphore(PREFETCH_PARALLELISM)
    coroutineScope {
        for (emote in emotes) {
            launch {
                gate.withPermit { loader.execute(thumbnailRequest(context, emote)) }
            }
        }
    }
}

/** About three rows. Glide's starting point for a preloader, not the rest of the catalog. */
internal const val PREFETCH_AHEAD = 24

private const val PREFETCH_PARALLELISM = 4
