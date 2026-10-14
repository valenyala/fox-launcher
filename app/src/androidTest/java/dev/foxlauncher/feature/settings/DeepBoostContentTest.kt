package dev.foxlauncher.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.memory.shizuku.DeepBoostState
import dev.foxlauncher.feature.memory.shizuku.ShizukuStatus
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The deep boost settings (task 9.1). */
@RunWith(AndroidJUnit4::class)
class DeepBoostContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(DeepBoostState(ShizukuStatus.NOT_INSTALLED, enabled = false))

    private fun show() {
        composeRule.setContent {
            FoxTheme {
                DeepBoostContent(
                    state = state,
                    onBack = { calls += "back" },
                    onEnabledChange = { calls += "enabled $it" },
                    onRequestPermission = { calls += "permission" },
                    onOpenShizuku = { calls += "open" },
                )
            }
        }
    }

    private fun assertStep(status: ShizukuStatus, text: Int, action: Int?, call: String?) {
        state = DeepBoostState(status, enabled = false)
        calls.clear()
        composeRule.onNodeWithText(context.getString(text)).assertIsDisplayed()
        if (action == null) {
            composeRule.onNodeWithTag(DeepBoostTestTags.ACTION).assertDoesNotExist()
        } else {
            composeRule.onNodeWithText(context.getString(action)).performClick()
            assertEquals(listOf(call), calls, "$status")
        }
    }

    @Test
    fun eachStatusSaysWhereShizukuStandsAndOffersTheNextStep() {
        show()

        assertStep(ShizukuStatus.NOT_INSTALLED, R.string.deep_boost_status_not_installed, R.string.deep_boost_get, "open")
        assertStep(ShizukuStatus.NOT_RUNNING, R.string.deep_boost_status_not_running, R.string.deep_boost_open, "open")
        assertStep(ShizukuStatus.TOO_OLD, R.string.deep_boost_status_too_old, R.string.deep_boost_open, "open")
        assertStep(ShizukuStatus.NEEDS_PERMISSION, R.string.deep_boost_status_needs_permission, R.string.deep_boost_allow, "permission")
        assertStep(ShizukuStatus.DENIED, R.string.deep_boost_status_denied, R.string.deep_boost_open, "open")
        assertStep(ShizukuStatus.READY, R.string.deep_boost_status_ready, action = null, call = null)
    }

    @Test
    fun theSwitchIsOffUntilTurnedOnAndReportsTheChange() {
        state = DeepBoostState(ShizukuStatus.READY, enabled = false)
        show()

        composeRule.onNodeWithTag(DeepBoostTestTags.SWITCH).assertIsOff().performClick()
        assertEquals(listOf("enabled true"), calls)

        state = DeepBoostState(ShizukuStatus.READY, enabled = true)
        composeRule.onNodeWithTag(DeepBoostTestTags.SWITCH).assertIsOn()
    }

    @Test
    fun onButNotReadySaysBoostWorksAsBefore() {
        state = DeepBoostState(ShizukuStatus.NOT_RUNNING, enabled = true)
        show()

        composeRule.onNodeWithText(context.getString(R.string.deep_boost_fallback)).assertIsDisplayed()
    }
}
