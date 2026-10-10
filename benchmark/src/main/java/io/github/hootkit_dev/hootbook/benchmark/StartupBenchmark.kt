package io.github.hootkit_dev.hootbook.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * App launch to a populated Home screen.
 *
 * CompilationMode.None is what a sideloaded APK gets until the device compiles it in
 * the background (no Play cloud profile, no baseline profile yet). Full is the ceiling
 * a baseline profile can approach; the gap between them is what a profile could win.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(
    private val startupMode: StartupMode,
    private val compilation: CompilationMode,
) {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun startup() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = startupMode,
        iterations = 5,
        setupBlock = {
            seedData()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForHome()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun params() = listOf(
            arrayOf(StartupMode.COLD, CompilationMode.None()),
            arrayOf(StartupMode.COLD, CompilationMode.Full()),
            arrayOf(StartupMode.WARM, CompilationMode.None()),
            arrayOf(StartupMode.HOT, CompilationMode.None()),
        )
    }
}
