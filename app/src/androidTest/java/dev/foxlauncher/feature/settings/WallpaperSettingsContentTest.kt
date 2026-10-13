package dev.foxlauncher.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.R
import dev.foxlauncher.core.data.db.WallpaperSourceType
import dev.foxlauncher.core.model.RotationTriggers
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The wallpaper settings (tasks 4.4, 4.6, 4.7) against fake actions. */
@RunWith(AndroidJUnit4::class)
class WallpaperSettingsContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    private val builtIn = WallpaperSourceUi(1, WallpaperSourceType.BUILT_IN, "Fox", enabled = true, needsReauth = false, imageCount = 3)
    private val folder = WallpaperSourceUi(5, WallpaperSourceType.FOLDER_TREE, "Holidays", enabled = true, needsReauth = false, imageCount = 12)

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
        setContent(WallpaperSettingsUiState(triggers = RotationTriggers(intervalMinutes = 180)))

        composeRule.onNodeWithText(plural(R.plurals.wallpaper_every_hours, 3)).assertIsDisplayed()
    }

    @Test
    fun theTriggerSwitchesReportTheirNewState() {
        setContent(WallpaperSettingsUiState(triggers = RotationTriggers(onUnlock = true)))

        composeRule.onNodeWithText(string(R.string.wallpaper_on_unlock)).performClick()
        composeRule.onNodeWithText(string(R.string.wallpaper_on_page_change)).performClick()

        assertEquals(listOf("unlock false", "page true"), calls)
    }

    @Test
    fun theLookSwitchesReportTheirNewState() {
        setContent(WallpaperSettingsUiState(changeLockScreen = true, parallax = true))

        clickRow(R.string.wallpaper_shuffle)
        clickRow(R.string.wallpaper_parallax)
        clickRow(R.string.wallpaper_lock_screen)

        assertEquals(listOf("shuffle true", "parallax false", "lock false"), calls)
    }

    @Test
    fun theCrossfadeIsSavedWhenSet() {
        setContent()

        composeRule.onNodeWithTag(WallpaperSettingsTestTags.LIST).performScrollToNode(hasText(string(R.string.wallpaper_crossfade)))
        composeRule.onNodeWithTag(WallpaperSettingsTestTags.CROSSFADE).performSemanticsAction(SemanticsActions.SetProgress) { it(1234f) }

        assertEquals(listOf("crossfade 1200"), calls, "snapped to whole 100 ms")
    }

    @Test
    fun nextNowAsksForTheNextWallpaper() {
        setContent()

        composeRule.onNodeWithText(string(R.string.wallpaper_next_now)).performClick()

        assertEquals(listOf("next"), calls)
    }

    @Test
    fun withoutTheLiveWallpaperApplyIsOffered() {
        setContent(WallpaperSettingsUiState(liveWallpaperActive = false))

        composeRule.onNodeWithText(string(R.string.wallpaper_apply)).performClick()

        assertEquals(listOf("apply"), calls)
    }

    @Test
    fun withTheLiveWallpaperThereIsNothingToApply() {
        setContent(WallpaperSettingsUiState(liveWallpaperActive = true))

        composeRule.onNodeWithText(string(R.string.wallpaper_apply)).assertDoesNotExist()
    }

    @Test
    fun sourcesCanBeTurnedOffAndAdded() {
        setContent(WallpaperSettingsUiState(sources = listOf(builtIn, folder)))

        clickRow(R.string.wallpaper_source_built_in)
        clickRow(R.string.wallpaper_add_folder)
        clickRow(R.string.wallpaper_add_pictures)

        assertEquals(listOf("source 1 false", "add folder", "add pictures"), calls)
    }

    @Test
    fun theLastEnabledSourceCannotBeTurnedOff() {
        setContent(WallpaperSettingsUiState(sources = listOf(builtIn, folder.copy(enabled = false))))

        scrollTo(R.string.wallpaper_source_built_in)
        composeRule.onNodeWithText(string(R.string.wallpaper_source_built_in)).assertIsNotEnabled()
    }

    @Test
    fun removingAFolderAsksFirstAndTheBuiltInPicturesCannotBeRemoved() {
        setContent(WallpaperSettingsUiState(sources = listOf(builtIn, folder)))

        composeRule.onNodeWithTag(WallpaperSettingsTestTags.LIST)
            .performScrollToNode(hasContentDescription(string(R.string.wallpaper_source_remove, "Holidays")))
        composeRule.onNodeWithContentDescription(string(R.string.wallpaper_source_remove, string(R.string.wallpaper_source_built_in)))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.wallpaper_source_remove, "Holidays")).performClick()
        assertEquals(emptyList(), calls)
        composeRule.onNodeWithText(string(R.string.wallpaper_remove)).performClick()

        assertEquals(listOf("remove 5"), calls)
    }

    @Test
    fun aFolderThatLostItsAccessIsPickedAgain() {
        setContent(WallpaperSettingsUiState(sources = listOf(builtIn, folder.copy(needsReauth = true))))

        clickRow(R.string.wallpaper_source_reauth)

        assertEquals(listOf("reauthorize 5"), calls)
    }

    @Test
    fun tappingAWallpaperShowsItAndTheOneShowingIsMarked() {
        val entries = listOf(WallpaperEntryUi(7, "content://x/7", "Lake", failing = false), WallpaperEntryUi(8, "content://x/8", "Hill", failing = false))
        setContent(WallpaperSettingsUiState(sources = listOf(builtIn), entries = entries, showingId = 7))

        composeRule.onNodeWithTag(WallpaperSettingsTestTags.LIST).performScrollToNode(hasContentDescription("Hill"))
        composeRule.onNodeWithContentDescription("Lake").assertIsSelected()
        composeRule.onNodeWithContentDescription("Hill").performClick()

        assertEquals(listOf("show 8"), calls)
    }

    @Test
    fun withNoPicturesTheGridSaysWhy() {
        setContent(WallpaperSettingsUiState(sources = listOf(builtIn.copy(imageCount = 0))))

        scrollTo(R.string.wallpaper_pictures_empty)
        composeRule.onNodeWithText(string(R.string.wallpaper_pictures_empty)).assertIsDisplayed()
    }

    private fun setContent(state: WallpaperSettingsUiState = WallpaperSettingsUiState()) {
        composeRule.setContent {
            FoxTheme { WallpaperSettingsContent(state = state, actions = RecordingActions()) }
        }
    }

    private fun scrollTo(text: Int) {
        composeRule.onNodeWithTag(WallpaperSettingsTestTags.LIST).performScrollToNode(hasText(string(text)))
    }

    private fun clickRow(text: Int) {
        scrollTo(text)
        composeRule.onNodeWithText(string(text)).performClick()
    }

    private fun string(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private fun plural(id: Int, n: Int) = composeRule.activity.resources.getQuantityString(id, n, n)

    private inner class RecordingActions : WallpaperSettingsActions {
        override fun back() { calls += "back" }
        override fun applyLiveWallpaper() { calls += "apply" }
        override fun nextNow() { calls += "next" }
        override fun show(entryId: Long) { calls += "show $entryId" }
        override fun setInterval(minutes: Int) { calls += "interval $minutes" }
        override fun setOnUnlock(enabled: Boolean) { calls += "unlock $enabled" }
        override fun setOnPageChange(enabled: Boolean) { calls += "page $enabled" }
        override fun setShuffle(enabled: Boolean) { calls += "shuffle $enabled" }
        override fun setCrossfadeMs(ms: Int) { calls += "crossfade $ms" }
        override fun setParallax(enabled: Boolean) { calls += "parallax $enabled" }
        override fun setChangeLockScreen(enabled: Boolean) { calls += "lock $enabled" }
        override fun addFolder() { calls += "add folder" }
        override fun addPictures() { calls += "add pictures" }
        override fun setSourceEnabled(sourceId: Long, enabled: Boolean) { calls += "source $sourceId $enabled" }
        override fun removeSource(sourceId: Long) { calls += "remove $sourceId" }
        override fun reauthorize(sourceId: Long) { calls += "reauthorize $sourceId" }
    }
}
