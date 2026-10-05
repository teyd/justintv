package dev.teyd.justintv.core.chat

/** Where an emote comes from. Declaration order is lookup priority, lowest first. */
enum class EmoteSource { Bttv, Ffz, SevenTv }

/**
 * A third-party emote that is matched by name inside message text.
 *
 * Twitch's own emotes are different: they arrive as character ranges in a message tag and are
 * not looked up by name.
 */
data class Emote(
    val name: String,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val source: EmoteSource,
    /**
     * A single-frame version of [url], for grids and pickers. Animated files can be megabytes
     * each; a static frame is a few kilobytes. Null when only the animated file exists.
     */
    val stillUrl: String? = null,
) {
    val aspectRatio: Float
        get() = if (width != null && height != null && width > 0 && height > 0) {
            (width.toFloat() / height).coerceIn(MIN_ASPECT, MAX_ASPECT)
        } else {
            1f
        }

    private companion object {
        // Keeps one absurd emote from taking over a chat line.
        const val MIN_ASPECT = 0.5f
        const val MAX_ASPECT = 4f
    }
}

/** A name to emote lookup. Later entries win, so callers add global sets before channel sets. */
class EmoteIndex private constructor(private val byName: Map<String, Emote>) {

    operator fun get(name: String): Emote? = byName[name]

    val size: Int get() = byName.size

    fun emotes(): List<Emote> = byName.values.toList()

    class Builder {
        private val map = HashMap<String, Emote>()

        fun addAll(emotes: Collection<Emote>): Builder {
            emotes.forEach { map[it.name] = it }
            return this
        }

        fun build(): EmoteIndex = EmoteIndex(map.toMap())
    }

    companion object {
        val EMPTY = EmoteIndex(emptyMap())
    }
}
