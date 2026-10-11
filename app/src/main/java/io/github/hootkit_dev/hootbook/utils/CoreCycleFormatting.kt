package io.github.hootkit_dev.hootbook.utils

import dev.hootkit.core.Cycle
import dev.hootkit.core.CycleUnit

/** Visual wording only; Core determines due dates and monthly equivalents. */
fun formatCoreCycle(cycle: Cycle): String {
    val unit = when (cycle.unit) {
        CycleUnit.DAY -> "day"; CycleUnit.WEEK -> "week"; CycleUnit.MONTH -> "month"; CycleUnit.YEAR -> "year"; CycleUnit.UNKNOWN -> return "Unknown cycle"
    }
    return if (cycle.every == 1u) "Every $unit" else "Every ${cycle.every} ${unit}s"
}
