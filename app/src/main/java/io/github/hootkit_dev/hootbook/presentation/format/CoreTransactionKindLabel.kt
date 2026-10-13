package io.github.hootkit_dev.hootbook.presentation.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.R

@Composable
fun TransactionKind.displayLabel(): String = stringResource(when (this) {
    TransactionKind.EXPENSE -> R.string.core_kind_expense
    TransactionKind.INCOME -> R.string.core_kind_income
    TransactionKind.TRANSFER -> R.string.core_kind_transfer
    TransactionKind.INVESTMENT -> R.string.core_kind_investment
    TransactionKind.LOAN_OUT -> R.string.core_kind_loan_out
    TransactionKind.LOAN_IN -> R.string.core_kind_loan_in
    TransactionKind.UNKNOWN -> R.string.core_kind_unknown
})
