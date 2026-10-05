package dev.teyd.justintv.feature.watch

import android.content.Context
import coil3.SingletonImageLoader
import dev.teyd.justintv.core.chat.Emote
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Emotes in the order the All grid lists them, which is the order the viewer will scroll. */
internal fun pickerOrder(emotes: List<Emote>): List<Emote> = sectionsOf(emotes).flatMap { it.emotes }

/**
 * Loads the picker's thumbnails ahead of time.
 *
 * The grid is already lazy, and that is the problem during a fast scroll: every cell that
 * appears starts its own request and decode, and cells appear faster than images arrive. A
 * channel with a thousand emotes is only about 2 MB of stills, so it is cheaper to fetch them
 * all quietly than to fetch them while the viewer is flinging. They land in Coil's memory and
 * disk caches, so the cells draw immediately now, and on the next visit too.
 *
 * Runs in display order with a few requests at a time, so it never crowds out the video or
 * chat. Cancelling it, for example by leaving the screen, stops it between images.
 */
internal suspend fun prefetchEmoteThumbnails(
    context: Context,
    emotes: List<Emote>,
) {
    val loader = SingletonImageLoader.get(context)
    val gate = Semaphore(PREFETCH_PARALLELISM)
    coroutineScope {
        for (emote in pickerOrder(emotes)) {
            launch {
                gate.withPermit { loader.execute(thumbnailRequest(context, emote)) }
            }
        }
    }
}

private const val PREFETCH_PARALLELISM = 6

/** Waits out the start of playback and chat history, which need the connection more. */
internal const val PREFETCH_DELAY_MS = 2_500L
