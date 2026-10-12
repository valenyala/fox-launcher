package dev.foxlauncher.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.R
import dev.foxlauncher.core.model.RotationTriggers
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The wallpaper rotation settings (task 4.4) against fake callbacks. */
@RunWith(AndroidJUnit4::class)
class WallpaperSettingsContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    @Test
    fun choosingAnIntervalReportsItsMinutes() {
        setContent()

        composeRule.onNodeWithText(string(R.string.wallpaper_interval)).performClick()
        composeRule.onNodeWithText(plural(R.plurals.wallpaper_every_minutes, 5)).performScrollTo().performClick()

        assertEquals(listOf("interval 5"), calls)
        composeRule.onNodeWithTag(WallpaperSettingsTestTags.INTERVAL_PICKER).assertDoesNotExist()
    }

    @Test
    fun theCurrentIntervalIsShown() {
        setContent(RotationTriggers(intervalMinutes = 180))

        composeRule.onNodeWithText(plural(R.plurals.wallpaper_every_hours, 3)).assertIsDisplayed()
    }

    @Test
    fun theTriggerSwitchesReportTheirNewState() {
        setContent(RotationTriggers(onUnlock = true))

        composeRule.onNodeWithText(string(R.string.wallpaper_on_unlock)).performClick()
        composeRule.onNodeWithText(string(R.string.wallpaper_on_page_change)).performClick()

        assertEquals(listOf("unlock false", "page true"), calls)
    }

    @Test
    fun nextNowAsksForTheNextWallpaper() {
        setContent()

        composeRule.onNodeWithText(string(R.string.wallpaper_next_now)).performClick()

        assertEquals(listOf("next"), calls)
    }

    @Test
    fun withoutTheLiveWallpaperApplyIsOffered() {
        setContent(liveActive = false)

        composeRule.onNodeWithText(string(R.string.wallpaper_apply)).performClick()

        assertEquals(listOf("apply"), calls)
    }

    @Test
    fun withTheLiveWallpaperThereIsNothingToApply() {
        setContent(liveActive = true)

        composeRule.onNodeWithText(string(R.string.wallpaper_apply)).assertDoesNotExist()
    }

    private fun setContent(triggers: RotationTriggers = RotationTriggers(), liveActive: Boolean = true) {
        composeRule.setContent {
            FoxTheme {
                WallpaperSettingsContent(
                    state = WallpaperSettingsUiState(liveWallpaperActive = liveActive, triggers = triggers),
                    onBack = {},
                    onApplyLiveWallpaper = { calls += "apply" },
                    onNextNow = { calls += "next" },
                    onIntervalChange = { calls += "interval $it" },
                    onUnlockChange = { calls += "unlock $it" },
                    onPageChangeChange = { calls += "page $it" },
                )
            }
        }
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun plural(id: Int, n: Int) = composeRule.activity.resources.getQuantityString(id, n, n)
}
