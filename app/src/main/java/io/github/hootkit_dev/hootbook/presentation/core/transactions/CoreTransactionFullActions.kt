package io.github.hootkit_dev.hootbook.presentation.core.transactions

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentPreview
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CoreTransactionFullActions(state: CoreTransactionsUiState, model: CoreTransactionsViewModel,
    onManageSubscriptions: (String) -> Unit, onManageMerchantIcon: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<String?>(null) }
    var bulkEditor by remember { mutableStateOf(false) }
    var deletion by remember { mutableStateOf<String?>(null) }
    var subscriptionEditor by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importAttachment) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val file = state.exportFile
        if (uri != null && file != null) scope.launch {
            exportError = false
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Output unavailable") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { exportError = true }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.writing) OutlinedButton(onClick = model::cancelWrite) { Text(stringResource(R.string.core_full_cancel_request)) }
        val store = model.attachmentStore
        if (store != null) {
            val refs = state.form?.attachments ?: state.detail?.attachments.orEmpty()
            for (ref in refs) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoreAttachmentImage(ref, store, Modifier.size(56.dp))
                TextButton(onClick = { preview = ref }) { Text(stringResource(R.string.core_full_attachment_view)) }
                if (state.form != null) TextButton(enabled = !state.writing, onClick = { model.removeAttachment(ref) }) { Text(stringResource(R.string.core_full_attachment_remove)) }
            }
            if (state.form != null) OutlinedButton(enabled = !state.writing && !state.importingAttachment, onClick = { pick.launch(arrayOf("image/*", "application/pdf", "text/plain")) }) { Text(stringResource(R.string.core_full_attachment_add)) }
            if (state.importingAttachment) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.form != null || state.detail != null) {
            OutlinedButton(enabled = !state.writing && !state.checkingDuplicates, onClick = model::checkDuplicates) { Text(stringResource(R.string.core_full_duplicates)) }
            if (state.checkingDuplicates) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.duplicateCandidates.isNotEmpty()) {
                Text(stringResource(R.string.core_full_duplicates_note))
                state.duplicateCandidates.forEach { row ->
                    Row {
                        Checkbox(row.id in state.selectedIds, onCheckedChange = { model.toggleSelected(row.id) }, enabled = !state.writing)
                        Text("${row.dateTime.inputText()} · ${row.amount.toPlainString()} ${row.currency} · ${row.merchant}")
                    }
                }
                TextButton(enabled = !state.writing, onClick = model::selectDuplicateCandidates) { Text(stringResource(R.string.core_full_select_candidates)) }
                TextButton(enabled = !state.writing && state.selectedIds.isNotEmpty(), onClick = { bulkEditor = true }) { Text(stringResource(R.string.core_full_bulk_edit)) }
            }
        }
        state.detail?.takeIf { state.form == null }?.let { row ->
            if (row.merchant.isNotBlank()) TextButton(enabled = !state.writing, onClick = { onManageMerchantIcon(row.merchant) }) { Text(stringResource(R.string.core_full_merchant_icon)) }
            if (!row.deleted && row.kind == TransactionKind.EXPENSE && row.subscriptionId == null) TextButton(enabled = !state.writing, onClick = { subscriptionEditor = true }) { Text(stringResource(R.string.core_full_make_subscription)) }
            if (row.deleted) TextButton(enabled = !state.writing, onClick = { model.clearSelection(); model.toggleSelected(row.id); deletion = "purge" }) { Text(stringResource(R.string.core_full_purge_selected)) }
        }
        state.createdSubscriptionId?.let { id -> TextButton(enabled = !state.writing, onClick = { onManageSubscriptions(id) }) { Text(stringResource(R.string.core_full_open_subscription)) } }
        if (state.form == null && state.detailId == null) {
            Text(stringResource(R.string.core_full_selected_count, state.selectedIds.size))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !state.writing, onClick = model::selectCurrentPage) { Text(stringResource(R.string.core_full_select_page)) }
                TextButton(enabled = !state.writing, onClick = model::selectAllMatching) { Text(stringResource(R.string.core_full_select_all)) }
                TextButton(enabled = !state.writing, onClick = model::clearSelection) { Text(stringResource(R.string.core_full_clear_selection)) }
                TextButton(enabled = !state.writing && state.selectedIds.isNotEmpty(), onClick = { bulkEditor = true }) { Text(stringResource(R.string.core_full_bulk_edit)) }
                TextButton(enabled = !state.writing && state.selectedIds.isNotEmpty(), onClick = { deletion = "delete" }) { Text(stringResource(R.string.core_full_delete_selected)) }
                if (state.query.deleted != DeletedFilter.EXCLUDE) {
                    TextButton(enabled = !state.writing && state.selectedIds.isNotEmpty(), onClick = { deletion = "purge" }) { Text(stringResource(R.string.core_full_purge_selected)) }
                    TextButton(enabled = !state.writing, onClick = { deletion = "purgeAll" }) { Text(stringResource(R.string.core_full_empty_recycle)) }
                }
                if (store != null) TextButton(enabled = !state.exporting, onClick = model::exportCsv) { Text(stringResource(R.string.core_full_export_all_pages)) }
            }
            if (state.exporting) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = model::cancelExport) { Text(stringResource(R.string.core_full_cancel_request)) } }
            state.exportFile?.let { file ->
                Text(stringResource(R.string.core_full_export_count, state.exportCount.toString()))
                Row {
                    TextButton(onClick = { saveCsv.launch("transactions.csv") }) { Text(stringResource(R.string.core_full_save_csv)) }
                    TextButton(onClick = {
                        exportError = false
                        runCatching {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            check(uri.path?.startsWith("/core_attachment_share/") == true)
                            val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            send.clipData = android.content.ClipData.newRawUri("transactions.csv", uri)
                            context.startActivity(Intent.createChooser(send, null))
                        }.onFailure { exportError = true }
                    }) { Text(stringResource(R.string.core_attachment_share)) }
                }
            }
            if (exportError) Text(stringResource(R.string.core_full_export_failed), color = MaterialTheme.colorScheme.error)
        }
    }
    preview?.let { ref -> model.attachmentStore?.let { CoreAttachmentPreview(ref, it, onDismiss = { preview = null }) } }
    LaunchedEffect(state.selectedIds) { if (state.selectedIds.isEmpty()) bulkEditor = false }
    if (bulkEditor) CoreBulkEditor(state, { bulkEditor = false }) { form -> model.bulkUpdate(form) }
    deletion?.let { action -> AlertDialog(onDismissRequest = { if (!state.writing) deletion = null },
        title = { Text(stringResource(if (action == "delete") R.string.core_full_delete_selected else R.string.core_full_permanent_confirm)) },
        text = { Text(stringResource(if (action == "delete") R.string.core_full_delete_note else if (action == "purgeAll") R.string.core_full_purge_all_note else R.string.core_full_purge_note)) },
        confirmButton = { TextButton(enabled = !state.writing, onClick = {
            if (action == "delete") model.deleteSelected() else model.purgeSelected(action == "purgeAll")
            deletion = null
        }) { Text(stringResource(R.string.core_full_confirm)) } },
        dismissButton = { TextButton(enabled = !state.writing, onClick = { deletion = null }) { Text(stringResource(R.string.cancel)) } }) }
    if (subscriptionEditor) CoreSubscriptionFromTransactionDialog(state.writing, { subscriptionEditor = false }) { cycle, end -> model.createSubscription(cycle, end); subscriptionEditor = false }
}

