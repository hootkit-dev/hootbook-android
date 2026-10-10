package io.github.hootkit_dev.hootbook.presentation.common

import androidx.annotation.StringRes
import io.github.hootkit_dev.hootbook.R
import java.time.LocalDate
import java.time.YearMonth

enum class TimePeriod(@StringRes val labelRes: Int) {
    THIS_MONTH(R.string.time_period_this_month),
    LAST_MONTH(R.string.time_period_last_month),
    CURRENT_FY(R.string.time_period_current_fy),
    ALL(R.string.time_period_all_time),
    CUSTOM(R.string.time_period_custom_range)
}

enum class TransactionTypeFilter(@StringRes val labelRes: Int) {
    ALL(R.string.type_all),
    INCOME(R.string.type_income),
    EXPENSE(R.string.type_expense),
    CREDIT(R.string.type_credit),
    TRANSFER(R.string.type_transfer),
    INVESTMENT(R.string.type_investment),
    LENT(R.string.type_lent),
    BORROWED(R.string.type_borrowed)
}

fun getDateRangeForPeriod(period: TimePeriod): Pair<LocalDate, LocalDate>? {
    val today = LocalDate.now()
    return when (period) {
        TimePeriod.THIS_MONTH -> {
            val start = YearMonth.now().atDay(1)
            start to today
        }
        TimePeriod.LAST_MONTH -> {
            val lastMonth = YearMonth.now().minusMonths(1)
            val start = lastMonth.atDay(1)
            val end = lastMonth.atEndOfMonth()
            start to end
        }
        // Kept under its old name so saved filters still load; it is the calendar year
        TimePeriod.CURRENT_FY -> today.withDayOfYear(1) to today
        TimePeriod.ALL -> {
            // Use a reasonable date range for "All Time" - 10 years back to today
            val start = today.minusYears(10)
            start to today
        }
        TimePeriod.CUSTOM -> {
            // Custom range is handled separately in ViewModel
            null
        }
    }
}