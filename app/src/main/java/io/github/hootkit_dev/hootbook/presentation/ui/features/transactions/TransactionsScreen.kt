@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.navigation.HideHostChrome
import io.github.hootkit_dev.hootbook.presentation.core.transactions.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.utils.DateFormats
import kotlinx.coroutines.delay
import java.time.LocalDate as JavaDate

/**
 * The ledger: search, filters and periods, totals, then transactions grouped by day. A long
 * press starts selection; the bar then carries the actions for the selected transactions.
 */
@Composable
fun TransactionsScreen(state: CoreTransactionsUiState, model: CoreTransactionsViewModel,
    onBack: () -> Unit, onOpenTransaction: (String) -> Unit, showBackButton: Boolean = false) {
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    var exportSheet by rememberSaveable { mutableStateOf(false) }
    var dateRange by rememberSaveable { mutableStateOf(false) }
    var bulkEditor by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf<BulkAction?>(null) }
    var selection by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.query.text)) }
    val selecting = selection || state.selectedIds.isNotEmpty()
    val recycleBin = state.query.deleted == DeletedFilter.ONLY
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        last >= 0 && last >= listState.layoutInfo.totalItemsCount - 5
    } }
    LaunchedEffect(nearEnd, state.page?.items?.size, state.loading) { if (nearEnd && !state.loading) model.loadMore() }
    // Scrolling the results or opening a sheet ends typing in the search field, so the keyboard
    // does not cover the list (or come back when the sheet closes).
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) focus.clearFocus() }
    LaunchedEffect(exportSheet, bulkEditor) { if (exportSheet || bulkEditor) focus.clearFocus() }
    val snackbar = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.transaction_deleted)
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(state.undoToken) {
        if (state.undoToken != null && snackbar.showSnackbar(deletedText, undoText, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) model.undo()
    }
    LaunchedEffect(search.text) { if (search.text != state.query.text) { delay(250); model.applyQuery(state.query.copy(text = search.text)) } }
    LaunchedEffect(state.query.text) { if (search.text != state.query.text) search = TextFieldValue(state.query.text) }
    LaunchedEffect(state.selectedIds.isEmpty()) { if (state.selectedIds.isEmpty()) bulkEditor = false }
    val leaveSelection = { selection = false; model.clearSelection() }
    val leaveRecycleBin = { model.applyQuery(state.query.copy(deleted = DeletedFilter.EXCLUDE)) }
    BackHandler(selecting) { leaveSelection() }
    HideHostChrome(selecting)
    BackHandler(!selecting && recycleBin) { leaveRecycleBin() }

    SecondaryScreen(
        title = when {
            selecting -> stringResource(R.string.txn_selected, state.selectedIds.size)
            recycleBin -> stringResource(R.string.txn_recycle_bin)
            else -> stringResource(R.string.transactions)
        },
        onBack = when {
            selecting -> leaveSelection
            recycleBin -> leaveRecycleBin
            showBackButton -> onBack
            else -> null
        },
        backIcon = if (selecting) Icons.Rounded.Close else null,
        backLabel = if (selecting) stringResource(R.string.close) else null,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            val enabled = !state.writing
            if (selecting) {
                TooltipIconButton(Icons.Rounded.SelectAll, stringResource(R.string.select_all), model::selectAllMatching, enabled = enabled)
                if (!recycleBin) TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.core_full_bulk_edit), { bulkEditor = true },
                    enabled = enabled && state.selectedIds.isNotEmpty())
                TooltipIconButton(if (recycleBin) Icons.Rounded.DeleteForever else Icons.Rounded.Delete,
                    stringResource(if (recycleBin) R.string.txn_purge else R.string.txn_delete_selected),
                    { confirm = if (recycleBin) BulkAction.PURGE else BulkAction.DELETE }, enabled = enabled && state.selectedIds.isNotEmpty())
            } else {
                if (!recycleBin) TooltipIconButton(Icons.Rounded.IosShare, stringResource(R.string.export_transactions), { exportSheet = true })
                OverflowMenu(listOfNotNull(
                    MenuAction(stringResource(R.string.txn_select), Icons.Rounded.Checklist) { selection = true },
                    if (recycleBin) MenuAction(stringResource(R.string.txn_empty_recycle), Icons.Rounded.DeleteForever, destructive = true,
                        enabled = state.page?.total?.let { it > 0u } == true) { confirm = BulkAction.EMPTY_RECYCLE_BIN }
                    else MenuAction(stringResource(R.string.txn_recycle_bin), Icons.Rounded.Delete) {
                        model.applyQuery(state.query.copy(deleted = DeletedFilter.ONLY))
                    },
                ))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            SearchBarBox(searchQuery = search, onSearchQueryChange = { search = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.search_transactions)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (search.text.isNotEmpty()) TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.clear_search), { search = TextFieldValue("") }) })
            QueryChips(state.query, onFilter = { filterSheet = true }, onCustomRange = { dateRange = true }, apply = model::applyQuery)
            state.error?.let { ErrorBanner(it, model::clearError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("transactions_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 150.dp)) {
                state.summary?.takeIf { !recycleBin }?.let { summary -> item(key = "totals") { LedgerTotals(summary, state.customSymbols, Modifier.padding(bottom = 16.dp)) } }
                val rows = state.page?.items.orEmpty()
                if (!state.loading && rows.isEmpty()) item(key = "empty") { EmptyLedger { model.applyQuery(CoreTransactionQuery()); search = TextFieldValue("") } }
                rows.withIndex().groupBy { (_, row) -> row.dateTime.let { JavaDate.of(it.year, it.month.toInt(), it.day.toInt()) } }.forEach { (date, day) ->
                    stickyHeader(key = "date_${day.first().value.id}") { DayHeader(date) }
                    day.forEachIndexed { position, (_, transaction) ->
                        item(key = transaction.id, contentType = "transaction") {
                            TransactionItem(transaction, account = state.accounts.find { it.id == transaction.accountId },
                                category = state.categories.find { it.id == transaction.categoryId },
                                targetAccount = state.accounts.find { it.id == transaction.toAccountId }, currentAccountId = state.query.accountId,
                                showDate = false, isSelectionMode = selecting, isSelected = transaction.id in state.selectedIds,
                                onSelectionToggle = { model.toggleSelected(transaction.id) },
                                onLongClick = { selection = true; model.toggleSelected(transaction.id) },
                                onClick = { onOpenTransaction(transaction.id) },
                                convertedMoney = state.displayValues[transaction.id], customSymbols = state.customSymbols,
                                merchantIcon = model.platformAssets?.merchantIcon(transaction.merchant),
                                shape = ListItemPosition.from(position, day.size).toShape(),
                                modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                }
                state.page?.takeIf { it.items.size.toUInt() < it.total }?.let { item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = model::loadMore, enabled = !state.loading) { Text(stringResource(R.string.txn_load_more)) }
                    }
                } }
            }
        }
    }
    if (filterSheet) TransactionsFilterBottomSheet(state, { filterSheet = false }, model::applyQuery)
    if (exportSheet) ExportSheet(state, model) { exportSheet = false }
    if (bulkEditor) BatchEditTransactionsBottomSheet(state, { bulkEditor = false }) { form -> model.bulkUpdate(form) }
    if (dateRange) DateRangePickerDialog(onDismiss = { dateRange = false }, onConfirm = { from, to ->
        model.applyQuery(state.query.copy(fromDate = from.toString(), toDateExclusive = to.plusDays(1).toString())); dateRange = false }, blurEffects = false)
    confirm?.let { action ->
        val count = state.selectedIds.size
        ConfirmDialog(
            title = when (action) {
                BulkAction.DELETE -> stringResource(R.string.txn_delete_selected_title, count)
                BulkAction.PURGE -> stringResource(R.string.txn_purge_title, count)
                BulkAction.EMPTY_RECYCLE_BIN -> stringResource(R.string.txn_empty_recycle)
            },
            text = stringResource(when (action) {
                BulkAction.DELETE -> R.string.txn_delete_selected_note
                BulkAction.PURGE -> R.string.txn_purge_note
                BulkAction.EMPTY_RECYCLE_BIN -> R.string.txn_empty_recycle_note
            }),
            confirmLabel = stringResource(if (action == BulkAction.DELETE) R.string.delete else R.string.txn_purge),
            onConfirm = {
                when (action) {
                    BulkAction.DELETE -> model.deleteSelected()
                    BulkAction.PURGE -> model.purgeSelected()
                    BulkAction.EMPTY_RECYCLE_BIN -> model.purgeSelected(allRecycleBin = true)
                }
                confirm = null; selection = false
            },
            onDismiss = { confirm = null }, destructive = true, enabled = !state.writing,
        )
    }
}

