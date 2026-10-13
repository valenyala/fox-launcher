package dev.foxlauncher.feature.widgets

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.widgets.clock.ClockWidget
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The native widget picker (task 5.1). */
@RunWith(AndroidJUnit4::class)
class WidgetPickerSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val picked = mutableListOf<String>()

    @Test
    fun eachWidgetIsListedWithItsSizeAndPickingOneReportsIt() {
        composeRule.setContent {
            FoxTheme { WidgetPickerSheet(specs = listOf(ClockWidget.spec), onPick = { picked += it.kind }, onDismiss = {}) }
        }

        composeRule.onNodeWithText(context.getString(R.string.widget_clock)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widget_size, 4, 1), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widget_clock)).performClick()

        assertEquals(listOf(ClockWidget.KIND), picked)
    }
}
