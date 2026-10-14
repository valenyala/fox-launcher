package dev.foxlauncher.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope

/** Fox's package, which the journeys start and measure. */
const val FOX = "dev.foxlauncher"

/**
 * What a Fox user does most (task 8.1), for the profile and the benchmarks alike: start to the
 * first frame, swipe between pages, open the drawer and scroll it. Gestures go to the middle of
 * the screen, clear of the dock and the status bar.
 */
object Journeys {

    /** Cold start of the home, to its first frame. */
    fun MacrobenchmarkScope.start() {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    fun MacrobenchmarkScope.swipePages() {
        val y = device.displayHeight / 2
        repeat(PAGE_SWIPES) {
            device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, SWIPE_STEPS)
            device.waitForIdle()
            device.swipe(device.displayWidth / 5, y, device.displayWidth * 4 / 5, y, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    fun MacrobenchmarkScope.openDrawer() {
        val x = device.displayWidth / 2
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, SWIPE_STEPS)
        device.waitForIdle()
    }

    /** Flings the open drawer down and back up, then closes it. */
    fun MacrobenchmarkScope.scrollDrawer() {
        val x = device.displayWidth / 2
        repeat(DRAWER_FLINGS) {
            device.swipe(x, device.displayHeight * 4 / 5, x, device.displayHeight / 5, FLING_STEPS)
            device.waitForIdle()
        }
        repeat(DRAWER_FLINGS) {
            device.swipe(x, device.displayHeight / 5, x, device.displayHeight * 4 / 5, FLING_STEPS)
            device.waitForIdle()
        }
        device.pressBack()
        device.waitForIdle()
    }

    private const val PAGE_SWIPES = 2
    private const val DRAWER_FLINGS = 3
    private const val SWIPE_STEPS = 20
    private const val FLING_STEPS = 5
}
