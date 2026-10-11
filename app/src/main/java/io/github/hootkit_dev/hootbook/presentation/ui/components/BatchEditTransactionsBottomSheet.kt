@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.transactions.*

@Composable
fun BatchEditTransactionsBottomSheet(state: CoreTransactionsUiState, dismiss: () -> Unit, submit: (CoreTransactionBulkForm) -> Unit) {
    var form by remember { mutableStateOf(CoreTransactionBulkForm()) }
    HootbookModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.core_full_bulk_edit), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.core_full_bulk_note))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            for ((label, value, update) in listOf(
                Triple(R.string.core_full_bulk_date, form.date) { v: String -> form = form.copy(date = v) },
                Triple(R.string.core_full_bulk_time, form.time) { v: String -> form = form.copy(time = v) },
                Triple(R.string.core_full_bulk_amount, form.amount) { v: String -> form = form.copy(amount = v) })) {
                OutlinedTextField(value, onValueChange = update, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.core_full_bulk_category))
            for (category in state.categories.filterNot { it.hidden }) {
                Row { RadioButton(form.categoryId == category.id, onClick = { form = form.copy(categoryId = category.id, subcategoryId = null) }); Text(category.displayName) }
                if (form.categoryId == category.id) category.subcategories.filterNot { it.hidden }.forEach { child ->
                    TextButton(onClick = { form = form.copy(subcategoryId = child.id) }) { Text("${if (form.subcategoryId == child.id) "✓ " else ""}${child.displayName}") }
                }
            }
            TextButton(onClick = { form = form.copy(categoryId = null, subcategoryId = null) }) { Text(stringResource(R.string.core_full_keep_category)) }
            Row { Checkbox(form.replaceNotes, onCheckedChange = { form = form.copy(replaceNotes = it) }); Text(stringResource(R.string.core_full_replace_notes)) }
            if (form.replaceNotes) OutlinedTextField(form.notes, { form = form.copy(notes = it) }, label = { Text(stringResource(R.string.core_full_notes)) })
            Button(enabled = !state.writing, onClick = { submit(form) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_full_confirm)) }
            TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
