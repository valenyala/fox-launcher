package dev.foxlauncher.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.AppShortcut
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.system.ACCESSIBILITY_DISCLOSURE_TAG
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The gesture settings screen (task 3.5) against a fake settings store. */
@RunWith(AndroidJUnit4::class)
class GesturesContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val camera = app("Camera", "com.android.camera/.Camera")
    private val chrome = app("Chrome", "com.android.chrome/com.google.android.apps.chrome.Main")
    private val incognito = AppShortcut("com.android.chrome", "incognito", 0, "Incognito", "New Incognito tab")

    /** The fake settings store: what each gesture is set to. */
    private val store = mutableMapOf(
        GestureType.DOUBLE_TAP to GestureAction.None,
        GestureType.PINCH_OUT to GestureAction.None,
        GestureType.SWIPE_DOWN to GestureAction.Notifications,
    )
    private var resets = 0
    private var settingsOpened = 0
    private var restarts = 0

    @Test
    fun choosingLockScreenForDoubleTapWritesIt() {
        setContent()

        composeRule.onNodeWithText(string(R.string.gesture_double_tap)).performClick()
        composeRule.onNodeWithText(string(R.string.action_lock_screen)).performScrollTo().performClick()

        assertEquals(GestureAction.LockScreen, store[GestureType.DOUBLE_TAP])
        composeRule.onNodeWithTag(GesturesTestTags.ACTION_PICKER).assertDoesNotExist()
    }

    @Test
    fun anAppIsChosenFromTheAppList() {
        setContent()

        composeRule.onNodeWithText(string(R.string.gesture_pinch_out)).performClick()
        composeRule.onNodeWithText(string(R.string.action_open_app_pick)).performScrollTo().performClick()
        composeRule.onNodeWithText("Camera").performClick()

        assertEquals(GestureAction.OpenApp(camera.component, 0), store[GestureType.PINCH_OUT])
        composeRule.onNodeWithTag(GesturesTestTags.APP_CHOOSER).assertDoesNotExist()
    }

    @Test
    fun aShortcutIsChosenFromItsAppsShortcuts() {
        setContent()

        composeRule.onNodeWithText(string(R.string.gesture_pinch_out)).performClick()
        composeRule.onNodeWithText(string(R.string.action_open_shortcut_pick)).performScrollTo().performClick()
        composeRule.onNodeWithText("Chrome").performClick()
        composeRule.onNodeWithText("New Incognito tab").performClick()

        assertEquals(GestureAction.OpenShortcut("com.android.chrome", "incognito", 0, "Incognito"), store[GestureType.PINCH_OUT])
    }

    @Test
    fun rowsNameTheirActions() {
        store[GestureType.PINCH_OUT] = GestureAction.OpenApp(camera.component, 0)
        setContent()

        composeRule.onNodeWithText(string(R.string.action_notifications)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_open_app, "Camera")).assertIsDisplayed()
    }

    @Test
    fun resetAsksTheStoreToReset() {
        setContent()

        composeRule.onNodeWithText(string(R.string.gestures_reset)).performClick()

        assertEquals(1, resets)
    }

    @Test
    fun withTheServiceOffTurnOnShowsTheDisclosureFirst() {
        setContent(accessibility = AccessibilityStatus.OFF)

        composeRule.onNodeWithText(string(R.string.gestures_turn_on)).performClick()
        composeRule.onNodeWithText(string(R.string.accessibility_disclosure)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.accessibility_open_settings)).performClick()

        assertEquals(1, settingsOpened)
    }

    @Test
    fun withTheServiceOnThereIsNothingToTurnOn() {
        setContent(accessibility = AccessibilityStatus.ON)

        composeRule.onNodeWithText(string(R.string.gestures_turn_on)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.accessibility_open_settings)).assertDoesNotExist()
    }

    @Test
    fun withTheServiceStoppedItSaysToTurnItOffAndOn() {
        setContent(accessibility = AccessibilityStatus.STOPPED)

        composeRule.onNodeWithText(string(R.string.gestures_accessibility_stopped)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.accessibility_open_settings)).performClick()
        composeRule.onNodeWithText(string(R.string.accessibility_stopped)).assertIsDisplayed()
        // The banner has an "Open settings" too: press the dialog's.
        composeRule.onNode(
            hasText(string(R.string.accessibility_open_settings)) and hasAnyAncestor(hasTestTag(ACCESSIBILITY_DISCLOSURE_TAG)),
        ).performClick()

        assertEquals(1, settingsOpened)
    }

    @Test
    fun withTheGrantAStoppedServiceIsRestartedInPlace() {
        setContent(accessibility = AccessibilityStatus.STOPPED, autoRestart = true)

        composeRule.onNodeWithText(string(R.string.gestures_accessibility_stopped_restartable)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.gestures_restart)).performClick()

        assertEquals(1, restarts)
        assertEquals(0, settingsOpened)
        composeRule.onNodeWithText(string(R.string.accessibility_stopped)).assertDoesNotExist()
    }

    @Test
    fun withoutTheGrantTheAdbCommandIsShown() {
        setContent(autoRestart = false)

        composeRule.onNodeWithText(string(R.string.gestures_auto_restart_command)).assertIsDisplayed()
    }

    @Test
    fun withTheGrantAutomaticRestartIsOn() {
        setContent(autoRestart = true)

        composeRule.onNodeWithText(string(R.string.gestures_auto_restart_on)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.gestures_auto_restart_command)).assertDoesNotExist()
    }

    @Test
    fun theLockHintShowsOnlyWhereItApplies() {
        setContent(miuiLockHint = true)
        composeRule.onNodeWithText(string(R.string.gestures_miui_lock_hint)).assertIsDisplayed()
    }

    @Test
    fun noLockHintElsewhere() {
        setContent(miuiLockHint = false)
        composeRule.onNodeWithText(string(R.string.gestures_miui_lock_hint)).assertDoesNotExist()
    }

    private fun setContent(
        accessibility: AccessibilityStatus = AccessibilityStatus.ON,
        autoRestart: Boolean = false,
        miuiLockHint: Boolean = false,
    ) {
        composeRule.setContent {
            FoxTheme {
                // The store as it is when the screen opens; tests check the store, not a recomposed list.
                val rows = store.map { (type, action) -> GestureRow(type, action) }.toImmutableList()
                GesturesContent(
                    state = GesturesUiState(rows = rows, apps = persistentListOf(camera, chrome), accessibility = accessibility, autoRestart = autoRestart, showMiuiLockHint = miuiLockHint),
                    onSetGesture = { type, action -> store[type] = action },
                    onReset = { resets++ },
                    loadShortcuts = { entry -> if (entry == chrome) listOf(incognito) else emptyList() },
                    onOpenAccessibilitySettings = { settingsOpened++ },
                    onBack = {},
                    onRestartService = { restarts++ },
                    iconContent = { Box(Modifier.size(40.dp)) },
                )
            }
        }
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun app(label: String, component: String) = AppEntry(
        component = component,
        userSerial = 0,
        label = label,
        packageName = component.substringBefore('/'),
        isWorkProfile = false,
        isSuspended = false,
        isAvailable = true,
        versionCode = 1,
    )
}
