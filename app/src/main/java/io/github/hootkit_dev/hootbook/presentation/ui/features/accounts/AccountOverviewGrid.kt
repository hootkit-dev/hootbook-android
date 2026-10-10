package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.presentation.ui.components.softShadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter

/**
 * The figure an overview row shows on its right: the amount (what credit cards owe reads
 * "Owed ¥…"), or why there is none.
 */
@Composable
private fun overviewValue(item: AccountOverviewItem): String = when (item.status) {
    OverviewStatus.READY -> {
        val amount = CurrencyFormatter.formatCurrency(item.amount!!.abs(), item.currency)
        when {
            item.category != AccountCategory.CREDIT_CARDS -> CurrencyFormatter.formatCurrency(item.amount, item.currency)
            item.amount.signum() > 0 -> stringResource(R.string.overview_owed, amount)
            item.amount.signum() < 0 -> stringResource(R.string.overview_overpaid, amount)
            else -> amount
        }
    }
    OverviewStatus.CONNECT -> stringResource(R.string.overview_connect_short)
    OverviewStatus.LOADING -> "—"
    OverviewStatus.UNAVAILABLE -> stringResource(R.string.overview_unavailable)
    OverviewStatus.MULTIPLE_SOURCES -> stringResource(R.string.overview_sources)
}

private fun AccountCategory.overviewIcon() = when (this) {
    AccountCategory.WALLETS -> Icons.Rounded.AccountBalanceWallet
    AccountCategory.BANKS -> Icons.Rounded.AccountBalance
    AccountCategory.CREDIT_CARDS -> Icons.Rounded.CreditCard
    AccountCategory.INVESTMENTS -> Icons.AutoMirrored.Rounded.ShowChart
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountOverviewList(items: List<AccountOverviewItem>, onOpen: (AccountCategory) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag("account_overview_list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { index, item ->
                val value = when(item.status) {
                    OverviewStatus.READY -> {
                        val debt = item.category == AccountCategory.CREDIT_CARDS
                        val contribution = item.netWorthContribution()
                        val sign = if (!debt || contribution?.signum() == 0) "" else if (contribution!!.signum() < 0) "−" else "+"
                        sign + (if (item.converted) "≈" else "") + CurrencyFormatter.formatCurrency(if (debt) item.amount!!.abs() else item.amount!!, item.currency)
                    }
                    OverviewStatus.CONNECT -> stringResource(R.string.overview_connect)
                    OverviewStatus.LOADING -> "—"
                    OverviewStatus.UNAVAILABLE -> stringResource(R.string.overview_unavailable)
                    OverviewStatus.MULTIPLE_SOURCES -> stringResource(R.string.overview_sources)
                }
                val supporting = when {
                    item.status == OverviewStatus.CONNECT -> stringResource(R.string.overview_connect_hint)
                    item.status == OverviewStatus.LOADING -> stringResource(R.string.overview_loading)
                    else -> stringResource(R.string.overview_count, item.count) + " · " + stringResource(when {
                        item.category == AccountCategory.CREDIT_CARDS -> if (item.amount?.signum() == -1) R.string.overview_credit_refund else R.string.overview_deduction
                        item.isSnapshot -> R.string.overview_market_value
                        else -> R.string.overview_balance
                    }) + if (item.status == OverviewStatus.READY) " · ${item.currency}" else ""
                }
                Surface(onClick = { onOpen(item.category) }, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().testTag("overview_${item.category.name}")) {
                    WalletStyleRow(
                        title = stringResource(item.category.titleRes), amount = value, subtitle = supporting,
                        icon = { Icon(when(item.category) {
                            AccountCategory.WALLETS -> Icons.Rounded.AccountBalanceWallet
                            AccountCategory.BANKS -> Icons.Rounded.AccountBalance
                            AccountCategory.CREDIT_CARDS -> Icons.Rounded.CreditCard
                            AccountCategory.INVESTMENTS -> Icons.AutoMirrored.Rounded.ShowChart
                        }, null, Modifier.size(20.dp)) }
                    )
                }
            }
            if (items.any { it.hasSnapshots }) Text(stringResource(R.string.overview_snapshot_note), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Summary first, then one grouped list. No independently rounded category cards. */
@Composable
internal fun AccountOverviewPanel(
    items: List<AccountOverviewItem>, onOpen: (AccountCategory) -> Unit,
    modifier: Modifier = Modifier, header: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth().testTag("networth_accounts_panel"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { header() }
        Text(stringResource(R.string.overview_categories), Modifier.padding(start = 4.dp, top = 8.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AccountOverviewList(items, onOpen)
    }
}
