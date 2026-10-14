package dev.foxlauncher.feature.widgets.app

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class AppWidgetCleanupTest {

    private fun placed(itemId: Long, appWidgetId: Int, providerFound: Boolean = true) = PlacedAppWidget(itemId, appWidgetId, providerFound)

    @Test
    fun aHomeInStepNeedsNothing() {
        val plan = AppWidgetCleanup.plan(hostIds = setOf(7, 8), placed = listOf(placed(1, 7), placed(2, 8)), pending = emptySet())

        assertTrue(plan.isEmpty)
    }

    @Test
    fun removingAWidgetFromTheHomeFreesItsId() {
        val plan = AppWidgetCleanup.plan(hostIds = setOf(7, 8), placed = listOf(placed(1, 7)), pending = emptySet())

        assertEquals(CleanupPlan(itemsToRemove = emptyList(), idsToDelete = setOf(8)), plan)
    }

    @Test
    fun aWidgetWhoseAppWasUninstalledLeavesTheHome() {
        // Android usually deletes the id itself; when it has not yet, Fox frees it too.
        val plan = AppWidgetCleanup.plan(hostIds = setOf(7, 8), placed = listOf(placed(1, 7), placed(2, 8, providerFound = false)), pending = emptySet())

        assertEquals(CleanupPlan(itemsToRemove = listOf(2), idsToDelete = setOf(8)), plan)
    }

    @Test
    fun anItemWhoseIdTheHostNoLongerHoldsLeavesTheHome() {
        val plan = AppWidgetCleanup.plan(hostIds = setOf(7), placed = listOf(placed(1, 7), placed(2, 9)), pending = emptySet())

        assertEquals(CleanupPlan(itemsToRemove = listOf(2), idsToDelete = emptySet()), plan)
    }

    @Test
    fun aWidgetBeingAddedKeepsItsId() {
        val plan = AppWidgetCleanup.plan(hostIds = setOf(7, 8), placed = listOf(placed(1, 7)), pending = setOf(8))

        assertTrue(plan.isEmpty)
    }

    @Test
    fun idsLeftOverFromEarlierAreFreed() {
        // A bind flow cut short by a crash, say: ids nothing on the home uses.
        val plan = AppWidgetCleanup.plan(hostIds = setOf(3, 4, 7), placed = listOf(placed(1, 7)), pending = emptySet())

        assertEquals(setOf(3, 4), plan.idsToDelete)
    }
}
