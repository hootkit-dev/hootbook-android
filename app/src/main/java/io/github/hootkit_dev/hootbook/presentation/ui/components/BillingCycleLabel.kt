package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.hootkit.core.Cycle
import dev.hootkit.core.CycleUnit
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import java.math.BigDecimal

/** Render the generated Core cycle. Never parse legacy cycle strings or calculate monthly equivalents. */
@Composable
fun billingCycleLabel(cycle: Cycle): String {
    val standard = when (cycle.unit) {
        CycleUnit.WEEK -> if (cycle.every == 1u) R.string.cycle_weekly else null
        CycleUnit.MONTH -> when (cycle.every) {
            1u -> R.string.cycle_monthly
            3u -> R.string.cycle_quarterly
            6u -> R.string.cycle_semi_annual
            else -> null
        }
        CycleUnit.YEAR -> if (cycle.every == 1u) R.string.cycle_annual else null
        else -> null
    }
    if (standard != null) return stringResource(standard)
    val unit = stringResource(when(cycle.unit) {
        CycleUnit.DAY -> R.string.cycle_unit_day; CycleUnit.WEEK -> R.string.cycle_unit_week
        CycleUnit.MONTH -> R.string.cycle_unit_month; CycleUnit.YEAR -> R.string.cycle_unit_year
        CycleUnit.UNKNOWN -> return stringResource(R.string.core_kind_unknown)
    })
    return stringResource(R.string.cycle_every, cycle.every.toInt(), unit)
}
@Composable
fun billingCycleSubtitle(cycle: Cycle, monthlyEquivalent: BigDecimal?, currency: String): String {
    val label = billingCycleLabel(cycle)
    return monthlyEquivalent?.let { stringResource(R.string.cycle_with_monthly, label, CoreMoneyPresentation.format(it, currency)) } ?: label
}
