package dev.foxlauncher.feature.widgets.privatewallpapers

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Private wallpapers widget: a tap toggles, and it says whether private pictures are included. */
@RunWith(AndroidJUnit4::class)
class PrivateWallpapersSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aTapToggles() {
        var taps = 0
        composeRule.setContent { FoxTheme { PrivateWallpapersSwitch(included = false, onClick = { taps++ }) } }

        composeRule.onNodeWithTag(PrivateWallpapersWidget.TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.widget_private_wallpapers_off)))
            .performClick()

        assertEquals(1, taps)
    }

    @Test
    fun saysWhenIncluded() {
        composeRule.setContent { FoxTheme { PrivateWallpapersSwitch(included = true, onClick = {}) } }

        composeRule.onNodeWithTag(PrivateWallpapersWidget.TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.widget_private_wallpapers_on)))
    }
}
