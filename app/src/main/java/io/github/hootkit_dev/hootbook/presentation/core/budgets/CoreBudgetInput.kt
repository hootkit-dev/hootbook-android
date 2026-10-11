package io.github.hootkit_dev.hootbook.presentation.core.budgets

import dev.hootkit.core.*
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import java.util.Locale

/** Text parsing is input/display only. Core owns validation and all budget arithmetic. */
data class CoreBudgetDraft(
    val id: String? = null,
    val name: String = "",
    val kind: BudgetKind = BudgetKind.EXPENSE,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val amount: String = "",
    val currency: String = "CNY",
    val start: String = budgetToday().budgetDateText(),
    val originalStart: String? = null,
    val end: String = "",
    val allAccounts: Boolean = true,
    val accountIds: Set<String> = emptySet(),
    val color: String = "#4CAF50",
    val limits: List<NewBudgetLimit> = emptyList(),
) {
    fun newBudget(defaultName: String) = NewBudget(
        name.trim().ifEmpty { defaultName }, kind, period, money(), budgetDate(start),
        if (period == BudgetPeriod.CUSTOM) budgetDate(end) else null,
        if (allAccounts) null else accountIds.toList(), color.trim(), limits,
    )
    fun patch(defaultName: String) = BudgetPatch(
        name = name.trim().ifEmpty { defaultName }, kind = kind, period = period, amount = money(),
        start = if (start != originalStart) budgetDate(start) else null,
        end = if (period == BudgetPeriod.CUSTOM) budgetDate(end) else null,
        accountIds = if (allAccounts) null else accountIds.toList(), allAccounts = allAccounts,
        color = color.trim(),
    )
    private fun money() = Money(budgetAmount(amount), currency.trim().uppercase(Locale.ROOT))
    companion object {
        fun from(row: Budget) = CoreBudgetDraft(row.id, row.name, row.kind, row.period,
            row.amount.amount.toPlainString(), row.amount.currency, row.anchor.budgetDateText(),
            row.anchor.budgetDateText(), row.end?.budgetDateText().orEmpty(), row.accountIds == null,
            row.accountIds.orEmpty().toSet(), row.color)
    }
}

internal fun budgetAmount(text: String): BigDecimal = text.trim().toBigDecimalOrNull()
    ?: throw CoreBudgetInputException(io.github.hootkit_dev.hootbook.R.string.core_history_invalid_amount)
internal fun budgetDate(text: String): LocalDate {
    val date = runCatching { JavaDate.parse(text.trim()) }.getOrElse { throw CoreBudgetInputException(io.github.hootkit_dev.hootbook.R.string.core_history_invalid_date) }
    return LocalDate(date.year, date.monthValue.toUByte(), date.dayOfMonth.toUByte())
}
internal fun budgetToday(): LocalDate = JavaDate.now().let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
internal fun LocalDate.budgetDateText(): String = JavaDate.of(year, month.toInt(), day.toInt()).toString()

/** Spec-authorized presentation ratio only; no monetary totals are computed in Android. */
internal fun budgetProgressFraction(spent: BigDecimal, amount: BigDecimal): Float =
    if (amount.signum() <= 0) 0f else spent.divide(amount, java.math.MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)

internal class CoreBudgetInputException(val resource:Int):IllegalArgumentException()
