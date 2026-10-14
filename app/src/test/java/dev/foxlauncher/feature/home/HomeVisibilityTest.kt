package dev.foxlauncher.feature.home

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeVisibilityTest {

    private val visibility = HomeVisibility()

    private suspend fun homeInFront() = visibility.homeGesturesInFront.first()

    @Test
    fun theResumedHomeWithNothingOverItHasItsOwnGestures() = runTest {
        assertFalse(homeInFront())
        visibility.onResumed()
        assertTrue(homeInFront())
        visibility.onPaused()
        assertFalse(homeInFront())
    }

    @Test
    fun theOpenDrawerTakesTheNavigationGesturesAsSettingsDo() = runTest {
        visibility.onResumed()
        visibility.onDrawerOpen(true)
        assertFalse(homeInFront())
        visibility.onDrawerOpen(false)
        assertTrue(homeInFront())
        visibility.onSettingsOpen(true)
        assertFalse(homeInFront())
        visibility.onDrawerOpen(true)
        visibility.onSettingsOpen(false)
        assertFalse(homeInFront())
    }

    @Test
    fun aSecondInstancePausingLateLeavesTheHomeInFront() = runTest {
        visibility.onResumed()
        visibility.onResumed()
        visibility.onPaused()
        assertTrue(homeInFront())
    }
}
