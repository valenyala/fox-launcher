package dev.foxlauncher.core.apps

import dev.foxlauncher.core.model.AppEntry

/**
 * Orders apps by label with [labelComparator] (normally `Collation.labelComparator()`), then by
 * component and user serial so that two apps with the same label keep a deterministic order —
 * the same app installed in the personal and the work profile, for instance.
 */
fun sortEntries(list: List<AppEntry>, labelComparator: Comparator<String>): List<AppEntry> =
    list.sortedWith(
        compareBy(labelComparator, AppEntry::label)
            .thenBy(AppEntry::component)
            .thenBy(AppEntry::userSerial),
    )
