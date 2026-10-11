package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hootkit.core.AccountsOverview
import io.github.hootkit_dev.hootbook.R

/** Core has no category totals; these original navigation rows retain the account counts. */
@Composable
internal fun AccountOverviewList(overview: AccountsOverview?, onOpen: (AccountCategory) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag("account_overview_list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AccountCategory.entries.forEach { category ->
            val count = overview?.accounts?.count { it.category() == category }
            Surface(onClick = { onOpen(category) }, color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().testTag("overview_${category.name}")) {
                WalletStyleRow(title = stringResource(category.titleRes), amount = "",
                    subtitle = count?.let { stringResource(R.string.overview_count, it) } ?: "—",
                    icon = { Icon(when (category) {
                        AccountCategory.WALLETS -> Icons.Rounded.AccountBalanceWallet
                        AccountCategory.BANKS -> Icons.Rounded.AccountBalance
                        AccountCategory.CREDIT_CARDS -> Icons.Rounded.CreditCard
                        AccountCategory.INVESTMENTS -> Icons.AutoMirrored.Rounded.ShowChart
                    }, null, Modifier.size(20.dp)) })
            }
        }
    }
}

@Composable
internal fun AccountOverviewPanel(overview: AccountsOverview?, onOpen: (AccountCategory) -> Unit,
    modifier: Modifier = Modifier, header: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().testTag("networth_accounts_panel"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { header() }
        Text(stringResource(R.string.overview_categories), Modifier.padding(start = 4.dp, top = 8.dp),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AccountOverviewList(overview, onOpen)
    }
}
