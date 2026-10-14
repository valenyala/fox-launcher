package dev.foxlauncher.feature.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.R
import dev.foxlauncher.core.model.WallpaperFraming
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The framing editor (ADR-26): the picture follows the fingers, Save keeps it, Reset and Cancel undo. */
@RunWith(AndroidJUnit4::class)
class WallpaperFramingEditorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var saved: WallpaperFraming? = null
    private var cancelled = false

    /** Landscape: on a portrait screen a strip of its width shows, so it moves sideways but not up and down. */
    private val picture = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }

    @Test
    fun draggingLeftShowsMoreOfTheRightAndSaveKeepsIt() {
        setContent()

        composeRule.onNodeWithTag(WallpaperFramingTestTags.PICTURE).performTouchInput { swipeLeft() }
        click(R.string.wallpaper_framing_save)

        val framing = checkNotNull(saved)
        assertTrue(framing.focusX > 0.5f, "$framing")
        assertEquals(0.5f, framing.focusY)
        assertEquals(1f, framing.zoom)
    }

    @Test
    fun pinchingOutZoomsIn() {
        setContent()

        composeRule.onNodeWithTag(WallpaperFramingTestTags.PICTURE).performTouchInput {
            pinch(center - Offset(10f, 10f), center - Offset(200f, 200f), center + Offset(10f, 10f), center + Offset(200f, 200f))
        }
        click(R.string.wallpaper_framing_save)

        assertTrue(checkNotNull(saved).zoom > 1f, "$saved")
    }

    @Test
    fun resetCentresThePictureAgain() {
        setContent(WallpaperFraming(focusX = 0.1f, zoom = 2f))

        click(R.string.wallpaper_framing_reset)
        click(R.string.wallpaper_framing_save)

        assertEquals(WallpaperFraming.CENTERED, saved)
    }

    @Test
    fun cancelLeavesItAsItWas() {
        setContent()

        composeRule.onNodeWithTag(WallpaperFramingTestTags.PICTURE).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText(composeRule.activity.getString(android.R.string.cancel)).performClick()

        assertTrue(cancelled)
        assertNull(saved)
    }

    private fun setContent(initial: WallpaperFraming = WallpaperFraming.CENTERED) {
        composeRule.setContent {
            FoxTheme {
                WallpaperFramingEditor(picture, initial, onSave = { saved = it }, onCancel = { cancelled = true })
            }
        }
        // The picture loads through Coil first.
        composeRule.waitUntil(LOAD_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(WallpaperFramingTestTags.PICTURE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(text: Int) {
        composeRule.onNodeWithText(composeRule.activity.getString(text)).performClick()
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
