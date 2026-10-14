package dev.foxlauncher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.baselineprofile.Journeys.start
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start with and without Fox's baseline profile (task 8.1). Frame timing is left to
 * scripts/perf (gfxinfo on the phone): the emulators' traces carry no render-thread slices.
 * `ANDROID_SERIAL=emulator-5558 ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=dev.foxlauncher.baselineprofile.FoxBenchmarks`, emulators only.
 * Emulator numbers compare builds; the phone's come from scripts/perf/measure.sh.
 */
@RunWith(AndroidJUnit4::class)
class FoxBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = FOX,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) { start() }

    private companion object {
        const val ITERATIONS = 5
    }
}
