package io.github.hootkit_dev.hootbook.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * Dates as the app shows them: Chinese as 9月1日 / 2025年9月1日 / 2026年9月, others as the
 * locale's short month names. One place, so no screen falls back to "9月 1" or English.
 */
object DateFormats {
    private fun zh(locale: Locale) = locale.language == "zh"

    /** 9月1日 / Sep 1 */
    fun monthDay(date: TemporalAccessor, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (zh(locale)) "M月d日" else "MMM d", locale).format(date)

    /** 2025年9月1日 / Sep 1, 2025 */
    fun fullDate(date: TemporalAccessor, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (zh(locale)) "yyyy年M月d日" else "MMM d, yyyy", locale).format(date)

    /** 9月1日 this year, 2025年9月1日 otherwise. */
    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        if (date.year == LocalDate.now().year) monthDay(date, locale) else fullDate(date, locale)

    /** 2026年9月 / September 2026 */
    fun yearMonth(date: TemporalAccessor, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (zh(locale)) "yyyy年M月" else "MMMM yyyy", locale).format(date)

    /** 9月1日 14:30 / Sep 1, 14:30 (24-hour) */
    fun dayTime(date: TemporalAccessor, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (zh(locale)) "M月d日 HH:mm" else "MMM d, HH:mm", locale).format(date)

    /** For code that takes a formatter: 9月1日 / Sep 1 */
    fun monthDayFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (zh(locale)) "M月d日" else "MMM d", locale)

    /** 2025年9月1日 / Sep 1, 2025 */
    fun fullDateFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (zh(locale)) "yyyy年M月d日" else "MMM d, yyyy", locale)

    /** 9月 / Sep */
    fun monthFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (zh(locale)) "M月" else "MMM", locale)

    /** 25年9月 / Sep 25 */
    fun shortYearMonthFormatter(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (zh(locale)) "yy年M月" else "MMM yy", locale)

    /** 14:30 */
    fun time(date: TemporalAccessor, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm", locale).format(date)
}

/**
 * Material date pickers work in UTC midnights. Hand them a day this way and read their
 * choice back with [pickerDate], or a device west of UTC shows and saves the day before.
 */
fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerDate(millis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
