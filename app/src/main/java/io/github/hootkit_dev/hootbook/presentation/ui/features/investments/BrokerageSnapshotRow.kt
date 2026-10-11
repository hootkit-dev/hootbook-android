package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.hootkit.core.BrokerageAccount
import dev.hootkit.core.BrokerageConnection
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem

/** Show Core's per-account snapshot totals, including connections without a ledger link. */
@Composable
internal fun BrokerageSnapshotRow(connection: BrokerageConnection, account: BrokerageAccount, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.testTag("broker_connection_${connection.id}_${account.accountId}"),
        headline = { Text(connection.label) },
        supporting = { Column {
            Text(account.accountId + " · " + account.asOf.coreDateText())
            if (!account.complete) Text(stringResource(R.string.core_complete_incomplete), style = MaterialTheme.typography.labelSmall)
        } },
        leading = { Icon(Icons.Rounded.Link, null) },
        trailing = { Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            val total = account.total
            if (total != null) Text(CoreMoneyPresentation.formatCurrency(total.amount, total.currency))
            else account.valueByCurrency.forEach { Text(CoreMoneyPresentation.formatCurrency(it.amount, it.currency)) }
        } },
        padding = PaddingValues(),
        onClick = onClick,
    )
}
