package dev.foxlauncher.feature.gestures

import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.MacroStep
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MacroRunnerTest {

    private fun macro(vararg steps: Pair<GestureAction, Long>) = GestureAction.Macro(steps.map { (action, delay) -> MacroStep(action, delay) })

    @Test
    fun stepsRunInOrderEachAfterItsWait() = runTest {
        val ran = mutableListOf<Pair<GestureAction, Long>>()

        val result = MacroRunner.run(macro(GestureAction.NextWallpaper to 0L, GestureAction.OpenDrawer to 1_000L, GestureAction.OpenSearch to 500L)) {
            ran += it to currentTime
            ActionResult.DONE
        }

        assertEquals(ActionResult.DONE, result)
        assertEquals(listOf(GestureAction.NextWallpaper to 0L, GestureAction.OpenDrawer to 1_000L, GestureAction.OpenSearch to 1_500L), ran)
    }

    @Test
    fun aStepThatNeedsTheUserStopsTheMacro() = runTest {
        val ran = mutableListOf<GestureAction>()

        val result = MacroRunner.run(macro(GestureAction.OpenDrawer to 0L, GestureAction.LockScreen to 0L, GestureAction.NextWallpaper to 0L)) {
            ran += it
            if (it == GestureAction.LockScreen) ActionResult.NEEDS_ACCESSIBILITY else ActionResult.DONE
        }

        assertEquals(ActionResult.NEEDS_ACCESSIBILITY, result)
        assertEquals(listOf(GestureAction.OpenDrawer, GestureAction.LockScreen), ran)
    }

    @Test
    fun aFailedStepIsSkipped() = runTest {
        val ran = mutableListOf<GestureAction>()

        val result = MacroRunner.run(macro(GestureAction.OpenApp("gone/.A", 0) to 0L, GestureAction.OpenDrawer to 0L)) {
            ran += it
            if (it is GestureAction.OpenApp) ActionResult.FAILED else ActionResult.DONE
        }

        assertEquals(ActionResult.DONE, result)
        assertEquals(2, ran.size)
    }

    @Test
    fun allFailedOrNothingToRun() = runTest {
        assertEquals(ActionResult.FAILED, MacroRunner.run(macro(GestureAction.OpenApp("gone/.A", 0) to 0L)) { ActionResult.FAILED })
        assertEquals(ActionResult.NOTHING, MacroRunner.run(macro()) { ActionResult.DONE })
    }
}
