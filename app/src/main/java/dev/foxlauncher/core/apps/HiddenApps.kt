package dev.foxlauncher.core.apps

import dev.foxlauncher.core.model.AppEntry

/**
 * Drops apps whose [AppEntry.key] is in [hidden], except when [query] is exactly a hidden app's
 * label (ignoring case, accents and surrounding space), so a hidden app can still be found by
 * typing its full name.
 */
fun applyHidden(list: List<AppEntry>, hidden: Set<String>, query: String): List<AppEntry> {
    if (hidden.isEmpty()) return list
    val exact = AppSearch.normalize(query.trim())
    return list.filter { app ->
        app.key !in hidden || (exact.isNotEmpty() && AppSearch.normalize(app.label.trim()) == exact)
    }
}
