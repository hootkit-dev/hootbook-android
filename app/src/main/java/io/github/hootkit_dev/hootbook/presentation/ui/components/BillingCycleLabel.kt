package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.utils.SubscriptionUtils
import java.math.BigDecimal

/** A stored billing cycle in the user's language: "每季度", "每 2 周"… */
@Composable
fun billingCycleLabel(billingCycle: String?): String {
    SubscriptionUtils.customParts(billingCycle)?.let { (count, unit) ->
        val unitName = stringResource(
            when (unit) {
                "day" -> R.string.cycle_unit_day
                "week" -> R.string.cycle_unit_week
                "year" -> R.string.cycle_unit_year
                else -> R.string.cycle_unit_month
            }
        )
        return stringResource(R.string.cycle_every, count.toInt(), unitName)
    }
    return stringResource(
        when (SubscriptionUtils.cycleKey(billingCycle)) {
            "weekly" -> R.string.cycle_weekly
            "quarterly" -> R.string.cycle_quarterly
            "semi-annual" -> R.string.cycle_semi_annual
            "annual" -> R.string.cycle_annual
            "custom" -> R.string.cycle_custom
            else -> R.string.cycle_monthly
        }
    )
}

/** The cycle, and what it comes to per month when that differs: "每年 · 约 ¥25/月". */
@Composable
fun billingCycleSubtitle(amount: BigDecimal, currency: String, billingCycle: String?): String {
    val label = billingCycleLabel(billingCycle)
    val monthly = SubscriptionUtils.monthlyEquivalent(amount, billingCycle)
    return if (monthly.compareTo(amount) == 0) label
    else stringResource(R.string.cycle_with_monthly, label, CurrencyFormatter.formatCurrency(monthly, currency))
}
