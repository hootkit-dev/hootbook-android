package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation

@Composable
fun accountSubtitle(account: Account): String = listOfNotNull(stringResource(when (account.kind) {
    AccountKind.WALLET -> R.string.type_wallet
    AccountKind.BANK -> R.string.type_bank_account
    AccountKind.CREDIT_CARD -> R.string.type_credit_card
    AccountKind.INVESTMENT -> R.string.type_investment_account
    AccountKind.UNKNOWN -> R.string.account_kind_unknown
}), account.last4?.takeIf { it.isNotBlank() }?.let { "•••• $it" }).joinToString(" · ")
