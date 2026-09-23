package dev.foxlauncher.core.apps

import java.text.Normalizer
import java.util.Locale

/**
 * Drawer search matching. Case- and diacritic-insensitive: "cafe" finds "Café", "MAPS" finds
 * "Maps". Pure JVM so it is unit tested without a device.
 */
object AppSearch {

    /** How well a label matches a query; lower is better. */
    enum class Rank { LABEL_PREFIX, WORD_PREFIX, SUBSTRING }

    /** True when [label] matches [query] at any [Rank]. A blank query matches everything. */
    fun match(query: String, label: String): Boolean =
        query.isBlank() || rank(query, label) != null

    /**
     * The best [Rank] at which [label] matches [query], or null when it does not match. Word
     * prefixes are checked before substrings, so "tube" ranks "YouTube" (substring) below
     * "Tube Map" (label prefix).
     */
    fun rank(query: String, label: String): Rank? {
        val q = normalize(query).trim()
        if (q.isEmpty()) return Rank.LABEL_PREFIX
        val l = normalize(label)
        return when {
            l.startsWith(q) -> Rank.LABEL_PREFIX
            l.split(WORD_SEPARATOR).any { it.startsWith(q) } -> Rank.WORD_PREFIX
            l.contains(q) -> Rank.SUBSTRING
            else -> null
        }
    }

    /** Lowercases and strips combining marks: "Ñandú" → "nandu". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
}
