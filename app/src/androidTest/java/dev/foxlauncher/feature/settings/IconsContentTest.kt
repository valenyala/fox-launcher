package dev.foxlauncher.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.apps.iconpack.IconPackInfo
import dev.foxlauncher.core.ui.IconShape
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Icons settings (task 7.1). */
@RunWith(AndroidJUnit4::class)
class IconsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = mutableListOf<String>()
    private val arctic = IconPackInfo("com.example.arctic", "Arctic")
    private var state by mutableStateOf(IconsUiState(themedAvailable = true))

    private fun show() {
        composeRule.setContent {
            FoxTheme {
                IconsContent(
                    state = state,
                    onBack = { calls += "back" },
                    onPackChange = { calls += "pack $it" },
                    onShapeChange = { calls += "shape ${it.id}" },
                    onSizeChange = { calls += "size $it" },
                    onShowLabelsChange = { calls += "labels $it" },
                    onThemedChange = { calls += "themed $it" },
                    iconContent = { _, sizeDp -> Box(Modifier.size(sizeDp.dp)) },
                )
            }
        }
    }

    @Test
    fun withoutPacksThePickerSaysHowToGetOne() {
        show()

        composeRule.onNodeWithTag(IconsTestTags.PACK).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.icons_pack_none_installed)).assertIsDisplayed()
    }

    @Test
    fun aPackIsPickedFromTheDialogAndSystemIconsGoBack() {
        state = IconsUiState(packs = persistentListOf(arctic), themedAvailable = true)
        show()

        composeRule.onNodeWithText(context.getString(R.string.icons_pack_system)).assertIsDisplayed()
        composeRule.onNodeWithTag(IconsTestTags.PACK).performClick()
        composeRule.onNodeWithText("Arctic").performClick()
        assertEquals(listOf("pack com.example.arctic"), calls)

        state = state.copy(pack = arctic)
        composeRule.onNodeWithText("Arctic").assertIsDisplayed()
        composeRule.onNodeWithTag(IconsTestTags.PACK).performClick()
        composeRule.onNodeWithText(context.getString(R.string.icons_pack_system)).performClick()
        assertEquals(listOf("pack com.example.arctic", "pack null"), calls)
    }

    @Test
    fun shapeSizeAndLabelsReportTheirChanges() {
        show()

        composeRule.onNodeWithText(context.getString(R.string.icons_shape_squircle)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.icons_shape_circle)).performScrollTo().performClick()
        composeRule.onNodeWithTag(IconsTestTags.SIZE).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(61f) }
        composeRule.onNodeWithTag(IconsTestTags.LABELS).performScrollTo().assertIsOn().performClick()

        assertEquals(listOf("shape ${IconShape.CIRCLE.id}", "size 60", "labels false"), calls)
    }

    @Test
    fun themedIconsShowOnlyWhereAndroidHasThem() {
        show()
        composeRule.onNodeWithTag(IconsTestTags.THEMED).performScrollTo().assertIsOff().performClick()
        assertEquals(listOf("themed true"), calls)

        state = IconsUiState(themedAvailable = false)
        composeRule.onNodeWithTag(IconsTestTags.THEMED).assertDoesNotExist()
    }
}
