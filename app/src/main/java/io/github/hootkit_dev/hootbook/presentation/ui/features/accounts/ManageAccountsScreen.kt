package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.accounts.CoreAccountsUiState

@Composable
internal fun ManageAccountsList(state: CoreAccountsUiState, initialKind: AccountKind?, onSelect: (String) -> Unit, onAdd: (AccountKind?) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val accounts = state.overview?.accounts.orEmpty()
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (accounts.isEmpty() && !state.loading) item { Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Text(stringResource(R.string.no_accounts_yet), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.add_account_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        accountSectionOrder.filter { initialKind == null || it == initialKind }.forEach { kind ->
            val rows = accounts.filter { it.kind == kind }
            val open = initialKind != null || kind.name in expanded
            if (rows.isNotEmpty()) {
                item(key = "section_${kind.name}") { AccountSectionSummary(kind, rows.size, open, if (initialKind == null) ({ expanded = if (open) expanded - kind.name else expanded + kind.name }) else null) }
                items(if (open) rows else rows.take(2), key = { it.id }) { row -> CompactAccountCard(row, state.customSymbols, { onSelect(row.id) }, display = state.displayValues[row.id]) }
                if (!open && rows.size > 2) item(key = "more_${kind.name}") { TextButton(onClick = { expanded = expanded + kind.name }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_show_more, rows.size - 2)) } }
                item(key = "gap_${kind.name}") { Spacer(Modifier.height(10.dp)) }
            }
            if (kind == AccountKind.INVESTMENT && (rows.isEmpty() || open)) item(key = "investment_add") { InvestmentsEmptySection({ onAdd(AccountKind.INVESTMENT) }, showTitle = rows.isEmpty()) }
        }
    }
}
