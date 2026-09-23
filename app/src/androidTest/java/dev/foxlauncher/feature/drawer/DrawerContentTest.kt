package dev.foxlauncher.feature.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `DrawerContent` is stateless, so the list, query and icons are all fakes. */
@RunWith(AndroidJUnit4::class)
class DrawerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apps = listOf(app("Calendar"), app("Mail"), app("Maps"))

    @Test
    fun queryShowsOnlyMatchingApps() {
        setDrawer(query = "ma")

        composeRule.onNodeWithText("Mail").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
        composeRule.onNodeWithText("Calendar").assertDoesNotExist()
        composeRule.onNodeWithTag(DrawerTestTags.RAIL).assertDoesNotExist()
    }

    @Test
    fun blankQueryShowsEveryAppWithHeadersAndRail() {
        setDrawer(query = "")

        apps.forEach { composeRule.onNodeWithText(it.label).assertIsDisplayed() }
        composeRule.onNodeWithTag(DrawerTestTags.RAIL).assertIsDisplayed()
    }

    @Test
    fun tappingAnAppReportsIt() {
        val clicked = mutableListOf<AppEntry>()
        setDrawer(query = "", onAppClick = { clicked += it })

        composeRule.onNodeWithText("Maps").performClick()

        assertEquals(listOf("Maps"), clicked.map { it.label })
    }

    private fun setDrawer(query: String, onAppClick: (AppEntry) -> Unit = {}) {
        val state = DrawerUiState(query = query, sections = buildSections(apps, query), isLoading = false)
        composeRule.setContent {
            FoxTheme {
                DrawerContent(
                    state = state,
                    searchState = TextFieldState(query),
                    autoFocusSearch = false,
                    onAppClick = { entry, _ -> onAppClick(entry) },
                    onAppLongClick = {},
                    onDismissPopup = {},
                    onAppInfo = { _, _ -> },
                    onUninstall = {},
                    iconContent = { Box(Modifier.size(48.dp)) },
                )
            }
        }
    }

    private fun app(label: String) = AppEntry(
        component = "test.${label.lowercase()}/.Main",
        userSerial = 0,
        label = label,
        packageName = "test.${label.lowercase()}",
        isWorkProfile = false,
        isSuspended = false,
        isAvailable = true,
        versionCode = 1,
    )
}
