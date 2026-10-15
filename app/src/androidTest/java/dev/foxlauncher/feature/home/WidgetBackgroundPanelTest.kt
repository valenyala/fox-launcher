package dev.foxlauncher.feature.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One widget's background panel (task 5.8). */
@RunWith(AndroidJUnit4::class)
class WidgetBackgroundPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val changes = mutableListOf<Int?>()

    private fun show(own: Int?, followsSetting: Boolean) {
        composeRule.setContent {
            FoxTheme {
                WidgetBackgroundPanel(
                    anchor = Rect(0f, 0f, 100f, 100f),
                    own = own,
                    defaultOpacity = 25,
                    followsSetting = followsSetting,
                    onChange = { changes += it },
                    onDone = {},
                )
            }
        }
    }

    @Test
    fun aFoxWidgetWithoutItsOwnShowsTheSettingWithTheSwitchOn() {
        show(own = null, followsSetting = true)

        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithTag(WidgetBackgroundPanelTestTags.SAME_AS_OTHERS).assertIsOn().performClick()
        composeRule.onNodeWithTag(WidgetBackgroundPanelTestTags.SLIDER).performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }

        // Off the setting it keeps the setting's value as its own; the slider then snaps to 5% steps.
        assertEquals(listOf<Int?>(25, 40), changes)
    }

    @Test
    fun aFoxWidgetWithItsOwnHasTheSwitchOff() {
        show(own = 80, followsSetting = true)

        composeRule.onNodeWithText("80%").assertIsDisplayed()
        composeRule.onNodeWithTag(WidgetBackgroundPanelTestTags.SAME_AS_OTHERS).assertIsOff().performClick()

        assertEquals(listOf<Int?>(null), changes)
    }

    @Test
    fun anAppWidgetStartsWithoutABackdropAndHasNoSwitch() {
        show(own = null, followsSetting = false)

        composeRule.onNodeWithText("0%").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widget_background_app_note)).assertIsDisplayed()
        composeRule.onNodeWithTag(WidgetBackgroundPanelTestTags.SAME_AS_OTHERS).assertDoesNotExist()
    }
}
