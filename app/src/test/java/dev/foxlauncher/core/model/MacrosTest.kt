package dev.foxlauncher.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class MacrosTest {

    @Test
    fun aMacroHoldsNoMacrosAndNoEmptySteps() {
        val macro = GestureAction.Macro(
            listOf(
                MacroStep(GestureAction.Macro(listOf(MacroStep(GestureAction.LockScreen)))),
                MacroStep(GestureAction.None),
                MacroStep(GestureAction.NextWallpaper),
            ),
        )

        assertEquals(listOf(MacroStep(GestureAction.NextWallpaper)), Macros.sanitize(macro).steps)
    }

    @Test
    fun stepsAndWaitsAreCapped() {
        val macro = GestureAction.Macro(List(12) { MacroStep(GestureAction.OpenDrawer, delayMs = if (it == 0) -5 else 60_000) })

        val clean = Macros.sanitize(macro).steps
        assertEquals(Macros.MAX_STEPS, clean.size)
        assertEquals(0L, clean.first().delayMs)
        assertEquals(Macros.MAX_DELAY_MS, clean.last().delayMs)
    }

    @Test
    fun theWaitCyclesThroughItsChoices() {
        assertEquals(Macros.DELAYS_MS, Macros.DELAYS_MS.runningFold(0L) { d, _ -> Macros.nextDelay(d) }.dropLast(1))
        assertEquals(0L, Macros.nextDelay(Macros.DELAYS_MS.last()))
        assertEquals(0L, Macros.nextDelay(1_234L), "a wait not on the list starts over")
    }

    @Test
    fun aMacroNeedsTheServiceWhenAStepDoes() {
        assertTrue(GestureAction.Macro(listOf(MacroStep(GestureAction.OpenDrawer), MacroStep(GestureAction.LockScreen))).requiresAccessibility)
        assertFalse(GestureAction.Macro(listOf(MacroStep(GestureAction.OpenDrawer))).requiresAccessibility)
    }
}
