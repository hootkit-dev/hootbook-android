@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.presentation.format.displayTitle
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.formatAmount
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Local inspection preserves the list's route, selection and scroll position. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionSummarySheet(
    transaction: Transaction,
    categories: Map<String, Category>,
    accounts: Map<String, Account>,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    HootbookModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandIcon(merchantName = transaction.merchant, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(transaction.displayTitle(categories[transaction.categoryId]?.displayName), style = MaterialTheme.typography.titleLarge)
                    Text(transaction.formatAmount(), style = MaterialTheme.typography.headlineMedium)
                }
            }
            SummaryField(stringResource(R.string.currency_label), transaction.currency)
            SummaryField(stringResource(R.string.date), transaction.dateTime.toJavaDateTime().format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
            ))
            categories[transaction.categoryId]?.displayName?.let { SummaryField(stringResource(R.string.category), it) }
            SummaryField(stringResource(R.string.account),
                listOfNotNull(accounts[transaction.accountId]?.name, accounts[transaction.accountId]?.last4).filter { it.isNotBlank() }.joinToString(" · "))
            accounts[transaction.toAccountId]?.let { SummaryField(stringResource(R.string.to_destination), listOfNotNull(it.name, it.last4).filter { text -> text.isNotBlank() }.joinToString(" · ")) }
            transaction.notes?.takeIf { it.isNotBlank() }?.let {
                SummaryField(stringResource(R.string.description_label), it)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.close)) }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onOpenDetails) { Text(stringResource(R.string.transaction_details)) }
            }
        }
    }
}

@Composable
private fun SummaryField(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
