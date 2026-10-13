package dev.foxlauncher.feature.memory

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class BoostTargetsTest {

    private val launchable = listOf(
        "dev.foxlauncher", "com.maps", "com.chat", "com.keyboard", "com.dialer", "com.sms",
        "com.reader", "com.wallpaper", "com.android.settings", "com.game", "com.chat",
    )
    private val protected = ProtectedApps(
        self = "dev.foxlauncher",
        foreground = "com.game",
        inputMethod = "com.keyboard",
        dialer = "com.dialer",
        sms = "com.sms",
        accessibility = setOf("com.reader"),
        liveWallpaper = "com.wallpaper",
    )

    @Test
    fun onlyUnprotectedThirdPartyAppsAreClosedEachOnce() {
        val targets = BoostTargets.choose(launchable, systemPackages = setOf("com.android.settings"), protectedApps = protected)

        assertEquals(listOf("com.chat", "com.maps"), targets)
    }

    @Test
    fun foxIsNeverClosedEvenWithNothingElseKnown() {
        val targets = BoostTargets.choose(listOf("dev.foxlauncher", "com.maps"), emptySet(), ProtectedApps(self = "dev.foxlauncher"))

        assertEquals(listOf("com.maps"), targets)
    }

    @Test
    fun aComponentGivesItsPackage() {
        assertEquals("com.google.android.inputmethod.latin", BoostTargets.packageOfComponent("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"))
        assertNull(BoostTargets.packageOfComponent(null))
        assertNull(BoostTargets.packageOfComponent(""))
        assertNull(BoostTargets.packageOfComponent("no-slash"))
    }

    @Test
    fun theAccessibilitySettingListsItsPackages() {
        val setting = "dev.foxlauncher/dev.foxlauncher.feature.gestures.system.FoxAccessibilityService:com.reader/.Service"

        assertEquals(setOf("dev.foxlauncher", "com.reader"), BoostTargets.accessibilityPackages(setting))
        assertEquals(emptySet(), BoostTargets.accessibilityPackages(null))
        assertEquals(emptySet(), BoostTargets.accessibilityPackages(""))
    }

    @Test
    fun upToAndroid13BothStrategiesRunAndFromAndroid14OnlyTheSelfTrim() {
        assertEquals(listOf("self", "others"), BoostStrategies.forSdk(29, "self", "others"))
        assertEquals(listOf("self", "others"), BoostStrategies.forSdk(33, "self", "others"))
        assertEquals(listOf("self"), BoostStrategies.forSdk(34, "self", "others"))
        assertEquals(listOf("self"), BoostStrategies.forSdk(37, "self", "others"))
    }
}
