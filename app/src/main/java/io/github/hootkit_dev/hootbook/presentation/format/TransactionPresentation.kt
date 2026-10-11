package io.github.hootkit_dev.hootbook.presentation.format

import dev.hootkit.core.Transaction
import dev.hootkit.core.LocalDateTime
import io.github.hootkit_dev.hootbook.presentation.format.EntryMoneyPresentation as CurrencyFormatter

fun LocalDateTime.toJavaDateTime(): java.time.LocalDateTime = java.time.LocalDateTime.of(
    year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt(), nanosecond.toInt(),
)
fun Transaction.displayTitle(categoryName: String? = null): String = merchant.takeIf { it.isNotBlank() } ?: notes?.takeIf { it.isNotBlank() } ?: categoryName.orEmpty()
fun Transaction.formatAmount(): String = CurrencyFormatter.formatCurrency(amount, currency)

fun dev.hootkit.core.TransactionKind.transactionKindLabelRes(): Int = when (this) {
    dev.hootkit.core.TransactionKind.EXPENSE -> io.github.hootkit_dev.hootbook.R.string.type_expense
    dev.hootkit.core.TransactionKind.INCOME -> io.github.hootkit_dev.hootbook.R.string.type_income
    dev.hootkit.core.TransactionKind.TRANSFER -> io.github.hootkit_dev.hootbook.R.string.type_transfer
    dev.hootkit.core.TransactionKind.INVESTMENT -> io.github.hootkit_dev.hootbook.R.string.type_investment
    dev.hootkit.core.TransactionKind.LOAN_OUT -> io.github.hootkit_dev.hootbook.R.string.type_lent
    dev.hootkit.core.TransactionKind.LOAN_IN -> io.github.hootkit_dev.hootbook.R.string.type_borrowed
    dev.hootkit.core.TransactionKind.UNKNOWN -> io.github.hootkit_dev.hootbook.R.string.core_kind_unknown
}

/** Unsigned Core clock fields must be unboxed before Java integer formatting. */
fun coreClockPart(value: UByte): String = String.format("%02d", value.toInt())
