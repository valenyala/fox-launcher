package dev.foxlauncher.feature.home

import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomePosition
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DefaultLayoutBuilderTest {

    private val dialer = app("com.google.android.dialer/.extensions.GoogleDialtactsActivity")
    private val messages = app("com.google.android.apps.messaging/.ui.ConversationListActivity")
    private val brave = app("com.brave.browser/com.google.android.apps.chrome.Main")
    private val camera = app("com.android.camera/.Camera")
    private val catalog = listOf(brave, camera, dialer, messages)

    private val allFound = resolver(
        DefaultApp.DIALER to dialer.component,
        DefaultApp.SMS to messages.component,
        DefaultApp.BROWSER to brave.component,
        DefaultApp.CAMERA to camera.component,
    )

    @Test
    fun fourAppsFillAFiveSlotDockAroundAFreeMiddle() {
        val layout = DefaultLayoutBuilder.build(allFound, catalog, dockSlots = 5)

        assertEquals(listOf(dialer to dock(0), messages to dock(1), brave to dock(3), camera to dock(4)), layout)
    }

    @Test
    fun anEvenDockFillsFromTheLeft() {
        val layout = DefaultLayoutBuilder.build(allFound, catalog, dockSlots = 4)

        assertEquals(listOf(0, 1, 2, 3), layout.map { it.second.col })
    }

    @Test
    fun aWiderOddDockCentresTheApps() {
        assertEquals(listOf(1, 2, 4, 5), DefaultLayoutBuilder.dockSlots(7))
    }

    @Test
    fun aSmallDockTakesTheFirstApps() {
        val layout = DefaultLayoutBuilder.build(allFound, catalog, dockSlots = 3)

        assertEquals(listOf(dialer to dock(0), messages to dock(1), brave to dock(2)), layout)
    }

    @Test
    fun anUnresolvedRoleLeavesNoGap() {
        // A tablet: no dialer, no SMS app.
        val noPhone = resolver(DefaultApp.BROWSER to brave.component, DefaultApp.CAMERA to camera.component)

        val layout = DefaultLayoutBuilder.build(noPhone, catalog, dockSlots = 5)

        assertEquals(listOf(brave to dock(0), camera to dock(1)), layout)
    }

    @Test
    fun aComponentTheCatalogListsDifferentlyFallsBackToItsPackage() {
        // PackageManager names an activity alias; the catalog lists the target activity.
        val aliased = resolver(DefaultApp.DIALER to "com.google.android.dialer/.MainAlias")

        val layout = DefaultLayoutBuilder.build(aliased, catalog, dockSlots = 5)

        assertEquals(listOf(dialer to dock(0)), layout)
    }

    @Test
    fun anAppMissingFromTheCatalogIsSkipped() {
        val uninstalled = resolver(DefaultApp.DIALER to "com.example.gone/.Main", DefaultApp.SMS to messages.component)

        val layout = DefaultLayoutBuilder.build(uninstalled, catalog, dockSlots = 5)

        assertEquals(listOf(messages to dock(0)), layout)
    }

    @Test
    fun oneAppInTwoRolesIsPlacedOnce() {
        val both = resolver(DefaultApp.DIALER to dialer.component, DefaultApp.SMS to dialer.component)

        val layout = DefaultLayoutBuilder.build(both, catalog, dockSlots = 5)

        assertEquals(listOf(dialer to dock(0)), layout)
    }

    @Test
    fun workProfileAppsAreNeverPicked() {
        val workOnly = listOf(app(brave.component, serial = 10, work = true))
        val layout = DefaultLayoutBuilder.build(allFound, workOnly, dockSlots = 5)

        assertTrue(layout.isEmpty())
    }

    @Test
    fun thePersonalCopyWinsOverTheWorkOne() {
        val workBrave = app(brave.component, serial = 10, work = true)
        val layout = DefaultLayoutBuilder.build(resolver(DefaultApp.BROWSER to brave.component), listOf(workBrave, brave), dockSlots = 5)

        assertEquals(listOf(brave to dock(0)), layout)
    }

    @Test
    fun nothingResolvedGivesAnEmptyHome() {
        assertTrue(DefaultLayoutBuilder.build({ null }, catalog, dockSlots = 5).isEmpty())
    }

    private fun resolver(vararg found: Pair<DefaultApp, String>): DefaultAppResolver {
        val map = found.toMap()
        return DefaultAppResolver { map[it] }
    }

    private fun dock(slot: Int) = HomePosition(HomeContainer.DOCK, 0, slot, 0)

    private fun app(component: String, serial: Long = 0, work: Boolean = false) = AppEntry(
        component = component,
        userSerial = serial,
        label = component.substringAfterLast('.'),
        packageName = component.substringBefore('/'),
        isWorkProfile = work,
        isSuspended = false,
        isAvailable = true,
        versionCode = 1,
    )
}
