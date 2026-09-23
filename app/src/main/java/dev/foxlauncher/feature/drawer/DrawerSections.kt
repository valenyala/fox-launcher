package dev.foxlauncher.feature.drawer

import androidx.compose.runtime.Immutable
import dev.foxlauncher.core.apps.AppSearch
import dev.foxlauncher.core.model.AppEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** A run of drawer apps under one letter header. [letter] is empty for search results, which have no header. */
@Immutable
data class DrawerSection(val letter: String, val apps: ImmutableList<AppEntry>)

/** The drawer's profile tabs, shown only when a work profile exists. */
enum class DrawerTab { PERSONAL, WORK }

/**
 * The apps a drawer tab lists. A search always covers every profile (work results carry the
 * badge), so tabs only filter the unsearched list.
 */
fun appsForTab(apps: List<AppEntry>, tab: DrawerTab, query: String): List<AppEntry> = when {
    query.isNotBlank() -> apps
    tab == DrawerTab.WORK -> apps.filter { it.isWorkProfile }
    else -> apps.filterNot { it.isWorkProfile }
}

/** Header used for labels that do not start with a letter (digits, symbols, emoji). */
const val OTHER_SECTION: String = "#"

/**
 * Groups [apps] (already label-sorted) for the drawer. With a blank [query] every app is listed
 * under its first letter; otherwise only matches are listed, in one headerless section, best
 * [AppSearch.Rank] first and label order within a rank.
 */
fun buildSections(apps: List<AppEntry>, query: String): ImmutableList<DrawerSection> {
    if (query.isNotBlank()) {
        val matches = apps
            .mapNotNull { app -> AppSearch.rank(query, app.label)?.let { app to it } }
            .sortedBy { (_, rank) -> rank.ordinal }
            .map { (app, _) -> app }
        return if (matches.isEmpty()) persistentListOf() else persistentListOf(DrawerSection("", matches.toImmutableList()))
    }
    val sections = mutableListOf<DrawerSection>()
    var letter: String? = null
    var run = mutableListOf<AppEntry>()
    for (app in apps) {
        val next = sectionLetter(app.label)
        if (next != letter) {
            letter?.let { sections += DrawerSection(it, run.toImmutableList()) }
            letter = next
            run = mutableListOf()
        }
        run += app
    }
    letter?.let { sections += DrawerSection(it, run.toImmutableList()) }
    return sections.toImmutableList()
}

/** The header an app is listed under: its first letter without accents, upper-cased, or [OTHER_SECTION]. */
fun sectionLetter(label: String): String {
    val first = AppSearch.normalize(label.trimStart()).firstOrNull() ?: return OTHER_SECTION
    return if (first.isLetter()) first.uppercase() else OTHER_SECTION
}
