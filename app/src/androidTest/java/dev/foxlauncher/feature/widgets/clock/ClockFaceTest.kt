package dev.foxlauncher.feature.widgets.clock

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.widgets.NativeWidgetScope
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Clock widget opens the clock app on a tap (user request, 2026-10-14); its picker preview does not. */
@RunWith(AndroidJUnit4::class)
class ClockFaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aTapOpensTheClockApp() {
        var opened = 0
        composeRule.setContent { FoxTheme { ClockFace(NativeWidgetScope(4, 1), onClick = { opened++ }) } }

        composeRule.onNodeWithTag(ClockWidget.TEST_TAG).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun thePreviewTakesNoTap() {
        composeRule.setContent { FoxTheme { ClockFace(NativeWidgetScope(4, 1, isPreview = true), onClick = null) } }

        composeRule.onNodeWithTag(ClockWidget.TEST_TAG).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
}
