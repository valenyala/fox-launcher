package dev.foxlauncher.feature.widgets.flashlight

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Flashlight widget: a tap toggles; no tap while the torch is unavailable or in the picker preview. */
@RunWith(AndroidJUnit4::class)
class FlashlightButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aTapToggles() {
        var taps = 0
        composeRule.setContent { FoxTheme { FlashlightButton(state = TorchState.OFF, onClick = { taps++ }) } }

        composeRule.onNodeWithTag(FlashlightWidget.TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.widget_flashlight_off)))
            .performClick()

        assertEquals(1, taps)
    }

    @Test
    fun onSaysOn() {
        composeRule.setContent { FoxTheme { FlashlightButton(state = TorchState.ON, onClick = {}) } }

        composeRule.onNodeWithTag(FlashlightWidget.TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.widget_flashlight_on)))
    }

    @Test
    fun noTapWhileTheCameraIsBusy() {
        composeRule.setContent { FoxTheme { FlashlightButton(state = TorchState.BUSY, onClick = {}) } }

        composeRule.onNodeWithTag(FlashlightWidget.TEST_TAG).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun thePreviewTakesNoTap() {
        composeRule.setContent { FoxTheme { FlashlightButton(state = TorchState.OFF, onClick = null) } }

        composeRule.onNodeWithTag(FlashlightWidget.TEST_TAG).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
}
