package io.github.hootkit_dev.hootbook.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Records the Baseline Profile in app/src/main/baseline-prof.txt: the classes and methods
 * of startup and the main screens, which ART then compiles ahead of time.
 *
 * Run it against the unminified `profiling` build (same package as `benchmark`), so the
 * rules name source classes; see the profile job in perf-device.yml. It needs Android 13+.
 */
@RunWith(JUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        maxIterations = 8,
        stableIterations = 2,
        includeInStartupProfile = true,
    ) {
        seedData()
        pressHome()
        startActivityAndWait()
        waitForHome()
        flingDownAndUp(times = 1, dpPerSecond = FLING_NORMAL_DP_PER_S)

        openTab("Transactions")
        waitForSeededRows()
        flingDownAndUp(times = 2, dpPerSecond = FLING_NORMAL_DP_PER_S)

        openTab("Analytics")
        flingDownAndUp(times = 1, dpPerSecond = FLING_NORMAL_DP_PER_S)

        openTab("Home")
        waitForHome()
        device.findObject(By.desc("core-home-profile"))?.let { profile ->
            profile.click()
            if (device.wait(Until.gone(By.desc("core-screen-home")), 10_000)) {
                device.waitForIdle(1_000)
                flingDownAndUp(times = 1, dpPerSecond = FLING_NORMAL_DP_PER_S)
                device.pressBack()
                waitForHome()
            }
        }
    }
}
