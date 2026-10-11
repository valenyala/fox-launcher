package dev.foxlauncher.feature.home

import dev.foxlauncher.core.data.db.HomeItemEntity
import dev.foxlauncher.core.data.db.HomeItemType

/** A package in one profile: what `LauncherApps.pinShortcuts` takes its id set for. */
data class ShortcutOwner(val packageName: String, val userSerial: Long)

/**
 * The shortcut ids on the home screen (pages, dock and folders) per owner: exactly what Fox must
 * keep pinned (task 2.6). Shortcut rows store `<package>/` as their component.
 */
fun pinnedShortcutIds(rows: List<HomeItemEntity>): Map<ShortcutOwner, Set<String>> =
    rows.filter { it.type == HomeItemType.SHORTCUT && it.component != null && it.userSerial != null && it.shortcutId != null }
        .groupBy({ ShortcutOwner(it.component!!.substringBefore('/'), it.userSerial!!) }, { it.shortcutId!! })
        .mapValues { (_, ids) -> ids.toSet() }

/**
 * Owners whose pinned set changed between [before] and [after], with their new set. `pinShortcuts`
 * replaces an owner's whole set, so an owner whose last shortcut left gets an empty set: unpinned.
 */
fun pinChanges(before: Map<ShortcutOwner, Set<String>>, after: Map<ShortcutOwner, Set<String>>): Map<ShortcutOwner, Set<String>> =
    (before.keys + after.keys)
        .filter { before[it].orEmpty() != after[it].orEmpty() }
        .associateWith { after[it].orEmpty() }
