@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.hootkit.core.SortOrder
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionQuery
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionsUiState
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionsViewModel
import io.github.hootkit_dev.hootbook.presentation.core.transactions.displayLabel
import io.github.hootkit_dev.hootbook.presentation.ui.components.DateField
import io.github.hootkit_dev.hootbook.presentation.ui.components.FormSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the ledger shows and in which order. Every group is a set of chips; none selected means any. */
@Composable
fun TransactionsFilterBottomSheet(state: CoreTransactionsUiState, onDismiss: () -> Unit, onApply: (CoreTransactionQuery) -> Unit) {
    var query by remember(state.query) { mutableStateOf(state.query.copy(kind = null, kinds = (state.query.kinds + listOfNotNull(state.query.kind)).distinct(),
        accountId = null, accountIds = (state.query.accountIds + listOfNotNull(state.query.accountId)).distinct(),
        categoryId = null, categoryIds = (state.query.categoryIds + listOfNotNull(state.query.categoryId)).distinct())) }
    // The date range is shown inclusive; the query keeps its exclusive end.
    val to = query.toDateExclusive.takeIf { it.isNotBlank() }?.let { runCatching { java.time.LocalDate.parse(it).minusDays(1).toString() }.getOrNull() }.orEmpty()
    FormSheet(
        title = stringResource(R.string.txn_filter), onDismiss = onDismiss,
        onConfirm = { onApply(query); onDismiss() }, confirmLabel = stringResource(R.string.apply),
        titleAction = { TextButton(onClick = { query = CoreTransactionQuery(text = query.text, deleted = query.deleted) }) { Text(stringResource(R.string.reset)) } },
    ) {
        ChipGroup(stringResource(R.string.txn_sort)) {
            listOf(SortOrder.NEWEST_FIRST to R.string.sort_date_newest, SortOrder.OLDEST_FIRST to R.string.sort_date_oldest,
                SortOrder.LARGEST_FIRST to R.string.sort_amount_highest).forEach { (sort, label) ->
                FilterChip(query.sort == sort, { query = query.copy(sort = sort) }, label = { Text(stringResource(label)) })
            }
        }
        ChipGroup(stringResource(R.string.txn_kind)) {
            TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.forEach { kind ->
                FilterChip(kind in query.kinds, { query = query.copy(kinds = query.kinds.toggle(kind)) }, label = { Text(kind.displayLabel()) })
            }
        }
        ChipGroup(stringResource(R.string.txn_date_range)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(query.fromDate, stringResource(R.string.txn_date_from), { query = query.copy(fromDate = it) }, Modifier.weight(1f),
                    emptyText = stringResource(R.string.txn_any))
                DateField(to, stringResource(R.string.txn_date_to), { value ->
                    query = query.copy(toDateExclusive = value.takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it).plusDays(1).toString() }.orEmpty())
                }, Modifier.weight(1f), emptyText = stringResource(R.string.txn_any))
            }
        }
        if (state.accounts.isNotEmpty()) ChipGroup(stringResource(R.string.txn_accounts)) {
            state.accounts.forEach { account ->
                FilterChip(account.id in query.accountIds, { query = query.copy(accountIds = query.accountIds.toggle(account.id)) }, label = { Text(account.name) })
            }
        }
        if (state.categories.isNotEmpty()) ChipGroup(stringResource(R.string.txn_categories)) {
            state.categories.filter { !it.hidden || it.id in query.categoryIds }.forEach { category ->
                FilterChip(category.id in query.categoryIds, { query = query.copy(categoryIds = query.categoryIds.toggle(category.id)) },
                    label = { Text(category.displayName) })
            }
        }
        val currencies = (state.accounts.flatMap { it.pockets.map { pocket -> pocket.currency } } + state.mainCurrency +
            listOfNotNull(query.currency.ifBlank { null })).distinct().sorted()
        if (currencies.size > 1) ChipGroup(stringResource(R.string.txn_currency)) {
            currencies.forEach { currency ->
                FilterChip(currency == query.currency, { query = query.copy(currency = if (query.currency == currency) "" else currency) }, label = { Text(currency) })
            }
        }
    }
}

@Composable
private fun ChipGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(title, indent = 0.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private fun <T> List<T>.toggle(value: T) = if (value in this) this - value else this + value

/**
 * Exports what the ledger currently matches to CSV, then offers to save the file or share it.
 * The export starts when the sheet opens and stops if it is closed first.
 */
@Composable
internal fun ExportSheet(state: CoreTransactionsUiState, model: CoreTransactionsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!state.exporting) model.exportCsv() }
    val file = state.exportFile.takeIf { !state.exporting }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && file != null) scope.launch {
            failed = false
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Output unavailable") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }
    FormSheet(
        title = stringResource(R.string.export_transactions),
        onDismiss = { if (state.exporting) model.cancelExport(); onDismiss() },
        onConfirm = { save.launch("transactions.csv") }, confirmLabel = stringResource(R.string.txn_save_file),
        confirmEnabled = file != null, error = if (failed) stringResource(R.string.txn_export_failed) else null,
    ) {
        Text(stringResource(R.string.txn_export_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (file == null) stringResource(R.string.txn_exporting) else stringResource(R.string.txn_exported, state.exportCount.toString()),
            style = MaterialTheme.typography.titleMedium)
        if (file != null) OutlinedButton(onClick = {
            failed = false
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                check(uri.path?.startsWith("/core_attachment_share/") == true)
                val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = android.content.ClipData.newRawUri("transactions.csv", uri)
                context.startActivity(Intent.createChooser(send, null))
            }.onFailure { failed = true }
        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.core_attachment_share)) }
    }
}
