package dev.foxlauncher.feature.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
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

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

    @Test
    fun tabsAppearOnlyWithAWorkProfile() {
        setDrawer(query = "")
        composeRule.onNodeWithTag(DrawerTestTags.TABS).assertDoesNotExist()
    }

    @Test
    fun workTabListsWorkAppsAndThePauseToggle() {
        val mixed = apps + app("Slack", work = true)
        val state = DrawerUiState(
            sections = buildSections(appsForTab(mixed, DrawerTab.WORK, ""), ""),
            isLoading = false,
            work = WorkUiState(isPaused = false),
            tab = DrawerTab.WORK,
        )
        val pauses = mutableListOf<Boolean>()
        setDrawer(state, onWorkPausedChange = { pauses += it })

        composeRule.onNodeWithTag(DrawerTestTags.TABS).assertIsDisplayed()
        composeRule.onNodeWithText("Slack").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.work_pause)).performClick()

        assertEquals(listOf(true), pauses)
    }

    @Test
    fun appNamesUseTheThemesOnSurfaceColour() {
        // A see-through surface matches no theme colour, so Surface cannot pick its content colour
        // itself: unset, names were black, invisible on the dark drawer.
        var contentColor = Color.Unspecified
        var onSurface = Color.Unspecified
        setDrawer(
            DrawerUiState(sections = buildSections(apps, ""), isLoading = false),
            onSurface = { onSurface = it },
            iconContent = { contentColor = LocalContentColor.current },
        )
        composeRule.waitForIdle()

        assertEquals(onSurface, contentColor)
    }

    @Test
    fun closingTheDrawerScrollsItBackToTheTop() {
        val many = (1..80).map { app("App %02d".format(it)) }
        var shown by mutableStateOf(true)
        composeRule.setContent {
            FoxTheme {
                DrawerContent(
                    state = DrawerUiState(sections = buildSections(many, ""), isLoading = false),
                    searchState = TextFieldState(),
                    autoFocusSearch = false,
                    onAppClick = { _, _ -> },
                    onAppLongClick = {},
                    onDismissPopup = {},
                    onAppInfo = { _, _ -> },
                    onUninstall = {},
                    iconContent = { Box(Modifier.size(48.dp)) },
                    isShown = shown,
                )
            }
        }
        composeRule.onNodeWithTag(DrawerTestTags.GRID).performScrollToNode(hasText("App 80"))
        composeRule.onNodeWithText("App 01").assertDoesNotExist()

        shown = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("App 01").assertIsDisplayed()
    }

    private fun setDrawer(query: String, onAppClick: (AppEntry) -> Unit = {}) {
        setDrawer(
            DrawerUiState(query = query, sections = buildSections(apps, query), isLoading = false),
            onAppClick = onAppClick,
        )
    }

    private fun setDrawer(
        state: DrawerUiState,
        onAppClick: (AppEntry) -> Unit = {},
        onWorkPausedChange: (Boolean) -> Unit = {},
        onSurface: (Color) -> Unit = {},
        iconContent: @Composable () -> Unit = {},
    ) {
        composeRule.setContent {
            FoxTheme {
                onSurface(MaterialTheme.colorScheme.onSurface)
                DrawerContent(
                    state = state,
                    searchState = TextFieldState(state.query),
                    autoFocusSearch = false,
                    onAppClick = { entry, _ -> onAppClick(entry) },
                    onAppLongClick = {},
                    onDismissPopup = {},
                    onAppInfo = { _, _ -> },
                    onUninstall = {},
                    onWorkPausedChange = onWorkPausedChange,
                    iconContent = {
                        iconContent()
                        Box(Modifier.size(48.dp))
                    },
                )
            }
        }
    }

    private fun app(label: String, work: Boolean = false) = AppEntry(
        component = "test.${label.lowercase()}/.Main",
        userSerial = if (work) 10 else 0,
        label = label,
        packageName = "test.${label.lowercase()}",
        isWorkProfile = work,
        isSuspended = false,
        isAvailable = true,
        versionCode = 1,
    )
}
