package io.github.hootkit_dev.hootbook.utils

import android.util.Log
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

object SubscriptionUtils {

    private val WEEKS_PER_YEAR = BigDecimal(52)
    private val MONTHS_PER_YEAR = BigDecimal(12)
    private val MONTHS_PER_QUARTER = BigDecimal(3)
    private val MONTHS_PER_HALF = BigDecimal(6)
    private val AVG_DAYS_PER_MONTH = BigDecimal("30.4375")
    private val MC = MathContext.DECIMAL64
    private val CUSTOM_CYCLE_UNITS = setOf("day", "week", "month", "year")

    /** The cycles a subscription can have, as stored; screens show their translated names. */
    val CYCLE_KEYS = listOf("Monthly", "Quarterly", "Semi-Annual", "Annual", "Weekly", "Custom")

    // Cycles saved under their on-screen name before they were stored as keys
    private val LEGACY_LABELS = mapOf(
        "每月" to "monthly", "每季度" to "quarterly", "每半年" to "semi-annual", "每年" to "annual",
        "每周" to "weekly", "每週" to "weekly", "自定义" to "custom", "自訂" to "custom",
        "semi-annually" to "semi-annual", "yearly" to "annual"
    )

    /** A stored cycle as a lower-case key ("monthly", "custom_2_week_forever"…), old labels included. */
    fun cycleKey(billingCycle: String?): String {
        val raw = billingCycle?.trim().orEmpty()
        if (raw.isEmpty()) return "monthly"
        return LEGACY_LABELS[raw] ?: LEGACY_LABELS[raw.lowercase()] ?: raw.lowercase()
    }

    /** Whether [billingCycle] is (or is being set to) a custom cycle. */
    fun isCustom(billingCycle: String?): Boolean = cycleKey(billingCycle).startsWith("custom")

    /** The parts of a custom cycle: how many units, which unit; null for a standard one. */
    fun customParts(billingCycle: String?): Pair<Long, String>? =
        cycleKey(billingCycle).takeIf { it.startsWith("custom_") }?.let(::parseCustomCycle)?.let { it.count to it.unit }

    private data class CustomCycle(val count: Long, val unit: String, val endDate: String?)

    private fun parseCustomCycle(cycle: String): CustomCycle? {
        val parts = cycle.split("_")
        val count = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val unit = parts.getOrNull(2)?.takeIf { it in CUSTOM_CYCLE_UNITS } ?: return null
        return CustomCycle(count, unit, parts.getOrNull(3))
    }

    fun formatBillingCycle(billingCycle: String?): String {
        val cycle = cycleKey(billingCycle)

        if (cycle.startsWith("custom_")) {
            val (count, unit, _) = parseCustomCycle(cycle) ?: return "Monthly"
            return if (count == 1L) "Every $unit" else "Every $count ${unit}s"
        }

        return when (cycle) {
            "weekly"      -> "Weekly"
            "quarterly"   -> "Quarterly"
            "semi-annual" -> "Semi-annual"
            "annual"      -> "Annual"
            else          -> "Monthly"
        }
    }

    fun cycleSubtitle(amount: BigDecimal, currency: String, billingCycle: String?): String {
        val cycleLabel = formatBillingCycle(billingCycle)
        val monthlyEq = monthlyEquivalent(amount, billingCycle)
        return if (monthlyEq.compareTo(amount) == 0) {
            cycleLabel
        } else {
            "$cycleLabel · ≈ ${CurrencyFormatter.formatCurrency(monthlyEq, currency)}/mo"
        }
    }

    fun monthlyEquivalent(amount: BigDecimal, billingCycle: String?): BigDecimal {
        val cycle = cycleKey(billingCycle)

        if (cycle.startsWith("custom_")) {
            val (count, unit, _) = parseCustomCycle(cycle) ?: return amount
            val divisor = BigDecimal(count)
            return when (unit) {
                "day"  -> amount.multiply(AVG_DAYS_PER_MONTH).divide(divisor, MC)
                "week" -> amount.multiply(WEEKS_PER_YEAR).divide(MONTHS_PER_YEAR.multiply(divisor), MC)
                "year" -> amount.divide(MONTHS_PER_YEAR.multiply(divisor), MC)
                else   -> amount.divide(divisor, MC)
            }
        }

        return when (cycle) {
            "weekly"      -> amount.multiply(WEEKS_PER_YEAR).divide(MONTHS_PER_YEAR, MC)
            "quarterly"   -> amount.divide(MONTHS_PER_QUARTER, MC)
            "semi-annual" -> amount.divide(MONTHS_PER_HALF, MC)
            "annual"      -> amount.divide(MONTHS_PER_YEAR, MC)
            else          -> amount
        }
    }

    /**
     * Calculates the next payment date based on the current date and billing cycle.
     * Supports standard cycles (Weekly, Monthly, Quarterly, Semi-Annual, Annual)
     * and custom cycles (e.g., custom_1_day_2026-04-30).
     */
    fun calculateNextPaymentDate(
        fromDate: LocalDate,
        billingCycle: String?
    ): LocalDate {
        val today = LocalDate.now()
        val cycle = cycleKey(billingCycle)
        
        val custom = if (cycle.startsWith("custom_")) parseCustomCycle(cycle) else null
        if (custom != null) {
            val (count, unit, endDateStr) = custom

            fun LocalDate.advance(): LocalDate = when (unit) {
                "day"  -> plusDays(count)
                "week" -> plusWeeks(count)
                "year" -> plusYears(count)
                else   -> plusMonths(count)
            }

            var nextDate = fromDate.advance()
            while (nextDate.isBefore(today)) nextDate = nextDate.advance()

            if (endDateStr != null && endDateStr != "forever") {
                try {
                    val endDate = LocalDate.parse(endDateStr)
                    if (nextDate.isAfter(endDate)) {
                        Log.d("SubscriptionUtils", "Next date $nextDate is after end date $endDate")
                    }
                } catch (e: Exception) {
                    Log.e("SubscriptionUtils", "Error parsing end date: $endDateStr", e)
                }
            }

            return nextDate
        }

        // Standard cycles
        var nextDate = when (cycle) {
            "weekly" -> fromDate.plusWeeks(1)
            "quarterly" -> fromDate.plusMonths(3)
            "semi-annual" -> fromDate.plusMonths(6)
            "annual" -> fromDate.plusYears(1)
            else -> fromDate.plusMonths(1) // covers "monthly" and defaults
        }

        while (nextDate.isBefore(today)) {
            nextDate = when (cycle) {
                "weekly" -> nextDate.plusWeeks(1)
                "quarterly" -> nextDate.plusMonths(3)
                "semi-annual" -> nextDate.plusMonths(6)
                "annual" -> nextDate.plusYears(1)
                else -> nextDate.plusMonths(1)
            }
        }
        return nextDate
    }
}
