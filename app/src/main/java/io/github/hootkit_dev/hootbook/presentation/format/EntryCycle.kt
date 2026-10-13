package io.github.hootkit_dev.hootbook.presentation.format

import dev.hootkit.core.Cycle
import dev.hootkit.core.CycleUnit
import io.github.hootkit_dev.hootbook.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

val entryCycleKeys = listOf("Weekly", "Monthly", "Quarterly", "Semi-Annual", "Annual", "Custom")
fun entryCycle(key: String): Cycle? = when (key) {
    "Weekly" -> Cycle(1u, CycleUnit.WEEK)
    "Monthly" -> Cycle(1u, CycleUnit.MONTH)
    "Quarterly" -> Cycle(3u, CycleUnit.MONTH)
    "Semi-Annual" -> Cycle(6u, CycleUnit.MONTH)
    "Annual" -> Cycle(1u, CycleUnit.YEAR)
    else -> null
}
fun entryCycleKey(cycle: Cycle): String = entryCycleKeys.firstOrNull { entryCycle(it) == cycle } ?: "Custom"
@Composable fun entryCycleLabel(key: String): String = stringResource(when (key) {
    "Weekly" -> R.string.cycle_weekly
    "Quarterly" -> R.string.cycle_quarterly
    "Semi-Annual" -> R.string.cycle_semi_annual
    "Annual" -> R.string.cycle_annual
    "Custom" -> R.string.cycle_custom
    else -> R.string.cycle_monthly
})
