package dev.foxlauncher.feature.wallpaper

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class StaticTargetsTest {

    @Test
    fun lockScreenIsBehindWhenItShowsAnotherEntry() {
        assertTrue(StaticTargets.lockScreenBehind(lockEntry = 3L, wanted = 5L))
    }

    @Test
    fun lockScreenIsBehindBeforeAnythingWasSet() {
        assertTrue(StaticTargets.lockScreenBehind(lockEntry = null, wanted = 5L))
    }

    @Test
    fun lockScreenIsNotBehindWhenItShowsTheWantedEntry() {
        assertFalse(StaticTargets.lockScreenBehind(lockEntry = 5L, wanted = 5L))
    }

    @Test
    fun lockScreenIsLeftAloneWhenNothingIsWanted() {
        assertFalse(StaticTargets.lockScreenBehind(lockEntry = 3L, wanted = null))
    }
}
