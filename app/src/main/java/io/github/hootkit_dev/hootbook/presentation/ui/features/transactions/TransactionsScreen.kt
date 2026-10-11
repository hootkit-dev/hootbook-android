@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.transactions.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import kotlinx.coroutines.delay
import java.time.LocalDate as JavaDate

@Composable
fun TransactionsScreen(state: CoreTransactionsUiState, model: CoreTransactionsViewModel,
    onBack: () -> Unit, onOpenTransaction: (String) -> Unit,
    showSummarySheet: Boolean = true, showBackButton: Boolean = false,
    actions: @Composable () -> Unit = {}) {
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    var dateRange by rememberSaveable { mutableStateOf(false) }
    var actionSheet by rememberSaveable { mutableStateOf(false) }
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf(false) }
    var summaryId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.query.text)) }
    val selecting = selection || state.selectedIds.isNotEmpty()
    val small = TopAppBarDefaults.pinnedScrollBehavior()
    val large = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        last >= 0 && last >= listState.layoutInfo.totalItemsCount - 5
    } }
    LaunchedEffect(nearEnd, state.page?.items?.size, state.loading) {
        if (nearEnd && !state.loading) model.loadMore()
    }
    val snackbar = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.transaction_deleted)
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(state.undoToken) {
        if (state.undoToken != null && snackbar.showSnackbar(deletedText, undoText, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) model.undo()
    }
    LaunchedEffect(search.text) { if (search.text != state.query.text) { delay(250); model.applyQuery(state.query.copy(text = search.text)) } }
    LaunchedEffect(state.query.text) { if (search.text != state.query.text) search = TextFieldValue(state.query.text) }
    BackHandler(selecting) { selection = false; model.clearSelection() }
    Scaffold(modifier = Modifier.fillMaxSize().nestedScroll(large.nestedScrollConnection),
        topBar = { CustomTitleTopAppBar(scrollBehaviorSmall = small, scrollBehaviorLarge = large,
            title = if (selecting) stringResource(R.string.items_selected_format, state.selectedIds.size) else stringResource(R.string.transactions),
            hasBackButton = selecting || showBackButton,
            navigationContent = { IconButton(onClick = { if (selecting) { selection = false; model.clearSelection() } else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.core_ui_back)) } },
            actionContent = {
                if (selecting) {
                    IconButton(onClick = model::selectAllMatching, enabled = !state.writing) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.select_all)) }
                    IconButton(onClick = { actionSheet = true }, enabled = state.selectedIds.isNotEmpty()) { Icon(Icons.Rounded.Edit, stringResource(R.string.core_full_bulk_edit)) }
                } else {
                    IconButton(onClick = { model.exportCsv(); actionSheet = true }) { Icon(Icons.Rounded.IosShare, stringResource(R.string.export_transactions)) }
                    IconButton(onClick = model::refresh, enabled = !state.loading) { Icon(Icons.Rounded.Refresh, stringResource(R.string.core_full_label_2)) }
                }
            }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            SearchBarBox(searchQuery = search, onSearchQueryChange = { search = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.search_transactions)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { Row {
                    if (search.text.isNotEmpty()) IconButton(onClick = { search = TextFieldValue("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_search)) }
                    Box { IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.more_options)) }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.filters)) }, onClick = { filterSheet = true; sortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, onClick = { selection = true; model.selectAllMatching(); sortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.core_full_label_50)) }, onClick = { model.applyQuery(state.query.copy(deleted = DeletedFilter.ONLY)); sortMenu = false })
                            HorizontalDivider()
                            listOf(R.string.sort_amount_lowest, R.string.sort_merchant_az, R.string.sort_merchant_za).forEach { label ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {}, enabled = false)
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.transaction_sort_unavailable), style = MaterialTheme.typography.bodySmall) }, onClick = {}, enabled = false)
                            transactionSortOptions().forEach { (sort, label) -> DropdownMenuItem(text = { Text(label) }, leadingIcon = { RadioButton(state.query.sort == sort, null) }, onClick = { model.applyQuery(state.query.copy(sort = sort)); sortMenu = false }) }
                        }
                    }
                } })
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(state.query.activeFilterCount() > 0, { filterSheet = true }, label = { Text(stringResource(R.string.filters) + if (state.query.activeFilterCount() > 0) " (${state.query.activeFilterCount()})" else "") }, leadingIcon = { Icon(Icons.Rounded.FilterList, null, Modifier.size(18.dp)) }) }
                items(periodChoices()) { period -> FilterChip(state.query.fromDate == period.from && state.query.toDateExclusive == period.to, { model.applyQuery(state.query.copy(fromDate = period.from, toDateExclusive = period.to)) }, label = { Text(stringResource(period.label)) }) }
                item { FilterChip(false, { dateRange = true }, label = { Text(stringResource(R.string.time_period_custom_range)) }) }
            }
            state.error?.let { error -> Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(error, Modifier.weight(1f)); TextButton(onClick = model::clearError) { Text(stringResource(R.string.core_attachment_close)) } }
            } }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("transactions_list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.summary?.let { summary -> item(key = "totals") {
                    TransactionTotalsCard(income = summary.income, expenses = summary.expense, netBalance = summary.net, currency = summary.currency, isEstimated = !summary.complete, isLoading = state.loading)
                    if (summary.perCurrency.size > 1) {
                        Text(stringResource(R.string.transaction_currency_summary), style = MaterialTheme.typography.labelLarge)
                        summary.perCurrency.forEach { totals ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(totals.currency, style = MaterialTheme.typography.labelMedium)
                                Text("+ " + CoreMoneyPresentation.format(totals.income, totals.currency, state.customSymbols), style = MaterialTheme.typography.labelMedium)
                                Text("− " + CoreMoneyPresentation.format(totals.expense, totals.currency, state.customSymbols), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (!summary.complete) Text(stringResource(R.string.core_full_missing_currencies, summary.missingCurrencies.joinToString()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } }
                val rows = state.page?.items.orEmpty()
                if (!state.loading && rows.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.no_transactions_found), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { model.applyQuery(CoreTransactionQuery()); search = TextFieldValue("") }) { Text(stringResource(R.string.reset)) }
                } }
                rows.forEachIndexed { index, transaction ->
                    val date = transaction.dateTime.let { JavaDate.of(it.year, it.month.toInt(), it.day.toInt()) }
                    val previousDate = rows.getOrNull(index - 1)?.dateTime?.let { JavaDate.of(it.year, it.month.toInt(), it.day.toInt()) }
                    if (previousDate != date) stickyHeader(key = "date_${transaction.id}") {
                        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { Text(transactionDateLabel(date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) }
                    }
                    item(key = transaction.id, contentType = "transaction") {
                        TransactionItem(transaction, account = state.accounts.find { it.id == transaction.accountId }, category = state.categories.find { it.id == transaction.categoryId },
                            targetAccount = state.accounts.find { it.id == transaction.toAccountId }, currentAccountId = state.query.accountId,
                            isSelectionMode = selecting, isSelected = transaction.id in state.selectedIds,
                            onSelectionToggle = { model.toggleSelected(transaction.id) }, onLongClick = { selection = true; model.toggleSelected(transaction.id) },
                            onClick = { if (showSummarySheet) summaryId = transaction.id else onOpenTransaction(transaction.id) },
                            convertedMoney = state.displayValues[transaction.id], customSymbols = state.customSymbols,
                            merchantIcon = model.platformAssets?.merchantIcon(transaction.merchant))
                    }
                }
                state.page?.let { page -> item(key = "pagination") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.transaction_page_position, if (page.total == 0u) "0" else "1", page.items.size.toString(), page.total.toString()), style = MaterialTheme.typography.bodySmall)
                        if (page.items.size.toUInt() < page.total) TextButton(onClick = model::loadMore, enabled = !state.loading) { Text(stringResource(R.string.transaction_load_more)) }
                    }
                } }
            }
        }
    }
    if (filterSheet) TransactionsFilterBottomSheet(state, { filterSheet = false }, model::applyQuery)
    if (dateRange) DateRangePickerDialog(onDismiss = { dateRange = false }, onConfirm = { from, to -> model.applyQuery(state.query.copy(fromDate = from.toString(), toDateExclusive = to.plusDays(1).toString())); dateRange = false }, blurEffects = false)
    summaryId?.let { id -> state.page?.items?.find { it.id == id }?.let { transaction ->
        TransactionSummarySheet(transaction, state, onDismiss = { summaryId = null }, onOpenDetails = { summaryId = null; onOpenTransaction(id) })
    } }
    if (actionSheet) HootbookModalBottomSheet(onDismissRequest = { actionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(20.dp)) { Text(stringResource(R.string.more_options), style = MaterialTheme.typography.headlineSmall); actions(); Spacer(Modifier.height(24.dp)) }
    }
}

