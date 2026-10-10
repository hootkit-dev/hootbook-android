package io.github.hootkit_dev.hootbook.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Locale-aware compact labels for date filters. */
object DateRangeUtils {
    // Resolve the locale per call so changing the app language does not retain old labels.
    private fun defaultFormatter(startDate: LocalDate, endDate: LocalDate): DateTimeFormatter {
        val locale = Locale.getDefault()
        val includeYear = startDate.year != endDate.year
        val pattern = when {
            locale.language == "zh" && includeYear -> "yyyy年M月d日"
            locale.language == "zh" -> "M月d日"
            includeYear -> "MMM d, yyyy"
            else -> "MMM d"
        }
        return DateTimeFormatter.ofPattern(pattern, locale)
    }

    /** Explicit formatters are respected; ranges crossing a year show both years by default. */
    fun formatDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
        formatter: DateTimeFormatter = defaultFormatter(startDate, endDate)
    ): String = "${startDate.format(formatter)} - ${endDate.format(formatter)}"

    fun formatDateRange(
        dateRange: Pair<LocalDate, LocalDate>?,
        formatter: DateTimeFormatter? = null
    ): String? = dateRange?.let { (start, end) ->
        formatDateRange(start, end, formatter ?: defaultFormatter(start, end))
    }
}
