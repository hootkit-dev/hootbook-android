package io.github.hootkit_dev.hootbook.presentation.core.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.R

@Composable
fun CoreKindLabel(kind:TransactionKind):String=stringResource(when(kind){
    TransactionKind.EXPENSE->R.string.core_detail_kind_expense;TransactionKind.INCOME->R.string.core_detail_kind_income
    TransactionKind.TRANSFER->R.string.core_detail_kind_transfer;TransactionKind.INVESTMENT->R.string.core_detail_kind_investment
    TransactionKind.LOAN_OUT->R.string.core_detail_kind_loan_out;TransactionKind.LOAN_IN->R.string.core_detail_kind_loan_in
    TransactionKind.UNKNOWN->R.string.core_detail_kind_unknown
})