@Composable
private fun CoreBulkEditor(state: CoreTransactionsUiState, dismiss: () -> Unit, submit: (CoreTransactionBulkForm) -> Unit) {
    var form by remember { mutableStateOf(CoreTransactionBulkForm()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.core_full_bulk_edit)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        } }, confirmButton = { TextButton(enabled = !state.writing, onClick = { submit(form) }) { Text(stringResource(R.string.core_full_confirm)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun CoreSubscriptionFromTransactionDialog(writing: Boolean, dismiss: () -> Unit, submit: (Cycle, LocalDate?) -> Unit) {
    var every by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(CycleUnit.MONTH) }
    var end by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.core_full_make_subscription)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(every, { every = it }, label = { Text(stringResource(R.string.core_full_cycle_every)) })
            CycleUnit.entries.filter { it != CycleUnit.UNKNOWN }.forEach { value -> Row {
                RadioButton(unit == value, onClick = { unit = value }); Text(stringResource(when(value) { CycleUnit.DAY -> R.string.core_full_day; CycleUnit.WEEK -> R.string.core_full_week; CycleUnit.MONTH -> R.string.core_full_month; else -> R.string.core_full_year }))
            } }
            OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.core_full_end_date)) })
            if (invalid) Text(stringResource(R.string.core_full_invalid_input), color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(enabled = !writing, onClick = {
            runCatching {
                val count = requireNotNull(every.toUIntOrNull()).also { require(it > 0u) }
                val date = end.takeIf(String::isNotBlank)?.let(java.time.LocalDate::parse)?.let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
                submit(Cycle(count, unit), date)
            }.onFailure { invalid = true }
        }) { Text(stringResource(R.string.core_full_confirm)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
