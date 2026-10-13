package dev.foxlauncher.feature.memory.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.memory.BoostLimit
import dev.foxlauncher.feature.memory.BoostResult
import dev.foxlauncher.feature.memory.MemorySnapshot
import dev.foxlauncher.feature.memory.RecentApps
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The memory widgets (task 5.3) on fake readings. Run in an English locale. */
@RunWith(AndroidJUnit4::class)
class MemoryWidgetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mb = 1024L * 1024
    private val gb = mb * 1024

    /** 6 GB, 2.1 GB available (so 3.9 GB, 65 %, used), 900 MB cached, 3 of 4 GB swap in use. */
    private val snapshot = MemorySnapshot(
        totalBytes = 6 * gb,
        availableBytes = 2 * gb + 100 * mb,
        thresholdBytes = gb / 4,
        lowMemory = false,
        cachedBytes = 900 * mb,
        buffersBytes = 2 * mb,
        swapTotalBytes = 4 * gb,
        swapFreeBytes = gb,
    )
    private val calls = mutableListOf<String>()

    private fun setContent(content: @Composable () -> Unit) = composeRule.setContent { FoxTheme { content() } }

    // --- gauge ---

    @Test
    fun theGaugeShowsUseAndWhatIsFree() {
        setContent { MemoryGaugeContent(snapshot, spanX = 2) }

        composeRule.onNodeWithText("65%").assertIsDisplayed()
        composeRule.onNodeWithText("2.1 GB free").assertIsDisplayed()
    }

    @Test
    fun theOneCellGaugeShowsTheSame() {
        setContent { MemoryGaugeContent(snapshot, spanX = 1) }

        composeRule.onNodeWithText("65%").assertIsDisplayed()
        composeRule.onNodeWithText("2.1 GB free").assertIsDisplayed()
    }

    @Test
    fun beforeTheFirstReadingTheGaugeShowsADash() {
        setContent { MemoryGaugeContent(null, spanX = 2) }

        composeRule.onNodeWithText(context.getString(R.string.memory_unknown)).assertIsDisplayed()
    }

    // --- boost ---

    @Test
    fun tappingTheRocketBoosts() {
        setContent { MemoryBoostContent(BoostUiState.Idle, limit = null, onBoost = { calls += "boost" }, onInfo = {}) }

        composeRule.onNodeWithContentDescription(context.getString(R.string.memory_boost_action)).performClick()

        assertEquals(listOf("boost"), calls)
    }

    @Test
    fun aRunningBoostSaysSoAndCannotBeTappedAgain() {
        setContent { MemoryBoostContent(BoostUiState.Boosting, limit = null, onBoost = { calls += "boost" }, onInfo = {}) }

        composeRule.onNodeWithText(context.getString(R.string.memory_boosting)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.memory_boosting)).assertIsNotEnabled()
    }

    @Test
    fun theMeasuredAmountIsShownAfterwards() {
        setContent { MemoryBoostContent(BoostUiState.Done(BoostResult(freedBytes = 123 * mb)), null, {}, {}) }

        composeRule.onNodeWithText(context.getString(R.string.memory_freed, "123")).assertIsDisplayed()
    }

    @Test
    fun nothingFreedSaysSoRatherThanZero() {
        setContent { MemoryBoostContent(BoostUiState.Done(BoostResult(freedBytes = 100 * 1024)), null, {}, {}) }

        composeRule.onNodeWithText(context.getString(R.string.memory_nothing_freed)).assertIsDisplayed()
    }

    @Test
    fun onAndroid14AndLaterACaptionExplainsTheLimit() {
        setContent { MemoryBoostContent(BoostUiState.Idle, limit = BoostLimit.ANDROID_14, onBoost = {}, onInfo = { calls += "info" }) }

        composeRule.onNodeWithText(context.getString(R.string.memory_own_only)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.memory_own_only_info)).performClick()

        assertEquals(listOf("info"), calls)
    }

    @Test
    fun onMiuiItsOwnCaptionExplainsTheLimit() {
        setContent { MemoryBoostContent(BoostUiState.Idle, limit = BoostLimit.MIUI, onBoost = {}, onInfo = { calls += "info" }) }

        composeRule.onNodeWithText(context.getString(R.string.memory_limited_miui)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.memory_own_only_info)).performClick()

        assertEquals(listOf("info"), calls)
    }

    @Test
    fun withoutALimitThereIsNoCaption() {
        setContent { MemoryBoostContent(BoostUiState.Idle, limit = null, onBoost = {}, onInfo = {}) }

        composeRule.onNodeWithText(context.getString(R.string.memory_own_only)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.memory_limited_miui)).assertDoesNotExist()
    }

    // --- detail ---

    @Test
    fun theDetailRowsShowTheBreakdown() {
        setContent { detail(recent = null, showRecent = false) }

        composeRule.onNodeWithText("3.9 GB").assertIsDisplayed()
        composeRule.onNodeWithText("2.1 GB").assertIsDisplayed()
        composeRule.onNodeWithText("900 MB").assertIsDisplayed()
        composeRule.onNodeWithText("3.0 / 4.0 GB").assertIsDisplayed()
    }

    @Test
    fun recentAppsOpenTheirAppInfo() {
        val maps = AppEntry("com.example.maps/.Main", 0, "Maps", "com.example.maps", false, false, true, 1)
        setContent { detail(recent = RecentApps(listOf(maps)), showRecent = true) }

        composeRule.onNodeWithText("Maps").performClick()

        assertEquals(listOf("info com.example.maps"), calls)
    }

    @Test
    fun withoutUsageAccessItOffersTheSetting() {
        setContent { detail(recent = RecentApps(null), showRecent = true) }

        composeRule.onNodeWithText(context.getString(R.string.memory_usage_access)).performClick()

        assertEquals(listOf("usage access"), calls)
    }

    @Test
    fun belowThreeRowsThereAreNoRecentApps() {
        setContent { detail(recent = RecentApps(null), showRecent = false) }

        composeRule.onNodeWithText(context.getString(R.string.memory_usage_access)).assertDoesNotExist()
    }

    @Composable
    private fun detail(recent: RecentApps?, showRecent: Boolean) {
        MemoryDetailContent(
            snapshot = snapshot,
            history = listOf(0.5f, 0.6f, 0.65f),
            recent = recent,
            showRecent = showRecent,
            onAppInfo = { calls += "info ${it.packageName}" },
            onUsageAccess = { calls += "usage access" },
            appIcon = { Box(Modifier.size(20.dp)) },
        )
    }
}
