package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.feature.wallpaper.StaticTarget.HOME
import dev.foxlauncher.feature.wallpaper.StaticTarget.LOCK
import kotlin.test.assertEquals
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
}
