package io.github.hootkit_dev.hootbook.presentation.format

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDateTime as JavaDateTime
import java.time.LocalDate as JavaDate

/** Parsing is an input boundary only. Core owns validation and all ledger calculations. */
internal fun JavaDateTime.toCoreDateTime() = LocalDateTime(
    year, monthValue.toUByte(), dayOfMonth.toUByte(), hour.toUByte(), minute.toUByte(), second.toUByte(), nano.toUInt(),
)
internal fun LocalDateTime.inputText(): String = JavaDateTime.of(
    year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt(), nanosecond.toInt(),
).toString()
internal fun parseCoreDateTime(text: String): LocalDateTime = JavaDateTime.parse(text.trim()).toCoreDateTime()
