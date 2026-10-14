package dev.foxlauncher.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.automation.AutomationCommand
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Commands from other apps (task 9.4). */
@RunWith(AndroidJUnit4::class)
class AutomationContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Boolean>()

    @Test
    fun offByDefaultTheSwitchReportsAndEveryActionIsListed() {
        composeRule.setContent { FoxTheme { AutomationContent(enabled = false, onEnabledChange = { changes += it }, onBack = {}) } }

        composeRule.onNodeWithTag(AutomationTestTags.SWITCH).assertIsOff().performClick()
        assertEquals(listOf(true), changes)
        AutomationCommand.entries.forEach { composeRule.onNodeWithText(it.action).performScrollTo().assertIsDisplayed() }
    }
}
