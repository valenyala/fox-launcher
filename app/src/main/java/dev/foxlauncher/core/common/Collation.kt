package dev.foxlauncher.core.common

import java.text.Collator
import java.util.Locale

/**
 * Label ordering for every user-visible list (drawer, search, folder contents).
 *
 * Pure JVM on purpose (per the "pure logic has no Android imports" rule): [Collator] gets accents
 * and locale-specific alphabets right, which a naive `String.compareTo` does not.
 */
object Collation {

    /**
     * Orders labels the way a person reads them: case- and accent-insensitive first, then a stable
     * tie-break on the raw string so that two labels differing only in case never compare equal and
     * sorting stays deterministic.
     */
    fun labelComparator(locale: Locale = Locale.getDefault()): Comparator<String> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        return Comparator { a, b ->
            val primary = collator.compare(a, b)
            if (primary != 0) primary else a.compareTo(b)
        }
    }

    /** One-shot comparison; prefer [labelComparator] when sorting a whole list. */
    fun compareLabels(a: String, b: String, locale: Locale = Locale.getDefault()): Int =
        labelComparator(locale).compare(a, b)
}
