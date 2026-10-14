package dev.foxlauncher.feature.gestures

import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.Macros
import kotlinx.coroutines.delay

/** Running a macro (task 9.3). Pure: it is given how to run one step, so it is unit tested. */
object MacroRunner {

    /**
     * Runs each step of [macro] after its delay, through [runStep]. It stops at the first step that
     * needs the user (the accessibility service, Fox's live wallpaper) and returns that result, so
     * the home can ask; a step that fails is skipped. Otherwise [ActionResult.DONE] when any step
     * ran, [ActionResult.FAILED] when all failed, [ActionResult.NOTHING] for an empty macro.
     */
    suspend fun run(macro: GestureAction.Macro, runStep: (GestureAction) -> ActionResult): ActionResult {
        var outcome = ActionResult.NOTHING
        for (step in Macros.sanitize(macro).steps) {
            if (step.delayMs > 0) delay(step.delayMs)
            when (val result = runStep(step.action)) {
                ActionResult.NEEDS_ACCESSIBILITY, ActionResult.ACCESSIBILITY_STOPPED, ActionResult.NEEDS_LIVE_WALLPAPER -> return result
                ActionResult.DONE -> outcome = ActionResult.DONE
                ActionResult.FAILED -> if (outcome == ActionResult.NOTHING) outcome = ActionResult.FAILED
                ActionResult.NOTHING -> Unit
            }
        }
        return outcome
    }
}
