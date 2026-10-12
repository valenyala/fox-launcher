package dev.foxlauncher.feature.home

import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomePosition

/** The apps a fresh install puts in the dock (task 2.7), in dock order. */
enum class DefaultApp { DIALER, SMS, BROWSER, CAMERA }

/**
 * Finds the app that plays a [DefaultApp] role on this device, as the flattened component of its
 * launcher activity. Null when nothing handles it, or when several apps do and none is the default.
 */
fun interface DefaultAppResolver {
    fun launcherComponentFor(app: DefaultApp): String?
}

/**
 * The first-run home: each [DefaultApp] the resolver finds in the personal profile's catalog goes
 * into the dock, in [DefaultApp] order; pages stay empty. An odd dock wider than the app count
 * keeps its middle slot free, above the "Search apps" pill, with the apps split evenly around it
 * (a 5-slot dock: 0, 1, 3, 4). An app that resolves to nothing leaves no gap: the rest move up.
 */
object DefaultLayoutBuilder {

    fun build(resolver: DefaultAppResolver, apps: List<AppEntry>, dockSlots: Int): List<Pair<AppEntry, HomePosition>> {
        val personal = apps.filter { !it.isWorkProfile }
        val chosen = DefaultApp.entries
            .mapNotNull { role -> resolver.launcherComponentFor(role)?.let { personal.entryFor(it) } }
            .distinctBy { it.key }
        return chosen.zip(dockSlots(dockSlots)) { entry, slot -> entry to HomePosition(HomeContainer.DOCK, 0, slot, 0) }
    }

    /** The slots the default apps fill, left to right. */
    internal fun dockSlots(dockSlots: Int): List<Int> {
        val count = DefaultApp.entries.size
        if (dockSlots % 2 == 0 || dockSlots <= count) return (0 until minOf(dockSlots, count)).toList()
        val middle = dockSlots / 2
        val right = count / 2
        return (middle - (count - right) until middle) + (middle + 1..middle + right)
    }

    /**
     * The catalog entry for [component]. The launcher activity `PackageManager` names can be an alias
     * the catalog lists differently, so any entry of the same package will do when none matches.
     */
    private fun List<AppEntry>.entryFor(component: String): AppEntry? =
        firstOrNull { it.component == component }
            ?: component.substringBefore('/').let { pkg -> firstOrNull { it.packageName == pkg } }
}