private data class PeriodChoice(val label: Int, val from: String, val to: String)
private fun periodChoices(): List<PeriodChoice> {
    val today = JavaDate.now(); val month = today.withDayOfMonth(1)
    return listOf(PeriodChoice(R.string.time_period_all_time, "", ""), PeriodChoice(R.string.time_period_this_month, month.toString(), month.plusMonths(1).toString()),
        PeriodChoice(R.string.time_period_last_month, month.minusMonths(1).toString(), month.toString()), PeriodChoice(R.string.today_lbl, today.toString(), today.plusDays(1).toString()))
}
@Composable
private fun transactionDateLabel(date: JavaDate): String = if (date == JavaDate.now()) stringResource(R.string.today_lbl) else {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    date.format(java.time.format.DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy年M月d日" else "EEE, MMM d, yyyy", locale))
}
private fun CoreTransactionQuery.activeFilterCount() = listOf(accountIds.isNotEmpty() || accountId != null, categoryIds.isNotEmpty() || categoryId != null,
    kinds.isNotEmpty() || kind != null, currency.isNotBlank(), deleted != DeletedFilter.EXCLUDE).count { it }
@Composable
private fun transactionSortOptions() = listOf(SortOrder.NEWEST_FIRST to stringResource(R.string.sort_date_newest), SortOrder.OLDEST_FIRST to stringResource(R.string.sort_date_oldest), SortOrder.LARGEST_FIRST to stringResource(R.string.sort_amount_highest))
