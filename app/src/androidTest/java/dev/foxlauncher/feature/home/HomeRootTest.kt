package dev.foxlauncher.feature.home

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `HomeRoot` is stateless, so the role state is injected here instead of calling `RoleManager`.
 */
@RunWith(AndroidJUnit4::class)
class HomeRootTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val buttonLabel = context.getString(R.string.set_as_default_launcher)

    @Test
    fun showsThePromptWhenFoxIsNotTheDefaultLauncher() {
        composeRule.setContent {
            FoxTheme { HomeRoot(isDefaultLauncher = false, onRequestDefaultLauncher = {}) { Text(HOME) } }
        }

        composeRule.onNodeWithText(buttonLabel).assertIsDisplayed()
    }

    @Test
    fun tappingTheButtonRequestsTheRole() {
        var requests = 0
        composeRule.setContent {
            FoxTheme { HomeRoot(isDefaultLauncher = false, onRequestDefaultLauncher = { requests++ }) { Text(HOME) } }
        }

        composeRule.onNodeWithText(buttonLabel).performClick()

        assertEquals(1, requests)
    }

    @Test
    fun showsOnlyTheHomeContentWhenFoxIsTheDefaultLauncher() {
        composeRule.setContent {
            FoxTheme { HomeRoot(isDefaultLauncher = true, onRequestDefaultLauncher = {}) { Text(HOME) } }
        }

        composeRule.onNodeWithText(buttonLabel).assertDoesNotExist()
        composeRule.onNodeWithText(HOME).assertIsDisplayed()
    }

    @Test
    fun homeContentIsNotShownBeforeFoxIsTheDefaultLauncher() {
        composeRule.setContent {
            FoxTheme { HomeRoot(isDefaultLauncher = false, onRequestDefaultLauncher = {}) { Text(HOME) } }
        }

        composeRule.onNodeWithText(HOME).assertDoesNotExist()
    }

    private companion object {
        const val HOME = "home content"
    }
}
