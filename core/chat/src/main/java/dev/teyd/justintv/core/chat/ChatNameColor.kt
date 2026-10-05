package dev.teyd.justintv.core.chat

/**
 * Name colours for chat.
 *
 * Twitch sends a `#RRGGBB` colour when the viewer set one. When the tag is empty the web client
 * still paints names in distinct colours by hashing the nick into a fixed palette. Matching that
 * behaviour is what makes a dense chat readable.
 */
object ChatNameColor {

    /** The same fifteen colours Twitch's web client uses for anonymous name colouring. */
    val DEFAULT_PALETTE: List<String> = listOf(
        "#FF0000", "#0000FF", "#00FF00", "#B22222", "#FF7F50",
        "#9ACD32", "#FF4500", "#2E8B57", "#DAA520", "#D2691E",
        "#5F9EA0", "#1E90FF", "#FF69B4", "#8A2BE2", "#00FF7F",
    )

    /**
     * @param tagged the `color` IRC tag, which may be blank
     * @param name the display name used when hashing a fallback
     */
    fun resolve(tagged: String?, name: String): String {
        val trimmed = tagged?.trim().orEmpty()
        if (isHexColor(trimmed)) return trimmed.uppercase()
        return DEFAULT_PALETTE[stableIndex(name)]
    }

    fun isHexColor(value: String): Boolean =
        value.length == 7 && value[0] == '#' && value.substring(1).all { it in "0123456789ABCDEFabcdef" }

    /** Case-insensitive hash so `Caedrel` and `caedrel` land on the same colour. */
    fun stableIndex(name: String, paletteSize: Int = DEFAULT_PALETTE.size): Int {
        if (paletteSize <= 0) return 0
        var hash = 0
        for (char in name.lowercase()) {
            hash = (hash * 31 + char.code) and 0x7FFF_FFFF
        }
        return hash % paletteSize
    }
}
