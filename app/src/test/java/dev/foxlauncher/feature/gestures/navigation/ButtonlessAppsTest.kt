package dev.foxlauncher.feature.gestures.navigation

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ButtonlessAppsTest {

    private val keys = setOf("com.example.game/com.example.game.Main|0", "org.video/.Player|10")

    @Test
    fun anAppIsListedByPackageAndProfile() {
        assertTrue(ButtonlessApps.isListed(keys, "com.example.game", 0))
        assertTrue(ButtonlessApps.isListed(keys, "org.video", 10))
    }

    @Test
    fun anotherProfileOrAPackageSharingAPrefixIsNot() {
        assertFalse(ButtonlessApps.isListed(keys, "com.example.game", 10))
        assertFalse(ButtonlessApps.isListed(keys, "com.example", 0))
        assertFalse(ButtonlessApps.isListed(keys, "com.example.gamepad", 0))
        assertFalse(ButtonlessApps.isListed(emptySet(), "com.example.game", 0))
    }

    @Test
    fun aMalformedKeyMatchesNothing() {
        assertFalse(ButtonlessApps.isListed(setOf("com.example.game/.Main"), "com.example.game", 0))
        assertFalse(ButtonlessApps.isListed(setOf("com.example.game/.Main|x"), "com.example.game", 0))
    }

    @Test
    fun openingHidesOnlyAListedAppsButtonsThatShow() {
        assertTrue(ButtonlessApps.hideBeforeOpening(listed = true, hiddenNow = false))
        assertFalse(ButtonlessApps.hideBeforeOpening(listed = true, hiddenNow = true))
        assertFalse(ButtonlessApps.hideBeforeOpening(listed = true, hiddenNow = null))
        assertFalse(ButtonlessApps.hideBeforeOpening(listed = false, hiddenNow = false))
    }

    @Test
    fun homeShowsThemOnlyWhenFoxHidThem() {
        assertTrue(ButtonlessApps.showOnHome(hiddenByFox = true, hiddenNow = true))
        assertTrue(ButtonlessApps.showOnHome(hiddenByFox = true, hiddenNow = null))
        assertFalse(ButtonlessApps.showOnHome(hiddenByFox = true, hiddenNow = false))
        assertFalse(ButtonlessApps.showOnHome(hiddenByFox = false, hiddenNow = true))
    }
}
