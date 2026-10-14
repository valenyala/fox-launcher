package dev.foxlauncher.core.data.settings

import dev.foxlauncher.core.model.DEFAULT_GESTURE_MAP
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.core.model.MacroStep
import dev.foxlauncher.core.model.Macros
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GestureMapJsonTest {

    @Test
    fun theFullDefaultMapSurvivesARoundTrip() {
        val stored = GestureMapJson.encodeChanges(DEFAULT_GESTURE_MAP)

        assertEquals(DEFAULT_GESTURE_MAP, GestureMapJson.decodeChanges(stored))
    }

    @Test
    fun everyActionSurvivesARoundTrip() {
        val actions = listOf(
            GestureAction.None, GestureAction.OpenDrawer, GestureAction.OpenSearch, GestureAction.Notifications,
            GestureAction.QuickSettings, GestureAction.LockScreen, GestureAction.Recents, GestureAction.PowerMenu,
            GestureAction.Screenshot, GestureAction.NextWallpaper, GestureAction.PreviousWallpaper,
            GestureAction.HomeSettings, GestureAction.PageOverview, GestureAction.ScrollToDefaultPage,
            GestureAction.OpenApp("com.android.camera/.Camera", 0),
            GestureAction.OpenShortcut("com.android.chrome", "new_incognito_tab", 10, label = "New Incognito tab"),
        )
        // One gesture per action; there are more gestures than actions.
        val changes = GestureType.entries.zip(actions).toMap()

        assertEquals(changes, GestureMapJson.decodeChanges(GestureMapJson.encodeChanges(changes)))
    }

    @Test
    fun nothingStoredMeansTheDefaults() {
        assertEquals(DEFAULT_GESTURE_MAP, GestureMapJson.decode(null))
        assertEquals(DEFAULT_GESTURE_MAP, GestureMapJson.decode(""))
    }

    @Test
    fun aStoredChangeOverridesOnlyItsGesture() {
        val camera = GestureAction.OpenApp("com.android.camera/.Camera", 0)
        val stored = GestureMapJson.encodeChanges(mapOf(GestureType.DOUBLE_TAP to camera))

        assertEquals(DEFAULT_GESTURE_MAP + (GestureType.DOUBLE_TAP to camera), GestureMapJson.decode(stored))
    }

    @Test
    fun anUnknownActionFallsBackToNone() {
        val stored = """{"DOUBLE_TAP":{"type":"teleport","where":"moon"},"SWIPE_UP":{"type":"open_search"}}"""

        val decoded = GestureMapJson.decode(stored)

        assertEquals(GestureAction.None, decoded[GestureType.DOUBLE_TAP])
        assertEquals(GestureAction.OpenSearch, decoded[GestureType.SWIPE_UP])
    }

    @Test
    fun anActionMissingAFieldFallsBackToNone() {
        val stored = """{"DOUBLE_TAP":{"type":"open_app","component":"com.android.camera/.Camera"}}"""

        assertEquals(GestureAction.None, GestureMapJson.decode(stored)[GestureType.DOUBLE_TAP])
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val stored = """{"DOUBLE_TAP":{"type":"open_app","component":"a/.B","userSerial":0,"addedIn":"v9"}}"""

        assertEquals(GestureAction.OpenApp("a/.B", 0), GestureMapJson.decode(stored)[GestureType.DOUBLE_TAP])
    }

    @Test
    fun aShortcutStoredBeforeItHadALabelStillReads() {
        val stored = """{"PINCH_OUT":{"type":"open_shortcut","packageName":"a","shortcutId":"s","userSerial":0}}"""

        assertEquals(GestureAction.OpenShortcut("a", "s", 0, label = ""), GestureMapJson.decode(stored)[GestureType.PINCH_OUT])
    }

    @Test
    fun anUnknownGestureIsDropped() {
        val stored = """{"TRIPLE_TAP":{"type":"lock_screen"},"PINCH_OUT":{"type":"recents"}}"""

        assertEquals(mapOf(GestureType.PINCH_OUT to GestureAction.Recents), GestureMapJson.decodeChanges(stored))
    }

    @Test
    fun unreadableJsonMeansTheDefaults() {
        assertEquals(DEFAULT_GESTURE_MAP, GestureMapJson.decode("{not json"))
        assertEquals(DEFAULT_GESTURE_MAP, GestureMapJson.decode("""["SWIPE_UP"]"""))
    }

    @Test
    fun theStoredFormUsesTheTypeDiscriminator() {
        val stored = GestureMapJson.encodeChanges(mapOf(GestureType.SWIPE_DOWN to GestureAction.QuickSettings))

        assertEquals("""{"SWIPE_DOWN":{"type":"quick_settings"}}""", stored)
    }

    @Test
    fun defaultsFollowThePlan() {
        assertEquals(GestureType.entries.toSet(), DEFAULT_GESTURE_MAP.keys)
        assertEquals(GestureAction.OpenDrawer, DEFAULT_GESTURE_MAP[GestureType.SWIPE_UP])
        assertEquals(GestureAction.Notifications, DEFAULT_GESTURE_MAP[GestureType.SWIPE_DOWN])
        assertEquals(GestureAction.QuickSettings, DEFAULT_GESTURE_MAP[GestureType.SWIPE_DOWN_2F])
        assertEquals(GestureAction.LockScreen, DEFAULT_GESTURE_MAP[GestureType.DOUBLE_TAP])
        assertEquals(GestureAction.HomeSettings, DEFAULT_GESTURE_MAP[GestureType.LONG_PRESS_EMPTY])
        assertEquals(GestureAction.PageOverview, DEFAULT_GESTURE_MAP[GestureType.PINCH_IN])
        assertEquals(GestureAction.NextWallpaper, DEFAULT_GESTURE_MAP[GestureType.SWIPE_UP_2F])
        assertEquals(GestureAction.OpenDrawer, DEFAULT_GESTURE_MAP[GestureType.DOCK_SWIPE_UP])
        assertEquals(GestureAction.ScrollToDefaultPage, DEFAULT_GESTURE_MAP[GestureType.HOME_KEY])
        val unset = GestureType.entries - setOf(
            GestureType.SWIPE_UP, GestureType.SWIPE_DOWN, GestureType.SWIPE_DOWN_2F, GestureType.DOUBLE_TAP,
            GestureType.LONG_PRESS_EMPTY, GestureType.PINCH_IN, GestureType.SWIPE_UP_2F, GestureType.DOCK_SWIPE_UP,
            GestureType.HOME_KEY,
        )
        assertTrue(unset.all { DEFAULT_GESTURE_MAP[it] == GestureAction.None })
    }

    @Test
    fun onlySystemActionsNeedTheAccessibilityService() {
        val system = setOf(
            GestureAction.Notifications, GestureAction.QuickSettings, GestureAction.LockScreen,
            GestureAction.Recents, GestureAction.PowerMenu, GestureAction.Screenshot,
        )
        val others = listOf(
            GestureAction.None, GestureAction.OpenDrawer, GestureAction.OpenSearch, GestureAction.NextWallpaper,
            GestureAction.PreviousWallpaper, GestureAction.HomeSettings, GestureAction.PageOverview,
            GestureAction.ScrollToDefaultPage, GestureAction.OpenApp("a/.B", 0), GestureAction.OpenShortcut("a", "s", 0),
        )

        assertTrue(system.all { it.requiresAccessibility })
        assertTrue(others.none { it.requiresAccessibility })
    }

    // --- macros and pages (task 9.3) ---

    private val macro = GestureAction.Macro(
        listOf(MacroStep(GestureAction.NextWallpaper), MacroStep(GestureAction.OpenApp("com.maps/.Main", 0), delayMs = 1_000)),
    )

    @Test
    fun aMacroSurvivesARoundTrip() {
        val changes = mapOf(GestureType.PINCH_OUT to macro)

        assertEquals(changes, GestureMapJson.decodeChanges(GestureMapJson.encodeChanges(changes)))
    }

    @Test
    fun aStoredMacroIsCleanedUpOnReading() {
        // As a newer or hand-edited store could hold: a nested macro and an out-of-range wait.
        val stored = """{"PINCH_OUT":{"type":"macro","steps":[{"action":{"type":"macro","steps":[]}},{"action":{"type":"lock_screen"},"delayMs":99999}]}}"""

        assertEquals(
            GestureAction.Macro(listOf(MacroStep(GestureAction.LockScreen, delayMs = Macros.MAX_DELAY_MS))),
            GestureMapJson.decodeChanges(stored)[GestureType.PINCH_OUT],
        )
    }

    @Test
    fun pagesSurviveARoundTrip() {
        val pages = mapOf(
            0 to mapOf(GestureType.DOUBLE_TAP to GestureAction.NextWallpaper),
            2 to mapOf(GestureType.SWIPE_DOWN to GestureAction.QuickSettings, GestureType.PINCH_OUT to macro),
        )

        assertEquals(pages, GestureMapJson.decodePages(GestureMapJson.encodePages(pages)))
    }

    @Test
    fun pagesForgiveWhatTheyCannotRead() {
        assertEquals(emptyMap(), GestureMapJson.decodePages(null))
        assertEquals(emptyMap(), GestureMapJson.decodePages("not json"))
        assertEquals(
            mapOf(1 to mapOf(GestureType.DOUBLE_TAP to GestureAction.LockScreen)),
            GestureMapJson.decodePages("""{"x":{"DOUBLE_TAP":{"type":"lock_screen"}},"-1":{},"1":{"DOUBLE_TAP":{"type":"lock_screen"},"NO_SUCH":{}},"2":{}}"""),
            "a key that is no page, a page with nothing readable, an unknown gesture",
        )
    }

    @Test
    fun emptyPagesAreNotStored() {
        assertEquals("{}", GestureMapJson.encodePages(mapOf(3 to emptyMap())))
    }
}
