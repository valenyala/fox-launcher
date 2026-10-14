package dev.foxlauncher.feature.widgets.app

/** An app widget item on the home, as the cleanup sees it: whether its provider is still there. */
data class PlacedAppWidget(val itemId: Long, val appWidgetId: Int, val providerFound: Boolean)

/** The home items to remove and the widget ids to free. */
data class CleanupPlan(val itemsToRemove: List<Long>, val idsToDelete: Set<Int>) {
    val isEmpty: Boolean get() = itemsToRemove.isEmpty() && idsToDelete.isEmpty()
}

/** Keeping Fox's widget ids and its home items in step (task 6.5). Pure, so it is unit tested. */
object AppWidgetCleanup {

    /**
     * Given the ids Fox's host holds ([hostIds]), the app widget items on the home ([placed]) and
     * the ids of widgets still being added ([pending]: bind dialog or configuration open, or not
     * yet saved): an item goes when its id is no longer the host's or its provider is gone (the app
     * was uninstalled); an id goes when no item that stays uses it and it is not pending, which also
     * frees the ids of items the user removed.
     */
    fun plan(hostIds: Set<Int>, placed: List<PlacedAppWidget>, pending: Set<Int>): CleanupPlan {
        val (kept, gone) = placed.partition { it.providerFound && it.appWidgetId in hostIds }
        val used = kept.mapTo(HashSet()) { it.appWidgetId }
        return CleanupPlan(itemsToRemove = gone.map { it.itemId }, idsToDelete = hostIds - used - pending)
    }
}
