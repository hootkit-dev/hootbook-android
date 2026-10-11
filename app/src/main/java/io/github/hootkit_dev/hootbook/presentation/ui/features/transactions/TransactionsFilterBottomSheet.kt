@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.transactions.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet

@Composable
fun TransactionsFilterBottomSheet(state: CoreTransactionsUiState, onDismiss: () -> Unit, onApply: (CoreTransactionQuery) -> Unit) {
    var query by remember(state.query) { mutableStateOf(state.query) }
    var error by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("type") }
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { query = CoreTransactionQuery() }) { Text(stringResource(R.string.reset)) }
            }
            FilterSection(stringResource(R.string.core_full_label_42), section == "type", { section = if (section == "type") "" else "type" }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionKind.entries.filter { it != TransactionKind.UNKNOWN }.forEach { kind ->
                        val selected = kind in query.kinds || kind == query.kind
                        FilterChip(selected, { query = query.copy(kind = null, kinds = (query.kinds + listOfNotNull(query.kind)).distinct().toggle(kind)) }, label = { Text(kind.displayLabel()) })
                    }
                }
            }
            FilterSection(stringResource(R.string.accounts), section == "accounts", { section = if (section == "accounts") "" else "accounts" }) {
                state.accounts.forEach { account -> FilterCheck(account.name, account.id in query.accountIds || account.id == query.accountId) {
                    query = query.copy(accountId = null, accountIds = (query.accountIds + listOfNotNull(query.accountId)).distinct().toggle(account.id))
                } }
            }
            FilterSection(stringResource(R.string.category), section == "categories", { section = if (section == "categories") "" else "categories" }) {
                state.categories.forEach { category ->
                    FilterCheck(category.displayName, category.id in query.categoryIds || category.id == query.categoryId) {
                        query = query.copy(categoryId = null, categoryIds = (query.categoryIds + listOfNotNull(query.categoryId)).distinct().toggle(category.id))
                    }
                    category.subcategories.forEach { child -> Row(Modifier.padding(start = 24.dp)) {
                        FilterCheck(child.displayName, false, enabled = false) {}
                    } }
                }
                Text(stringResource(R.string.transaction_subcategory_filter_unavailable), style = MaterialTheme.typography.bodySmall)
            }
            FilterSection(stringResource(R.string.select_currency), section == "currencies", { section = if (section == "currencies") "" else "currencies" }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (state.accounts.flatMap { it.pockets.map { pocket -> pocket.currency } } + listOf(state.mainCurrency) + listOfNotNull(query.currency.ifBlank { null })).distinct().sorted().forEach { currency ->
                        FilterChip(currency == query.currency, {
                            query = query.copy(currency = if (query.currency == currency) "" else currency)
                        }, label = { Text(currency) })
                    }
                }
            }
            FilterSection(stringResource(R.string.amount_range), section == "amount", { section = if (section == "amount") "" else "amount" }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField("", {}, enabled = false, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.transaction_minimum)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField("", {}, enabled = false, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.transaction_maximum)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Text(stringResource(R.string.transaction_amount_filter_unavailable), style = MaterialTheme.typography.bodySmall)
            }
            FilterSection(stringResource(R.string.date_range), section == "date", { section = if (section == "date") "" else "date" }) {
                OutlinedTextField(query.fromDate, { query = query.copy(fromDate = it) }, label = { Text(stringResource(R.string.core_full_label_36)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(query.toDateExclusive, { query = query.copy(toDateExclusive = it) }, label = { Text(stringResource(R.string.core_full_label_37)) }, modifier = Modifier.fillMaxWidth())
            }
            FilterSection(stringResource(R.string.core_full_label_48), section == "deleted", { section = if (section == "deleted") "" else "deleted" }) {
                listOf(DeletedFilter.EXCLUDE to R.string.core_full_label_49, DeletedFilter.ONLY to R.string.core_full_label_50, DeletedFilter.INCLUDE to R.string.core_full_label_51).forEach { (value, label) ->
                    FilterCheck(stringResource(label), query.deleted == value) { query = query.copy(deleted = value) }
                }
            }
            if (error) Text(stringResource(R.string.transaction_filter_invalid), color = MaterialTheme.colorScheme.error)
            Button(onClick = { if (runCatching { query.toFilter() }.isSuccess) { onApply(query); onDismiss() } else error = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun <T> List<T>.toggle(value: T) = if (value in this) this - value else this + value
@Composable
private fun FilterCheck(label: String, selected: Boolean, enabled: Boolean = true, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click), verticalAlignment = Alignment.CenterVertically) { Checkbox(selected, { click() }, enabled = enabled); Text(label) }
}
@Composable
private fun FilterSection(title: String, expanded: Boolean, click: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            TextButton(onClick = click, modifier = Modifier.fillMaxWidth()) { Text(title, modifier = Modifier.weight(1f)); Text(if (expanded) "−" else "+") }
            if (expanded) content()
        }
    }
}
