@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.utils.displayTitle
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
import io.github.hootkit_dev.hootbook.utils.formatAmount

import io.github.hootkit_dev.hootbook.data.database.entity.TransactionEntity
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Local inspection preserves the list's route, selection and scroll position. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionSummarySheet(
    transaction: TransactionEntity,
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
                BrandIcon(merchantName = transaction.merchantName, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(transaction.displayTitle(), style = MaterialTheme.typography.titleLarge)
                    Text(transaction.formatAmount(), style = MaterialTheme.typography.headlineMedium)
                }
            }
            SummaryField(stringResource(R.string.currency_label), transaction.currency)
            SummaryField(stringResource(R.string.date), transaction.dateTime.format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
            ))
            transaction.category?.let { SummaryField(stringResource(R.string.category), io.github.hootkit_dev.hootbook.presentation.common.categoryName(it)) }
            SummaryField(stringResource(R.string.account),
                listOfNotNull(transaction.bankName, transaction.accountNumber).filter { it.isNotBlank() }.joinToString(" · "))
            transaction.description?.takeIf { it.isNotBlank() }?.let {
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
