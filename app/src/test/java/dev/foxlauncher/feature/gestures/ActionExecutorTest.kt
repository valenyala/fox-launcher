package dev.foxlauncher.feature.gestures

import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.feature.wallpaper.WallpaperAdvancer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ActionExecutorTest {

    private val system = FakeSystemActions()
    private val apps = FakeAppLauncher()
    private val wallpaper = FakeWallpaper()
    private val calls = mutableListOf<String>()
    private val context = ExecContext(
        openDrawer = { calls += "drawer" },
        openSearch = { calls += "search" },
        scrollToDefaultPage = { calls += "default page" },
        openPageOverview = { calls += "overview" },
        openHomeSettings = { calls += "home settings" },
    )
    private val executor = ActionExecutor(system, apps, wallpaper)

    @Test
    fun noneDoesNothing() {
        assertEquals(ActionResult.NOTHING, executor.execute(GestureAction.None, context))
        assertTrue(calls.isEmpty() && system.performed.isEmpty())
    }

    @Test
    fun homeActionsGoThroughTheContext() {
        val actions = listOf(
            GestureAction.OpenDrawer, GestureAction.OpenSearch, GestureAction.ScrollToDefaultPage,
            GestureAction.PageOverview, GestureAction.HomeSettings,
        )

        val results = actions.map { executor.execute(it, context) }

        assertTrue(results.all { it == ActionResult.DONE })
        assertEquals(listOf("drawer", "search", "default page", "overview", "home settings"), calls)
    }

    @Test
    fun eachSystemActionReachesTheService() {
        system.status = AccessibilityStatus.ON
        val expected = mapOf(
            GestureAction.Notifications to SystemAction.NOTIFICATIONS,
            GestureAction.QuickSettings to SystemAction.QUICK_SETTINGS,
            GestureAction.LockScreen to SystemAction.LOCK_SCREEN,
            GestureAction.Recents to SystemAction.RECENTS,
            GestureAction.PowerMenu to SystemAction.POWER_MENU,
            GestureAction.Screenshot to SystemAction.SCREENSHOT,
        )

        val results = expected.keys.map { executor.execute(it, context) }

        assertTrue(results.all { it == ActionResult.DONE })
        assertEquals(expected.values.toList(), system.performed)
    }

    @Test
    fun aSystemActionWithTheServiceOffAsksForItAndPerformsNothing() {
        system.status = AccessibilityStatus.OFF

        assertEquals(ActionResult.NEEDS_ACCESSIBILITY, executor.execute(GestureAction.Notifications, context))
        assertTrue(system.performed.isEmpty())
    }

    @Test
    fun aSystemActionWithTheServiceStoppedSaysSoAndPerformsNothing() {
        // Turned on in the settings, but the system stopped it (MIUI clearing recent apps).
        system.status = AccessibilityStatus.STOPPED

        assertEquals(ActionResult.ACCESSIBILITY_STOPPED, executor.execute(GestureAction.Notifications, context))
        assertTrue(system.performed.isEmpty())
    }

    @Test
    fun aRunningServiceTheSystemRefusesFails() {
        system.status = AccessibilityStatus.ON
        system.accepts = false

        assertEquals(ActionResult.FAILED, executor.execute(GestureAction.LockScreen, context))
    }

    @Test
    fun everyActionThatNeedsAccessibilityIsASystemAction() {
        system.status = AccessibilityStatus.OFF
        val all = listOf(
            GestureAction.None, GestureAction.OpenDrawer, GestureAction.OpenSearch, GestureAction.Notifications,
            GestureAction.QuickSettings, GestureAction.LockScreen, GestureAction.Recents, GestureAction.PowerMenu,
            GestureAction.Screenshot, GestureAction.NextWallpaper, GestureAction.PreviousWallpaper,
            GestureAction.HomeSettings, GestureAction.PageOverview, GestureAction.ScrollToDefaultPage,
            GestureAction.OpenApp("a/.B", 0), GestureAction.OpenShortcut("a", "s", 0),
        )

        all.forEach { action ->
            val asks = executor.execute(action, context) == ActionResult.NEEDS_ACCESSIBILITY
            assertEquals(action.requiresAccessibility, asks, "$action")
        }
    }

    @Test
    fun openAppLaunchesItsComponentInItsProfile() {
        val result = executor.execute(GestureAction.OpenApp("com.android.camera/.Camera", 10), context)

        assertEquals(ActionResult.DONE, result)
        assertEquals(listOf("com.android.camera/.Camera|10"), apps.launched)
    }

    @Test
    fun anAppThatIsGoneFails() {
        apps.installed = false

        assertEquals(ActionResult.FAILED, executor.execute(GestureAction.OpenApp("com.example.gone/.Main", 0), context))
    }

    @Test
    fun openShortcutStartsIt() {
        executor.execute(GestureAction.OpenShortcut("com.android.chrome", "incognito", 0), context)

        assertEquals(listOf("com.android.chrome/incognito|0"), apps.launched)
    }

    @Test
    fun wallpaperActionsAdvanceTheRotation() {
        assertEquals(ActionResult.DONE, executor.execute(GestureAction.NextWallpaper, context))
        assertEquals(ActionResult.DONE, executor.execute(GestureAction.PreviousWallpaper, context))

        assertEquals(listOf(true, false), wallpaper.moves)
    }

    @Test
    fun withoutTheLiveWallpaperAWallpaperGestureOffersItAndChangesNothing() {
        wallpaper.liveWallpaperApplied = false

        assertEquals(ActionResult.NEEDS_LIVE_WALLPAPER, executor.execute(GestureAction.NextWallpaper, context))
        assertTrue(wallpaper.moves.isEmpty())
    }

    private class FakeSystemActions : SystemActions {
        override var status = AccessibilityStatus.ON
        var accepts = true
        val performed = mutableListOf<SystemAction>()

        override fun perform(action: SystemAction): Boolean {
            if (accepts) performed += action
            return accepts
        }
    }

    private class FakeAppLauncher : AppLauncher {
        var installed = true
        val launched = mutableListOf<String>()

        override fun launchApp(component: String, userSerial: Long): Boolean {
            if (installed) launched += "$component|$userSerial"
            return installed
        }

        override fun startShortcut(packageName: String, shortcutId: String, userSerial: Long): Boolean {
            launched += "$packageName/$shortcutId|$userSerial"
            return true
        }
    }

    private class FakeWallpaper : WallpaperAdvancer {
        var liveWallpaperApplied = true
        val moves = mutableListOf<Boolean>()

        override fun advance(forward: Boolean): Boolean {
            if (liveWallpaperApplied) moves += forward
            return liveWallpaperApplied
        }
    }
}
