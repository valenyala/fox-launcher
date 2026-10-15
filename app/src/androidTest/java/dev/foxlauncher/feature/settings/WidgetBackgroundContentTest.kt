package dev.foxlauncher.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The widget background setting (task 5.6). */
@RunWith(AndroidJUnit4::class)
class WidgetBackgroundContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Int>()
    private var opacity by mutableIntStateOf(25)

    private fun show() {
        composeRule.setContent {
            FoxTheme {
                WidgetBackgroundContent(opacity = opacity, onBack = {}, onOpacityChange = { changes += it }, preview = { Box {} })
            }
        }
    }

    @Test
    fun theOpacityShowsAsAPercentage() {
        show()

        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithTag(WidgetBackgroundTestTags.PREVIEW).assertIsDisplayed()

        opacity = 0
        composeRule.onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun theSliderSnapsToFivePercentSteps() {
        show()

        composeRule.onNodeWithTag(WidgetBackgroundTestTags.OPACITY).performSemanticsAction(SemanticsActions.SetProgress) { it(62f) }
        composeRule.onNodeWithTag(WidgetBackgroundTestTags.OPACITY).performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }

        assertEquals(listOf(60, 100), changes)
    }
}
