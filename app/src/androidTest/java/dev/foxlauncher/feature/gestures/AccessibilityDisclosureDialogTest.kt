package dev.foxlauncher.feature.gestures

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.gestures.system.AccessibilityDisclosureDialog
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The disclosure shown before sending the user to the accessibility settings (task 3.4). */
@RunWith(AndroidJUnit4::class)
class AccessibilityDisclosureDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var opened = 0
    private var dismissed = 0

    @Test
    fun itSaysWhatTheServiceDoesNotDo() {
        setDialog()

        composeRule.onNodeWithText(string(R.string.accessibility_disclosure)).assertIsDisplayed()
    }

    @Test
    fun openSettingsAndNotNowReportTheirChoice() {
        setDialog()

        composeRule.onNodeWithText(string(R.string.accessibility_open_settings)).performClick()
        composeRule.onNodeWithText(string(R.string.accessibility_not_now)).performClick()

        assertEquals(1, opened)
        assertEquals(1, dismissed)
    }

    @Test
    fun aStoppedServiceIsToBeTurnedOffAndOn() {
        setDialog(stopped = true)

        composeRule.onNodeWithText(string(R.string.accessibility_stopped)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.accessibility_disclosure)).assertDoesNotExist()
    }

    private fun setDialog(stopped: Boolean = false) {
        composeRule.setContent {
            FoxTheme {
                AccessibilityDisclosureDialog(onOpenSettings = { opened++ }, onDismiss = { dismissed++ }, stopped = stopped)
            }
        }
    }

    private fun string(id: Int) = composeRule.activity.getString(id)
}
