@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.*
import io.github.hootkit_dev.hootbook.presentation.core.transactions.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.*

@Composable
fun TransactionDetailScreen(state: CoreTransactionsUiState, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onMerchantIcon: (String) -> Unit = {}, onSubscription: (String) -> Unit = {}, onDebt: () -> Unit = {},
    merchantIcon: Any? = null, actions: @Composable () -> Unit = {}) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val transaction = state.detail
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.transaction_details)) },
        navigationIcon = { IconButton(onClick = onBack, enabled = !state.writing) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.core_ui_back)) } },
        actions = {
            IconButton(onClick = onEdit, enabled = transaction != null && !state.writing && !transaction.deleted && transaction.kind != TransactionKind.UNKNOWN) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit_transaction)) }
            IconButton(onClick = { confirmDelete = true }, enabled = transaction != null && !state.writing && !transaction.deleted) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) }
        }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.loading || state.writing) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (transaction == null && !state.loading) Text(stringResource(R.string.core_full_label_4))
                transaction?.let { row ->
                    TransactionDetailContent(row, state, onMerchantIcon, merchantIcon)
                    row.subscriptionId?.let { id -> FilledTonalButton(onClick = { onSubscription(id) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Repeat, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.core_full_open_subscription)) } }
                    row.debtId?.let { FilledTonalButton(onClick = onDebt, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_full_label_22)) } }
                    actions()
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.core_full_label_15)) },
        text = { Text(stringResource(R.string.core_full_label_16)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !state.writing) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun TransactionDetailContent(transaction: Transaction, state: CoreTransactionsUiState, onMerchantIcon: (String) -> Unit = {}, merchantIcon: Any? = null) {
    val brandIcon = io.github.hootkit_dev.hootbook.presentation.common.icons.BrandIcons.getIconResource(transaction.merchant)
    val category = state.categories.find { it.id == transaction.categoryId }
    val child = category?.subcategories?.find { it.id == transaction.subcategoryId }
    val account = state.accounts.find { it.id == transaction.accountId }
    val target = state.accounts.find { it.id == transaction.toAccountId }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(80.dp).clickable(enabled = transaction.merchant.isNotBlank()) { onMerchantIcon(transaction.merchant) }) {
            Box(contentAlignment = Alignment.Center) {
                if (merchantIcon != null || brandIcon != null) coil3.compose.AsyncImage(merchantIcon ?: brandIcon, null, Modifier.size(48.dp))
                else CoreRecordIcon(child?.icon ?: category?.icon ?: account?.icon.orEmpty(), Modifier.size(48.dp))
            }
        }
        Text(transaction.merchant.ifBlank { category?.displayName ?: transaction.kind.displayLabel() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(CoreMoneyPresentation.format(transaction.amount, transaction.currency, state.customSymbols), style = MaterialTheme.typography.displaySmall, color = transactionColor(transaction.kind), modifier = Modifier.testTag("detail_amount"))
        state.displayValues[transaction.id]?.let { value -> Text(CoreMoneyPresentation.format(value.amount, value.currency, state.customSymbols), style = MaterialTheme.typography.titleMedium)
            if (!value.complete) Text(stringResource(R.string.core_full_conversion_missing), color = MaterialTheme.colorScheme.error) }
        AssistChip(onClick = {}, label = { Text(transaction.kind.displayLabel()) })
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            DetailRow(stringResource(R.string.date), transaction.dateTime.inputText())
            DetailRow(stringResource(R.string.account), account?.name ?: stringResource(R.string.core_full_label_58))
            if (transaction.kind == TransactionKind.TRANSFER) {
                DetailRow(stringResource(R.string.core_full_label_61), target?.name.orEmpty())
                val incomingCurrency = transaction.toCurrency ?: transaction.currency
                val incoming = transaction.toAmount ?: transaction.amount.takeIf { incomingCurrency == transaction.currency }
                DetailRow(stringResource(R.string.transfer_received_amount, incomingCurrency), CoreMoneyPresentation.format(incoming, incomingCurrency, state.customSymbols))
            }
            category?.let { DetailRow(stringResource(R.string.category), it.displayName) }
            child?.let { DetailRow(stringResource(R.string.subcategory), it.displayName) }
            if (transaction.notes?.isNotBlank() == true) DetailRow(stringResource(R.string.notes), transaction.notes.orEmpty())
        }
    }
    if (transaction.deleted) Text(stringResource(R.string.core_full_label_11), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TransactionSummarySheet(transaction: Transaction, state: CoreTransactionsUiState, onDismiss: () -> Unit, onOpenDetails: () -> Unit) {
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TransactionDetailContent(transaction, state)
            Button(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.transaction_details)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
