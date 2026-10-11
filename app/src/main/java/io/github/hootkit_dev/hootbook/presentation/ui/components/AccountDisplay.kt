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
    AccountKind.UNKNOWN -> R.string.core_accounts_34
}), account.last4?.takeIf { it.isNotBlank() }?.let { "•••• $it" }).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountCurrencyPills(account: Account, customSymbols: Map<String, String>, modifier: Modifier = Modifier) {
    if (account.pockets.size < 2) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        account.pockets.forEach { pocket ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pocket.currency, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CoreMoneyPresentation.format(pocket.balance, pocket.currency, customSymbols), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