private enum class BulkAction { DELETE, PURGE, EMPTY_RECYCLE_BIN }

/** Filter (with how many are set), quick periods and a custom range, one row that scrolls. */
@Composable
private fun QueryChips(query: CoreTransactionQuery, onFilter: () -> Unit, onCustomRange: () -> Unit, apply: (CoreTransactionQuery) -> Unit) {
    val active = query.activeFilterCount()
    val periods = periodChoices()
    val custom = (query.fromDate.isNotEmpty() || query.toDateExclusive.isNotEmpty()) && periods.none { it.matches(query) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(active > 0, onFilter,
            label = { Text(if (active > 0) stringResource(R.string.txn_filter_count, active) else stringResource(R.string.txn_filter)) },
            leadingIcon = { Icon(Icons.Rounded.FilterList, null, Modifier.size(FilterChipDefaults.IconSize)) }) }
        items(periods) { period -> FilterChip(period.matches(query), { apply(query.copy(fromDate = period.from, toDateExclusive = period.to)) },
            label = { Text(stringResource(period.label)) }) }
        item { FilterChip(custom, onCustomRange, label = { Text(stringResource(R.string.time_period_custom_range)) },
            leadingIcon = { Icon(Icons.Rounded.DateRange, null, Modifier.size(FilterChipDefaults.IconSize)) }) }
    }
}

@Composable
private fun DayHeader(date: JavaDate) {
    val label = when (date) {
        JavaDate.now() -> stringResource(R.string.today_lbl)
        JavaDate.now().minusDays(1) -> stringResource(R.string.yesterday_lbl)
        else -> DateFormats.shortDate(date)
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyLedger(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.no_transactions_found), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
    }
}

private data class PeriodChoice(val label: Int, val from: String, val to: String) {
    fun matches(query: CoreTransactionQuery) = query.fromDate == from && query.toDateExclusive == to
}
private fun periodChoices(): List<PeriodChoice> {
    val today = JavaDate.now(); val month = today.withDayOfMonth(1)
    return listOf(PeriodChoice(R.string.time_period_all_time, "", ""), PeriodChoice(R.string.time_period_this_month, month.toString(), month.plusMonths(1).toString()),
        PeriodChoice(R.string.time_period_last_month, month.minusMonths(1).toString(), month.toString()), PeriodChoice(R.string.today_lbl, today.toString(), today.plusDays(1).toString()))
}
internal fun CoreTransactionQuery.activeFilterCount() = listOf(accountIds.isNotEmpty() || accountId != null, categoryIds.isNotEmpty() || categoryId != null,
    kinds.isNotEmpty() || kind != null, currency.isNotBlank(), sort != SortOrder.NEWEST_FIRST).count { it }
