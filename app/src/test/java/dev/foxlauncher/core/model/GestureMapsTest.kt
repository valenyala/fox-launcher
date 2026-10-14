package dev.foxlauncher.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GestureMapsTest {

    private val base = mapOf(GestureType.DOUBLE_TAP to GestureAction.LockScreen, GestureType.SWIPE_UP to GestureAction.OpenDrawer)
    private val pages = mapOf(1 to mapOf(GestureType.DOUBLE_TAP to GestureAction.NextWallpaper))

    @Test
    fun aPageWithItsOwnActionUsesIt() {
        assertEquals(GestureAction.NextWallpaper, GestureMaps.actionFor(GestureType.DOUBLE_TAP, 1, base, pages))
    }

    @Test
    fun otherwiseAllPagesApply() {
        assertEquals(GestureAction.LockScreen, GestureMaps.actionFor(GestureType.DOUBLE_TAP, 0, base, pages))
        assertEquals(GestureAction.OpenDrawer, GestureMaps.actionFor(GestureType.SWIPE_UP, 1, base, pages), "page 1 has nothing of its own for it")
        assertEquals(GestureAction.None, GestureMaps.actionFor(GestureType.PINCH_IN, 1, base, pages))
    }

    @Test
    fun settingAndClearingAPageAction() {
        val set = GestureMaps.withPageAction(pages, 2, GestureType.SWIPE_UP, GestureAction.Recents)
        assertEquals(GestureAction.Recents, set[2]?.get(GestureType.SWIPE_UP))

        val cleared = GestureMaps.withPageAction(set, 1, GestureType.DOUBLE_TAP, null)
        assertTrue(1 !in cleared, "a page left with nothing of its own goes")
        assertEquals(set[2], cleared[2])
    }
}
