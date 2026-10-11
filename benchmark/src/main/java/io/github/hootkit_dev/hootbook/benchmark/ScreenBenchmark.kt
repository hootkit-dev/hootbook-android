package io.github.hootkit_dev.hootbook.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Latency and frame timing for the main screens, on a freshly started process each
 * iteration.
 *
 * Each test runs once per entry of the "variants" order (base, candidate, candidate, base),
 * so the iteration counts here are per round: latency tests take 5 (one number per
 * iteration), frame tests 2 (hundreds of frames per iteration).
 *
 * Most tests run without AOT compilation, what a sideloaded install sees at first. The
 * `WithProfile` tests compile each build with its Baseline Profile, if it has one (what
 * users get once the profile is installed), so base vs candidate shows what a profile wins.
 */
@RunWith(Parameterized::class)
class ScreenBenchmark(private val variant: Variant) {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun measure(
        iterations: Int = FRAME_ITERATIONS,
        metrics: List<Metric> = listOf(FrameTimingMetric()),
        compilationMode: CompilationMode = CompilationMode.None(),
        // Frame tests wait for Home to settle; latency tests start from it at once.
        settle: Boolean = true,
        setup: MacrobenchmarkScope.() -> Unit = {},
        block: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = variant.packageName,
        metrics = metrics,
        compilationMode = compilationMode,
        iterations = iterations,
        setupBlock = {
            step("setup")
            killProcess()
            seedData()
            startActivityAndWait()
            waitForHome()
            if (settle) settle()
            setup()
        },
        measureBlock = {
            step("measure")
            block()
        },
    )

    @Test
    fun homeScroll() = measure {
        flingDownAndUp(times = 3)
    }

    /**
     * How often the list was published to the UI (each publish recomposes it), and tap →
     * first frame that draws the list. The trace sections only exist in builds that add
     * them; older baselines report none.
     *
     * No frame metrics: a tap-to-list window holds only 4-6 frames, half idle and half
     * heavy, so their percentiles jump between the two.
     */
    @OptIn(ExperimentalMetricApi::class)
    private val transactionsMetrics = listOf(
        FrameTimingMetric(), // Current Core UI does not emit old publish/firstDraw traces; no fake latency metric.
    )

    /**
     * Frame timing during Core transaction loading. Waiting for seeded rows bounds the
     * measurement only; this is not a tap-to-first-draw latency or publish-count metric.
     */
    @Test
    fun transactionsLoadFrames() = dataReady(CompilationMode.None())

    @Test
    fun transactionsLoadFramesWithProfile() = dataReady(withProfile)

    private fun dataReady(compilationMode: CompilationMode) =
        measure(LATENCY_ITERATIONS, transactionsMetrics, compilationMode, settle = false) {
            clickTab("Transactions")
            waitForSeededRows()
        }

    /** Cold start to a populated Home, each build compiled with its Baseline Profile if any. */
    @Test
    fun startupWithProfile() = rule.measureRepeated(
        packageName = variant.packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = withProfile,
        startupMode = StartupMode.COLD,
        iterations = LATENCY_ITERATIONS,
        setupBlock = {
            seedData()
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForHome()
    }

    /** Browsing the month's list with normal-speed flicks. */
    @Test
    fun transactionsScroll() = measure(setup = {
        openTab("Transactions")
        waitForSeededRows()
    }) {
        flingDownAndUp(times = 3, dpPerSecond = FLING_NORMAL_DP_PER_S)
    }

    /** The hardest possible fling: 7500 dp/s, near Android's 8000 dp/s cap. */
    @Test
    fun transactionsFling() = measure(setup = {
        openTab("Transactions")
        waitForSeededRows()
    }) {
        flingDownAndUp(times = 2)
    }

    @Test
    fun analyticsOpenAndScroll() = measure {
        openTab("Analytics")
        flingDownAndUp(times = 2)
    }

    @Test
    fun tabSwitching() = measure {
        repeat(3) {
            openTab("Analytics")
            openTab("Transactions")
            openTab("Home")
        }
    }

    /** Home → Core profile, scroll it, then back. */
    @Test
    fun profileOpenAndScroll() = measure {
        device.findObject(By.desc("core-home-profile")).click()
        if (!device.wait(Until.gone(By.desc("core-screen-home")), 10_000)) {
            fail("Settings did not open")
        }
        device.waitForIdle(1_000)
        flingDownAndUp(times = 2)
        device.pressBack()
        waitForHome()
    }

    companion object {
        private const val LATENCY_ITERATIONS = 5
        private const val FRAME_ITERATIONS = 2

        /** A build without a profile (e.g. an older baseline) is compiled with none. */
        private val withProfile =
            CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.UseIfAvailable)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() = Variants.params()
    }
}
