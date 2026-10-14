package dev.foxlauncher.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.memory.DeepBoostState
import dev.foxlauncher.feature.shizuku.ShizukuStatus
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Shizuku settings: the deep boost (task 9.1), the navigation buttons (task 9.6) and the apps without them (9.7). */
@RunWith(AndroidJUnit4::class)
class ShizukuContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = mutableListOf<String>()
    private var deepBoost by mutableStateOf(DeepBoostState(ShizukuStatus.NOT_INSTALLED, enabled = false))
    private var navigation by mutableStateOf(NavigationButtonsState(hidden = false))
    private var buttonAppsCount by mutableStateOf(0)

    private fun show() {
        composeRule.setContent {
            FoxTheme {
                ShizukuContent(
                    deepBoost = deepBoost,
                    navigation = navigation,
                    buttonAppsCount = buttonAppsCount,
                    onBack = { calls += "back" },
                    onDeepBoostChange = { calls += "deep boost $it" },
                    onNavigationButtonsHiddenChange = { calls += "hidden $it" },
                    onOpenButtonApps = { calls += "button apps" },
                    onRequestPermission = { calls += "permission" },
                    onOpenShizuku = { calls += "open" },
                )
            }
        }
    }

    private fun assertStep(status: ShizukuStatus, text: Int, action: Int?, call: String?) {
        deepBoost = DeepBoostState(status, enabled = false)
        calls.clear()
        composeRule.onNodeWithText(context.getString(text)).assertIsDisplayed()
        if (action == null) {
            composeRule.onNodeWithTag(ShizukuTestTags.ACTION).assertDoesNotExist()
        } else {
            composeRule.onNodeWithText(context.getString(action)).performClick()
            assertEquals(listOf(call), calls, "$status")
        }
    }

    @Test
    fun eachStatusSaysWhereShizukuStandsAndOffersTheNextStep() {
        show()

        assertStep(ShizukuStatus.NOT_INSTALLED, R.string.shizuku_status_not_installed, R.string.shizuku_get, "open")
        assertStep(ShizukuStatus.NOT_RUNNING, R.string.shizuku_status_not_running, R.string.shizuku_open, "open")
        assertStep(ShizukuStatus.TOO_OLD, R.string.shizuku_status_too_old, R.string.shizuku_open, "open")
        assertStep(ShizukuStatus.NEEDS_PERMISSION, R.string.shizuku_status_needs_permission, R.string.shizuku_allow, "permission")
        assertStep(ShizukuStatus.DENIED, R.string.shizuku_status_denied, R.string.shizuku_open, "open")
        assertStep(ShizukuStatus.READY, R.string.shizuku_status_ready, action = null, call = null)
    }

    @Test
    fun theDeepBoostSwitchIsOffUntilTurnedOnAndReportsTheChange() {
        deepBoost = DeepBoostState(ShizukuStatus.READY, enabled = false)
        show()

        composeRule.onNodeWithTag(ShizukuTestTags.DEEP_BOOST_SWITCH).assertIsOff().performClick()
        assertEquals(listOf("deep boost true"), calls)

        deepBoost = DeepBoostState(ShizukuStatus.READY, enabled = true)
        composeRule.onNodeWithTag(ShizukuTestTags.DEEP_BOOST_SWITCH).assertIsOn()
    }

    @Test
    fun deepBoostOnButNotReadySaysBoostWorksAsBefore() {
        deepBoost = DeepBoostState(ShizukuStatus.NOT_RUNNING, enabled = true)
        show()

        composeRule.onNodeWithText(context.getString(R.string.deep_boost_fallback)).assertIsDisplayed()
    }

    @Test
    fun theNavigationSwitchWaitsForShizukuButShowsTheSystemsSetting() {
        deepBoost = DeepBoostState(ShizukuStatus.NOT_RUNNING, enabled = false)
        navigation = NavigationButtonsState(hidden = true)
        show()

        composeRule.onNodeWithTag(ShizukuTestTags.NAVIGATION_SWITCH).performScrollTo().assertIsOn().assertIsNotEnabled().performClick()
        composeRule.onNodeWithText(context.getString(R.string.navigation_buttons_needs_shizuku)).assertIsDisplayed()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun theNavigationSwitchAsksToHideAndShowsWhatTheSystemDid() {
        deepBoost = DeepBoostState(ShizukuStatus.READY, enabled = false)
        show()

        composeRule.onNodeWithTag(ShizukuTestTags.NAVIGATION_SWITCH).performScrollTo().assertIsOff().assertIsEnabled().performClick()
        assertEquals(listOf("hidden true"), calls)

        // While the shell works, a second tap does nothing.
        navigation = NavigationButtonsState(hidden = false, busy = true)
        composeRule.onNodeWithTag(ShizukuTestTags.NAVIGATION_SWITCH).assertIsNotEnabled()

        navigation = NavigationButtonsState(hidden = true)
        composeRule.onNodeWithTag(ShizukuTestTags.NAVIGATION_SWITCH).assertIsOn().assertIsEnabled()
    }

    @Test
    fun aFailedChangeOrAnUnreadableSettingIsSaid() {
        deepBoost = DeepBoostState(ShizukuStatus.READY, enabled = false)
        navigation = NavigationButtonsState(hidden = false, failed = true)
        show()

        composeRule.onNodeWithText(context.getString(R.string.navigation_buttons_failed)).performScrollTo().assertIsDisplayed()

        navigation = NavigationButtonsState(hidden = null)
        composeRule.onNodeWithText(context.getString(R.string.navigation_buttons_unknown)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ShizukuTestTags.NAVIGATION_SWITCH).assertIsOff().assertIsEnabled()
    }

    @Test
    fun theAppsWithoutButtonsRowSaysHowManyAndWhetherTheyMatterNow() {
        deepBoost = DeepBoostState(ShizukuStatus.READY, enabled = false)
        navigation = NavigationButtonsState(hidden = true)
        show()

        composeRule.onNodeWithText(context.getString(R.string.button_apps_none)).performScrollTo().assertIsDisplayed()

        buttonAppsCount = 2
        val two = context.resources.getQuantityString(R.plurals.button_apps_count, 2, 2)
        composeRule.onNodeWithText(two).performScrollTo().assertIsDisplayed()

        navigation = NavigationButtonsState(hidden = false)
        composeRule.onNodeWithText(context.getString(R.string.button_apps_everywhere)).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(ShizukuTestTags.BUTTON_APPS).performClick()
        assertEquals(listOf("button apps"), calls)
    }

    @Test
    fun withoutShizukuTheSwitchStillWorksWhenFoxWritesTheSettingItself() {
        deepBoost = DeepBoostState(ShizukuStatus.NOT_RUNNING, enabled = false)
        navigation = NavigationButtonsState(hidden = true, direct = true)
        show()

        composeRule.onNodeWithTag(ShizukuTestTags.NAVIGATION_SWITCH).performScrollTo().assertIsOn().assertIsEnabled().performClick()
        composeRule.onNodeWithText(context.getString(R.string.navigation_buttons_needs_shizuku)).assertDoesNotExist()
        assertEquals(listOf("hidden false"), calls)
    }
}
