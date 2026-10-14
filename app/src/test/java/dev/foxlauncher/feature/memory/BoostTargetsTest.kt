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
    fun shizukuItselfIsNeverClosed() {
        val targets = BoostTargets.choose(
            listOf("moe.shizuku.privileged.api", "com.maps"),
            emptySet(),
            ProtectedApps(self = "dev.foxlauncher", shizuku = "moe.shizuku.privileged.api"),
        )

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
    fun withoutALimitBothStrategiesRunAndWithOneOnlyTheSelfTrim() {
        assertEquals(listOf("self", "others"), BoostStrategies.choose(null, deepActive = false, "self", "others", "deep"))
        assertEquals(listOf("self"), BoostStrategies.choose(BoostLimit.ANDROID_14, deepActive = false, "self", "others", "deep"))
        assertEquals(listOf("self"), BoostStrategies.choose(BoostLimit.MIUI, deepActive = false, "self", "others", "deep"))
    }

    @Test
    fun anActiveDeepBoostRunsFirstWhateverTheLimit() {
        for (limit in listOf(null, BoostLimit.ANDROID_14, BoostLimit.MIUI)) {
            assertEquals(listOf("deep", "self"), BoostStrategies.choose(limit, deepActive = true, "self", "others", "deep"), "$limit")
        }
    }

    @Test
    fun anActiveDeepBoostLiftsTheLimit() {
        assertEquals(null, BoostStrategies.limit(BoostLimit.MIUI, deepActive = true))
        assertEquals(BoostLimit.MIUI, BoostStrategies.limit(BoostLimit.MIUI, deepActive = false))
        assertEquals(null, BoostStrategies.limit(null, deepActive = false))
    }
}
