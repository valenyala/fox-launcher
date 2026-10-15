package dev.foxlauncher.feature.widgets.lock

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** The Lock screen widget (task 5.7): a tap locks; its picker preview takes no tap. */
@RunWith(AndroidJUnit4::class)
class LockButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aTapLocks() {
        var locks = 0
        composeRule.setContent { FoxTheme { LockButton(onClick = { locks++ }) } }

        composeRule.onNodeWithContentDescription(context.getString(R.string.widget_lock_screen_action)).assertIsDisplayed()
        composeRule.onNodeWithTag(LockScreenWidget.TEST_TAG).performClick()

        assertEquals(1, locks)
    }

    @Test
    fun thePreviewTakesNoTap() {
        composeRule.setContent { FoxTheme { LockButton(onClick = null) } }

        composeRule.onNodeWithTag(LockScreenWidget.TEST_TAG).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
}
