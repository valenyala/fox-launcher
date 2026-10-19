package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.model.WallpaperTarget.BOTH
import dev.foxlauncher.core.model.WallpaperTarget.HOME
import dev.foxlauncher.core.model.WallpaperTarget.LOCK
import org.junit.Test
import kotlin.test.assertEquals

class EntryTargetsTest {

    private val entries = listOf(1L to HOME, 2L to LOCK, 3L to BOTH, 4L to HOME)

    @Test
    fun homeTakesHomeAndBothInOrder() {
        assertEquals(listOf(1L, 3L, 4L), EntryTargets.home(entries))
    }

    @Test
    fun lockTakesLockAndBothInOrder() {
        assertEquals(listOf(2L, 3L), EntryTargets.lock(entries))
    }

    @Test
    fun homeFallsBackToEveryImageWhenNoneIsAimedAtIt() {
        assertEquals(listOf(2L, 5L), EntryTargets.home(listOf(2L to LOCK, 5L to LOCK)))
    }

    @Test
    fun lockIsEmptyWhenNoneIsAimedAtIt() {
        assertEquals(emptyList(), EntryTargets.lock(listOf(1L to HOME)))
    }

    @Test
    fun lockedOnTheLiveWallpaperShowsTheLockPicture() {
        val showing = EntryTargets.showing(locked = true, foxOnLock = true, homeActive = 1L, lockActive = 2L, entries = entries)
        assertEquals(EntryTargets.Showing(2L, lockPicture = true), showing)
    }

    @Test
    fun unlockedShowsTheHomePicture() {
        val showing = EntryTargets.showing(locked = false, foxOnLock = true, homeActive = 1L, lockActive = 2L, entries = entries)
        assertEquals(EntryTargets.Showing(1L, lockPicture = false), showing)
    }

    @Test
    fun lockedWithoutTheLiveWallpaperOnTheLockScreenShowsTheHomePicture() {
        val showing = EntryTargets.showing(locked = true, foxOnLock = false, homeActive = 1L, lockActive = 2L, entries = entries)
        assertEquals(EntryTargets.Showing(1L, lockPicture = false), showing)
    }

    @Test
    fun lockedWithNoLockPicturesShowsTheHomePicture() {
        val showing = EntryTargets.showing(locked = true, foxOnLock = true, homeActive = 1L, lockActive = null, entries = listOf(1L to HOME))
        assertEquals(EntryTargets.Showing(1L, lockPicture = false), showing)
    }

    @Test
    fun anActiveEntryThatLeftTheLockListFallsBackToItsFirst() {
        val showing = EntryTargets.showing(locked = true, foxOnLock = true, homeActive = 1L, lockActive = 4L, entries = entries)
        assertEquals(EntryTargets.Showing(2L, lockPicture = true), showing)
    }
}
