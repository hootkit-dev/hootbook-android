package io.github.hootkit_dev.hootbook.benchmark

import android.content.res.Resources
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

const val TARGET_PACKAGE = "io.github.hootkit_dev.hootbook.benchmark"
private const val TAG = "HootbookBench"
private const val TIMEOUT_MS = 15_000L
private const val SEED_TIMEOUT_MS = 90_000L

// Marquee rows animate forever, so an unbounded waitForIdle can stall for its full 10 s default.
private const val IDLE_MS = 1_000L

fun step(message: String) = Log.i(TAG, message)

/** Logs what is on screen (package, text, id, description per node) and fails the test. */
fun MacrobenchmarkScope.fail(message: String): Nothing {
    Log.e(TAG, "FAIL: $message")
    val dump = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
    for (node in NODE.findAll(dump)) {
        val (text, id, pkg, desc) = node.destructured
        if (text.isNotEmpty() || desc.isNotEmpty()) {
            Log.e(TAG, "  [$pkg] text=\"$text\" id=$id desc=\"$desc\"")
        }
    }
    error(message)
}

private val NODE = Regex(
    """<node [^>]*?text="([^"]*)"[^>]*?resource-id="([^"]*)"[^>]*?package="([^"]*)"[^>]*?content-desc="([^"]*)""""
)

/**
 * On a busy device the launcher can hit an ANR, and its "isn't responding" dialog covers
 * the app. Dismiss it with "Wait" rather than failing the test.
 */
private fun MacrobenchmarkScope.dismissSystemAnr(): Boolean {
    val wait = device.findObject(By.res("android:id/aerr_wait")) ?: return false
    step("dismissing system ANR dialog")
    wait.click()
    return true
}

/** Writes the fixed data set and skips onboarding. Returns once the data is committed. */
fun MacrobenchmarkScope.seedData() {
    val out = device.executeShellCommand(
        "am start -W -n $packageName/io.github.hootkit_dev.hootbook.benchmark.SeedActivity"
    )
    if ("Error" in out) fail("SeedActivity failed to start: $out")
    val deadline = System.currentTimeMillis() + SEED_TIMEOUT_MS
    // Written by SeedActivity once the data set and onboarding flags are committed.
    val marker = "/sdcard/Android/data/$packageName/files/benchmark-seeded"
    while ("benchmark-seeded" !in device.executeShellCommand("ls $marker")) {
        if (System.currentTimeMillis() > deadline) fail("Seeding did not finish")
        Thread.sleep(250)
    }
}

/** Home is usable once the bottom bar and the add button are on screen. */
fun MacrobenchmarkScope.waitForHome() {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (!device.wait(Until.hasObject(By.desc("core-screen-home")), 1_000)) {
        if (dismissSystemAnr()) continue
        if (System.currentTimeMillis() > deadline) fail("Home did not appear")
    }
}

/** Lets the screen finish loading and animating (at most [IDLE_MS]) before frames count. */
fun MacrobenchmarkScope.settle() {
    device.waitForIdle(IDLE_MS)
}

/** Bottom-bar labels share text with screen titles, so take the lowest match on screen. */
fun MacrobenchmarkScope.clickTab(label: String) {
    step("clickTab $label")
    if (!device.wait(Until.hasObject(By.text(label)), TIMEOUT_MS)) fail("Tab $label not found")
    device.findObjects(By.text(label)).maxBy { it.visibleBounds.top }.click()
}

fun MacrobenchmarkScope.openTab(label: String) {
    clickTab(label)
    device.waitForIdle(IDLE_MS)
}

/** Waits until a row from the seeded data set is on screen. */
fun MacrobenchmarkScope.waitForSeededRows() {
    if (!device.wait(Until.hasObject(By.text(SEEDED_MERCHANT)), TIMEOUT_MS)) fail("No seeded rows shown")
}

private val SEEDED_MERCHANT = Pattern.compile(
    "Meituan|Ele\\.me|Starbucks|Luckin Coffee|FamilyMart|Hema|JD\\.com|Taobao|Pinduoduo|Didi|Metro|" +
        "China Railway|Wanda Cinema|Haidilao|Xiaomi Store|Apple|Uniqlo|IKEA|China Mobile|State Grid|" +
        "Pharmacy|Gym|Bookstore|Salary"
)

/** The tallest scrollable node is the screen's main list (carousels are short). */
fun MacrobenchmarkScope.mainList(): UiObject2 {
    if (!device.wait(Until.hasObject(By.scrollable(true)), TIMEOUT_MS)) fail("No scrollable list")
    return device.findObjects(By.scrollable(true)).maxBy { it.visibleBounds.height() }
        .also { step("mainList ${it.className} ${it.visibleBounds}") }
}

/**
 * UiAutomator's default fling, 7500 dp/s: about the hardest flick Android accepts
 * (ViewConfiguration caps flings at 8000 dp/s).
 */
const val FLING_MAX_DP_PER_S = 7500

/** A normal browsing flick, 2500 dp/s. */
const val FLING_NORMAL_DP_PER_S = 2500

/**
 * Flings the screen's main list [times] down, then back up, at [dpPerSecond]. The list is
 * looked up again before every fling: screens that recompose when their data arrives
 * replace the node, which makes a held UiObject2 stale.
 *
 * The gesture is UiObject2.fling's (a swipe across the list at that speed), but without
 * its wait: fling() then waits up to 5 s for a "scroll finished" accessibility event,
 * which Compose lists never send, so every fling cost 5 s. Instead each fling waits a
 * fixed time for the list to coast to a stop, the same for both builds.
 */
fun MacrobenchmarkScope.flingDownAndUp(times: Int, dpPerSecond: Int = FLING_MAX_DP_PER_S) {
    val speed = (dpPerSecond * Resources.getSystem().displayMetrics.density).toInt()
    // Android's fling decay runs about 2.4 s from 7500 dp/s and 1.1 s from 2500 dp/s; the
    // last second of the fast one only crawls, so it is cut short.
    val coastMs = if (dpPerSecond > FLING_NORMAL_DP_PER_S) 1_500L else 1_200L
    fun fling(direction: Direction, label: String) {
        step("$label at $speed px/s")
        val list = mainList()
        // Keep gestures away from the edges so they are not taken as system back / home.
        list.setGestureMargins(
            device.displayWidth / 5, device.displayHeight / 5,
            device.displayWidth / 5, device.displayHeight / 5,
        )
        // Flinging the content down is a swipe up.
        list.swipe(Direction.reverse(direction), 1f, speed)
        Thread.sleep(coastMs)
    }
    repeat(times) { fling(Direction.DOWN, "fling down ${it + 1}/$times") }
    repeat(times) { fling(Direction.UP, "fling up ${it + 1}/$times") }
}
