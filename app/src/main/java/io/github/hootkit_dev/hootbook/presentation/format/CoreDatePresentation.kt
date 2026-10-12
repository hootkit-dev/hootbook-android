package io.github.hootkit_dev.hootbook.presentation.format

import dev.hootkit.core.LocalDate
import dev.hootkit.core.LocalDateTime

/** ISO display text keeps Core's local calendar values; no device timezone conversion. */
fun LocalDate.coreDateText(): String = java.time.LocalDate.of(year, month.toInt(), day.toInt()).toString()

fun LocalDateTime.coreDateText(): String = java.time.LocalDateTime.of(
    year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt(), nanosecond.toInt(),
).toString()
