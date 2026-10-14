package dev.foxlauncher.feature.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.home.grid.GridSpec
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.widgets.app.AppWidgetCatalog
import dev.foxlauncher.feature.widgets.app.AppWidgetOption
import dev.foxlauncher.feature.widgets.app.ProviderSize
import dev.foxlauncher.feature.widgets.app.WidgetSpans
import dev.foxlauncher.core.common.Collation
import java.util.Locale
import kotlin.math.ceil
import dev.foxlauncher.feature.widgets.clock.ClockWidget
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The widget picker: native widgets (task 5.1) and app widgets (task 6.2). */
@RunWith(AndroidJUnit4::class)
class WidgetPickerSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val picked = mutableListOf<String>()

    @Test
    fun eachWidgetIsListedWithItsSizeAndPickingOneReportsIt() {
        composeRule.setContent {
            FoxTheme { WidgetPickerSheet(specs = listOf(ClockWidget.spec), onPick = { picked += it.kind }, onDismiss = {}) }
        }

        composeRule.onNodeWithText(context.getString(R.string.widget_clock)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widget_size, 4, 1), substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widget_clock)).performClick()

        assertEquals(listOf(ClockWidget.KIND), picked)
    }

    @Test
    fun tappingAPreviewWithItsOwnButtonAddsTheWidget() {
        // Like Boost: the whole widget is a button.
        val button = NativeWidgetSpec(
            kind = "button",
            nameRes = R.string.widget_memory_boost,
            descriptionRes = R.string.widget_memory_boost_description,
            minSpan = Span(1, 1),
            defaultSpan = Span(1, 1),
            maxSpan = Span(1, 1),
            content = { Button(onClick = { picked += "inner button" }) { Text("go") } },
        )
        composeRule.setContent {
            FoxTheme { WidgetPickerSheet(specs = listOf(button), onPick = { picked += it.kind }, onDismiss = {}) }
        }

        composeRule.onNodeWithTag(WidgetPickerTestTags.preview("button"), useUnmergedTree = true).performClick()

        assertEquals(listOf("button"), picked)
    }

    // --- app widgets (task 6.2) ---

    private val cell = 16.dp

    private fun option(pkg: String, app: String, label: String, serial: Long = 0, widthCells: Float = 1f) = AppWidgetOption(
        provider = "$pkg/.$label",
        userSerial = serial,
        packageName = pkg,
        appLabel = app,
        label = label,
        description = null,
        isWorkProfile = serial != 0L,
        size = with(composeRule.density) { ProviderSize(minWidth = ceil(cell.toPx() * widthCells).toInt(), minHeight = 1) },
    )

    private val clock = option("com.clock", "Clock", "Analog")
    private val clockWide = option("com.clock", "Clock", "Big", widthCells = 1.5f)
    private val calendar = option("com.cal", "Calendar", "Month")
    private val calendarWork = option("com.cal", "Calendar", "Agenda", serial = 10)
    private val tooWide = option("com.huge", "Huge", "Banner", widthCells = 6f)
    private val pickedApp = mutableListOf<Pair<String, WidgetSpans>>()

    private fun showAppWidgets(options: List<AppWidgetOption>) {
        val groups = AppWidgetCatalog.group(options, Collation.labelComparator(Locale.ENGLISH))
        composeRule.setContent {
            FoxTheme {
                WidgetPickerSheet(
                    specs = listOf(ClockWidget.spec),
                    onPick = { picked += it.kind },
                    onDismiss = {},
                    appWidgets = groups,
                    cellSize = DpSize(cell, cell),
                    grid = GridSpec(cols = 5, rows = 6, dockSlots = 5),
                    onPickAppWidget = { option, spans -> pickedApp += option.key to spans },
                    appWidgetPreview = { Box(Modifier.fillMaxSize().testTag("fake preview ${it.label}")) },
                )
            }
        }
        composeRule.onNodeWithTag(WidgetPickerTestTags.APPS_TAB).performClick()
    }

    private fun appEntryTags(): List<String> =
        composeRule.onAllNodes(
            SemanticsMatcher("app entry") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(WidgetPickerTestTags.APP_ENTRY) == true },
        ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    @Test
    fun appWidgetsAreGroupedByAppAndProfile() {
        showAppWidgets(listOf(clockWide, calendarWork, clock, calendar))

        assertEquals(
            listOf(
                WidgetPickerTestTags.appGroup("com.cal|0"),
                WidgetPickerTestTags.appWidget(calendar.key),
                WidgetPickerTestTags.appGroup("com.cal|10"),
                WidgetPickerTestTags.appWidget(calendarWork.key),
                WidgetPickerTestTags.appGroup("com.clock|0"),
                WidgetPickerTestTags.appWidget(clock.key),
                WidgetPickerTestTags.appWidget(clockWide.key),
            ),
            appEntryTags(),
        )
        composeRule.onNodeWithText("Calendar").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widgets_app_work, "Calendar")).assertIsDisplayed()
        composeRule.onNodeWithTag("fake preview Agenda", useUnmergedTree = true).assertExists()
    }

    @Test
    fun anAppWidgetShowsItsCellsAndPickingItReportsThem() {
        showAppWidgets(listOf(clockWide))

        composeRule.onNodeWithText(context.getString(R.string.widget_size, 2, 1)).assertIsDisplayed()
        composeRule.onNodeWithTag(WidgetPickerTestTags.appPreview(clockWide.key), useUnmergedTree = true).performClick()

        assertEquals(listOf(clockWide.key to WidgetSpans(Span(2, 1), Span(2, 1), Span(2, 1))), pickedApp)
        assertEquals(emptyList(), picked)
    }

    @Test
    fun appWidgetsTooLargeForTheGridAreLeftOut() {
        showAppWidgets(listOf(tooWide, clock))

        assertEquals(listOf(WidgetPickerTestTags.appGroup("com.clock|0"), WidgetPickerTestTags.appWidget(clock.key)), appEntryTags())
    }

    @Test
    fun withNoAppWidgetsTheTabSaysSo() {
        showAppWidgets(listOf(tooWide))

        composeRule.onNodeWithText(context.getString(R.string.widgets_apps_empty)).assertIsDisplayed()
    }
}
