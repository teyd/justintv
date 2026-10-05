package dev.teyd.justintv.core.model

/**
 * Stream languages Twitch accepts as a directory filter.
 *
 * The codes were checked against Twitch's API; unknown codes make the whole query fail, so
 * the list is closed on purpose. Categories are the data that must never be hardcoded.
 * Languages are a small fixed vocabulary, so a fixed list is appropriate here.
 */
data class StreamLanguage(val code: String, val label: String)

object StreamLanguages {
    val ALL: List<StreamLanguage> = listOf(
        StreamLanguage("EN", "English"),
        StreamLanguage("DE", "Deutsch"),
        StreamLanguage("ES", "Español"),
        StreamLanguage("FR", "Français"),
        StreamLanguage("PT", "Português"),
        StreamLanguage("IT", "Italiano"),
        StreamLanguage("RU", "Русский"),
        StreamLanguage("TR", "Türkçe"),
        StreamLanguage("PL", "Polski"),
        StreamLanguage("NL", "Nederlands"),
        StreamLanguage("SV", "Svenska"),
        StreamLanguage("DA", "Dansk"),
        StreamLanguage("FI", "Suomi"),
        StreamLanguage("NO", "Norsk"),
        StreamLanguage("CS", "Čeština"),
        StreamLanguage("SK", "Slovenčina"),
        StreamLanguage("HU", "Magyar"),
        StreamLanguage("RO", "Română"),
        StreamLanguage("BG", "Български"),
        StreamLanguage("UK", "Українська"),
        StreamLanguage("EL", "Ελληνικά"),
        StreamLanguage("AR", "العربية"),
        StreamLanguage("HI", "हिन्दी"),
        StreamLanguage("JA", "日本語"),
        StreamLanguage("KO", "한국어"),
        StreamLanguage("ZH", "中文"),
        StreamLanguage("ZH_HK", "粵語"),
        StreamLanguage("TH", "ไทย"),
        StreamLanguage("VI", "Tiếng Việt"),
        StreamLanguage("ID", "Bahasa Indonesia"),
        StreamLanguage("MS", "Bahasa Melayu"),
        StreamLanguage("ASL", "American Sign Language"),
        StreamLanguage("OTHER", "Other"),
    )

    private val codes: Set<String> = ALL.mapTo(mutableSetOf()) { it.code }

    fun isValid(code: String): Boolean = code in codes

    /** Keeps only codes Twitch accepts, in a stable order. */
    fun sanitize(requested: Collection<String>): List<String> =
        ALL.map { it.code }.filter { it in requested }

    fun label(code: String?): String? = ALL.firstOrNull { it.code == code }?.label
}
