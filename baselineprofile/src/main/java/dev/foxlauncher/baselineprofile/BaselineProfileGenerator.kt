package dev.foxlauncher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.baselineprofile.Journeys.openDrawer
import dev.foxlauncher.baselineprofile.Journeys.scrollDrawer
import dev.foxlauncher.baselineprofile.Journeys.start
import dev.foxlauncher.baselineprofile.Journeys.swipePages
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes `app/src/release/generated/baselineProfiles/` (task 8.1):
 * `ANDROID_SERIAL=emulator-5558 ./gradlew :app:generateBaselineProfile`, on an emulator, never the phone.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = FOX, includeInStartupProfile = true) {
        start()
        swipePages()
        openDrawer()
        scrollDrawer()
    }
}
