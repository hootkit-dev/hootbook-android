package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hootkit.core.LedgerSummary
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation

/** Income, expense and net in the main currency, then each currency when there are several. */
@Composable
fun LedgerTotals(summary: LedgerSummary, symbols: Map<String, String>, modifier: Modifier = Modifier, count: UInt? = null) {
    val colors = transactionColor(TransactionKind.INCOME) to transactionColor(TransactionKind.EXPENSE)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(shape = ListItemPosition.from(0, if (summary.perCurrency.size > 1) 2 else 1).toShape(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                if (count != null) Text(stringResource(R.string.core_analytics_count, count.toString()), Modifier.padding(start = 16.dp, top = 16.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Total(stringResource(R.string.txn_income), CoreMoneyPresentation.format(summary.income, summary.currency, symbols), colors.first, Modifier.weight(1f))
                    Total(stringResource(R.string.txn_expense), CoreMoneyPresentation.format(summary.expense, summary.currency, symbols), colors.second, Modifier.weight(1f))
                    Total(stringResource(R.string.txn_net), CoreMoneyPresentation.format(summary.net, summary.currency, symbols), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
        }
        if (summary.perCurrency.size > 1) Surface(shape = ListItemPosition.Bottom.toShape(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.txn_totals_by_currency), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                summary.perCurrency.forEach { totals ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(totals.currency, Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
                        Text("+" + CoreMoneyPresentation.format(totals.income, totals.currency, symbols), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.first)
                        Text("−" + CoreMoneyPresentation.format(totals.expense, totals.currency, symbols), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.second)
                    }
                }
            }
        }
        if (!summary.complete) Text(stringResource(R.string.txn_totals_incomplete, summary.missingCurrencies.joinToString()),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** One total: label, then the amount shrunk to fit its third of the row rather than cut off. */
@Composable
private fun Total(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicText(amount, maxLines = 1, style = MaterialTheme.typography.titleMedium.copy(color = color, fontFeatureSettings = "tnum"),
            autoSize = TextAutoSize.StepBased(minFontSize = MaterialTheme.typography.labelSmall.fontSize, maxFontSize = MaterialTheme.typography.titleMedium.fontSize))
    }
}
