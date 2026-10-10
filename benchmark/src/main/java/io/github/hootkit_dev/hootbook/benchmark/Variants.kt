package io.github.hootkit_dev.hootbook.benchmark

import androidx.test.platform.app.InstrumentationRegistry

/** One measured pass over one build: [variant] is "base" or "candidate". */
data class Variant(val index: Int, val variant: String, val round: Int, val packageName: String) {
    override fun toString() = "$index"
}

/**
 * The builds a parameterized benchmark measures, in order.
 *
 * Both builds are installed side by side and the "variants" instrumentation argument lists
 * the order, e.g. `base+candidate+candidate+base`; the baseline is built with applicationId
 * suffix `.benchmark.base`. Without the argument (e.g. a local run) there is one pass over
 * the default benchmark package.
 *
 * Test names only carry the index ("transactionsScroll[0]"), because the test runner's
 * method filter only accepts numeric parameter suffixes. compare.py maps indexes back to
 * builds from the same order (`--order`).
 */
object Variants {
    val all: List<Variant> by lazy {
        val order = InstrumentationRegistry.getArguments().getString("variants")
            ?.split('+')?.map { it.trim() }?.filter { it.isNotEmpty() }
            .orEmpty()
        if (order.isEmpty()) {
            listOf(Variant(0, "default", 1, TARGET_PACKAGE))
        } else {
            val seen = mutableMapOf<String, Int>()
            order.mapIndexed { i, name ->
                val round = (seen[name] ?: 0) + 1
                seen[name] = round
                Variant(i, name, round, if (name == "base") "$TARGET_PACKAGE.base" else TARGET_PACKAGE)
            }
        }
    }

    fun params(): List<Array<Any>> = all.map { arrayOf<Any>(it) }
}
