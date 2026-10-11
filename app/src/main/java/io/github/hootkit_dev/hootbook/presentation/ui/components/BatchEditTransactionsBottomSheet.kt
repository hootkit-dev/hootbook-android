@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionBulkForm
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionsUiState

/** Changes the selected transactions together; every field left empty keeps each one's value. */
@Composable
fun BatchEditTransactionsBottomSheet(state: CoreTransactionsUiState, dismiss: () -> Unit, submit: (CoreTransactionBulkForm) -> Unit) {
    var form by remember { mutableStateOf(CoreTransactionBulkForm()) }
    val keep = stringResource(R.string.bulk_keep)
    val enabled = !state.writing
    val categories = state.categories.filterNot { it.hidden }
    val category = categories.find { it.id == form.categoryId }
    FormSheet(title = stringResource(R.string.core_full_bulk_edit), onDismiss = dismiss, busy = state.writing, error = state.error,
        confirmLabel = stringResource(R.string.save), onConfirm = { submit(form) }) {
        Text(stringResource(R.string.bulk_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DateField(form.date, stringResource(R.string.bulk_date), { form = form.copy(date = it) }, enabled = enabled, emptyText = keep)
        TimeField(form.time, stringResource(R.string.bulk_time), { form = form.copy(time = it) }, enabled = enabled, emptyText = keep)
        FormTextField(form.amount, stringResource(R.string.bulk_amount), { form = form.copy(amount = it) }, enabled = enabled, decimal = true,
            supporting = stringResource(R.string.bulk_empty_keeps))
        SelectField(stringResource(R.string.bulk_category), category?.displayName ?: keep,
            listOf<Pair<String?, String>>(null to keep) + categories.map { it.id to it.displayName },
            { form = form.copy(categoryId = it, subcategoryId = null) }, enabled = enabled)
        if (category != null && category.subcategories.any { !it.hidden }) {
            val none = stringResource(R.string.bulk_no_subcategory)
            SelectField(stringResource(R.string.bulk_subcategory), category.subcategories.find { it.id == form.subcategoryId }?.displayName ?: none,
                listOf<Pair<String?, String>>(null to none) + category.subcategories.filterNot { it.hidden }.map { it.id to it.displayName },
                { form = form.copy(subcategoryId = it) }, enabled = enabled)
        }
        Column {
            CheckboxRow(stringResource(R.string.bulk_replace_notes), form.replaceNotes, { form = form.copy(replaceNotes = it) }, enabled = enabled)
            if (form.replaceNotes) FormTextField(form.notes, stringResource(R.string.core_full_notes), { form = form.copy(notes = it) }, enabled = enabled,
                supporting = stringResource(R.string.bulk_notes_help))
        }
    }
}
