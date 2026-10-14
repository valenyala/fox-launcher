package dev.foxlauncher.feature.gestures.navigation

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ButtonAppsTest {

    private val keys = setOf("com.example.game/com.example.game.Main|0", "org.video/.Player|10")

    @Test
    fun anAppIsListedByPackageAndProfile() {
        assertTrue(ButtonApps.isListed(keys, "com.example.game", 0))
        assertTrue(ButtonApps.isListed(keys, "org.video", 10))
    }

    @Test
    fun anotherProfileOrAPackageSharingAPrefixIsNot() {
        assertFalse(ButtonApps.isListed(keys, "com.example.game", 10))
        assertFalse(ButtonApps.isListed(keys, "com.example", 0))
        assertFalse(ButtonApps.isListed(keys, "com.example.gamepad", 0))
        assertFalse(ButtonApps.isListed(emptySet(), "com.example.game", 0))
    }

    @Test
    fun aMalformedKeyMatchesNothing() {
        assertFalse(ButtonApps.isListed(setOf("com.example.game/.Main"), "com.example.game", 0))
        assertFalse(ButtonApps.isListed(setOf("com.example.game/.Main|x"), "com.example.game", 0))
    }

    @Test
    fun openingShowsOnlyAListedAppsButtonsThatAreHidden() {
        assertTrue(ButtonApps.showBeforeOpening(listed = true, hiddenNow = true))
        assertFalse(ButtonApps.showBeforeOpening(listed = true, hiddenNow = false))
        assertFalse(ButtonApps.showBeforeOpening(listed = true, hiddenNow = null))
        assertFalse(ButtonApps.showBeforeOpening(listed = false, hiddenNow = true))
    }

    @Test
    fun homeHidesThemOnlyWhenFoxShowedThem() {
        assertTrue(ButtonApps.hideOnHome(shownByFox = true, hiddenNow = false))
        assertTrue(ButtonApps.hideOnHome(shownByFox = true, hiddenNow = null))
        assertFalse(ButtonApps.hideOnHome(shownByFox = true, hiddenNow = true))
        assertFalse(ButtonApps.hideOnHome(shownByFox = false, hiddenNow = false))
    }
}
