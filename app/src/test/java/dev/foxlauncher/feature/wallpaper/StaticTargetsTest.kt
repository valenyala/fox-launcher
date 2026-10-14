package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.feature.wallpaper.StaticTarget.HOME
import dev.foxlauncher.feature.wallpaper.StaticTarget.LOCK
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class StaticTargetsTest {

    @Test
    fun theLiveWallpaperDrawsTheHomeScreenItself() {
        assertEquals(emptySet(), StaticTargets.of(foxLiveActive = true, changeLockScreen = false))
    }

    @Test
    fun withoutTheLiveWallpaperTheHomeScreenGetsAStillImage() {
        assertEquals(setOf(HOME), StaticTargets.of(foxLiveActive = false, changeLockScreen = false))
    }

    @Test
    fun theLockScreenChangesOnlyWhenAskedFor() {
        assertEquals(setOf(LOCK), StaticTargets.of(foxLiveActive = true, changeLockScreen = true))
        assertEquals(setOf(HOME, LOCK), StaticTargets.of(foxLiveActive = false, changeLockScreen = true))
    }

    @Test
    fun catchingUpTheLockScreenLeavesTheHomeScreenAlone() {
        assertEquals(setOf(LOCK), StaticTargets.of(foxLiveActive = false, changeLockScreen = true, lockOnly = true))
        assertEquals(emptySet(), StaticTargets.of(foxLiveActive = false, changeLockScreen = false, lockOnly = true))
    }

    @Test
    fun theLockScreenAloneWaitsForTheScreenToGoOff() {
        assertTrue(StaticTargets.waitsForScreenOff(setOf(LOCK)))
    }

    @Test
    fun aHomeScreenImageIsSetAtOnce() {
        assertFalse(StaticTargets.waitsForScreenOff(setOf(HOME)))
        assertFalse(StaticTargets.waitsForScreenOff(setOf(HOME, LOCK)))
    }

    @Test
    fun theLockScreenIsBehindUntilItShowsTheEntryOnScreen() {
        assertTrue(StaticTargets.lockScreenBehind(changeLockScreen = true, lockEntry = 3L, showing = 5L))
        assertTrue(StaticTargets.lockScreenBehind(changeLockScreen = true, lockEntry = null, showing = 5L))
        assertFalse(StaticTargets.lockScreenBehind(changeLockScreen = true, lockEntry = 5L, showing = 5L))
    }

    @Test
    fun theLockScreenIsLeftAloneWhenNotAskedForOrWithNothingToShow() {
        assertFalse(StaticTargets.lockScreenBehind(changeLockScreen = false, lockEntry = 3L, showing = 5L))
        assertFalse(StaticTargets.lockScreenBehind(changeLockScreen = true, lockEntry = 3L, showing = null))
    }
}
