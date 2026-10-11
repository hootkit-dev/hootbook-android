@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.debts

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentImage
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentPreview
import io.github.hootkit_dev.hootbook.presentation.core.plans.CorePlanDateField
import io.github.hootkit_dev.hootbook.presentation.core.plans.CorePlanDateTimeField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.accounts.CoreTextField

/** Standalone Core-only route. Never enters legacy people/debt screens. */
@Composable
fun CoreDebtsScreen(
    onBack: () -> Unit, initialPersonId: String? = null,
    onOpenTransactions: (String) -> Unit = {}, viewModel: CoreDebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var preview by remember{mutableStateOf<String?>(null)}
    var nameFilter by rememberSaveable{mutableStateOf("")}
    var sortName by rememberSaveable{mutableStateOf(false)}
    var debtKind by rememberSaveable{mutableStateOf<DebtKind?>(null)}
    val avatar=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),viewModel::avatar)
    val attachment=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),viewModel::attachment)
    val contact=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==android.app.Activity.RESULT_OK)result.data?.data?.let{uri->
            try{val selected=selectedPhoneContact(context.contentResolver,uri);viewModel.selectedContact(selected.first,selected.second)}catch(_:Exception){viewModel.platformFailure()}
        }
    }
    LaunchedEffect(initialPersonId) { viewModel.initialize(initialPersonId) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    var deletePerson by remember { mutableStateOf<Person?>(null) }
    var deleteDebt by remember { mutableStateOf<DebtEntry?>(null) }
    var unlinkDebt by remember { mutableStateOf<DebtEntry?>(null) }
    // Confirmations dismiss before underlying draft/detail and stay put during a write.
    val closeConfirmation: () -> Unit = {
        if (!state.saving) { deletePerson = null; deleteDebt = null; unlinkDebt = null }
    }
    BackHandler {
        if (deletePerson != null || deleteDebt != null || unlinkDebt != null) closeConfirmation() else goBack()
    }
    LaunchedEffect(state.commandRevision) { closeConfirmation() }
    val settlementTitle = stringResource(R.string.core_debts_settle_title)
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.person?.name ?: stringResource(R.string.core_debts_title)) },
            navigationIcon = { TextButton(onClick = goBack, enabled = !state.saving) { Text(stringResource(R.string.core_debts_back)) } },
            actions = { TextButton(onClick = viewModel::refresh, enabled = !state.saving) { Text(stringResource(R.string.core_debts_refresh)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.loading || state.saving || state.assetLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.platformError?.let{Text(stringResource(it),color=MaterialTheme.colorScheme.error)}
            if (state.navigationBlocked) Text(stringResource(R.string.core_debts_saving), modifier = Modifier.padding(16.dp))
            state.error?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_debts_dismiss)) }
                }
            } }
            if (state.selectedId == null) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { state.summary?.let { summary ->
                        DebtTotals(summary.receivable.toPlainString(), summary.payable.toPlainString(), summary.net.toPlainString(), summary.currency, summary.complete)
                        Text(stringResource(R.string.core_debts_summary_people, summary.people.toInt(), summary.peopleReceivable.toInt(), summary.peoplePayable.toInt(), summary.peopleOverdue.toInt()))
                    } }
                    item { FlowRow(verticalArrangement = Arrangement.Center) {
                        FilterChip(selected = state.includeArchived, onClick = { viewModel.includeArchived(!state.includeArchived) },
                            enabled = !state.saving, label = { Text(stringResource(R.string.core_debts_archived)) })
                        TextButton(onClick = { viewModel.editPerson() }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_add_person)) }
                    } }
                    item{OutlinedTextField(nameFilter,{nameFilter=it},label={Text(stringResource(R.string.core_detail_name_filter))},singleLine=true,enabled=!state.saving)
                        FilterChip(sortName,{sortName=!sortName},label={Text(stringResource(R.string.core_detail_sort_name))})}
                    if (!state.loading && state.people.isEmpty()) item { Text(stringResource(R.string.core_debts_empty)) }
                    items(state.people.filter{it.name.contains(nameFilter,ignoreCase=true)}.let{if(sortName)it.sortedBy{row->row.name.lowercase(java.util.Locale.ROOT)}else it}, key = { it.id }) { person ->
                        Card(onClick = { viewModel.select(person.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                person.avatar?.let{ref->viewModel.attachmentStore?.let{store->CoreAttachmentImage(ref,store,Modifier.size(56.dp))}}
                                Text(person.name, style = MaterialTheme.typography.titleLarge)
                                PersonTotalsView(person)
                            }
                        }
                    }
                }
            } else {
                val person = state.person
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        if (person == null) Text(stringResource(R.string.core_debts_orphan))
                        else {
                        Text(person.name, style = MaterialTheme.typography.headlineMedium)
                        PersonTotalsView(person)
                        person.phone?.let { number -> Text(number);TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number,null)))}.onFailure{viewModel.platformFailure()}}){Text(stringResource(R.string.core_detail_dial))} }
                        person.notes?.let { Text(it) }
                        person.avatar?.let{ref->viewModel.attachmentStore?.let{store->CoreAttachmentImage(ref,store,Modifier.size(88.dp));TextButton(onClick={preview=ref}){Text(stringResource(R.string.core_detail_open_attachment))}}}
                        FlowRow {
                            TextButton(onClick = { viewModel.editPerson(person) }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_edit)) }
                            TextButton(onClick = { viewModel.archive(person) }, enabled = !state.saving) { Text(stringResource(if (person.archived) R.string.core_debts_unarchive else R.string.core_debts_archive)) }
                            TextButton(onClick = { deletePerson = person }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_delete), color = MaterialTheme.colorScheme.error) }
                        }
                        FlowRow {
                            TextButton(onClick = { viewModel.editDebt() }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_add_record)) }
                            TextButton(onClick = { viewModel.settle(settlementTitle) }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_settle)) }
                        }
                        Text(stringResource(R.string.core_debts_settle_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!state.loading && state.debts.isEmpty()) item { Text(stringResource(R.string.core_debts_history_empty)) }
                    item{FlowRow{FilterChip(debtKind==null,{debtKind=null},label={Text(stringResource(R.string.core_detail_all))});DebtKind.entries.filter{it!=DebtKind.UNKNOWN}.forEach{kind->FilterChip(debtKind==kind,{debtKind=kind},label={Text(debtKindLabel(kind))})}}}
                    items(state.debts.filter{debtKind==null||it.kind==debtKind}.sortedByDescending{it.date.debtText()}, key = { it.id }) { debt ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(debt.title, style = MaterialTheme.typography.titleMedium)
                                Text("${debtKindLabel(debt.kind)} · ${debt.amount.amount.toPlainString()} ${debt.amount.currency}")
                                Text(debt.date.debtText())
                                debt.dueDate?.let { Text(stringResource(R.string.core_debts_due_display, it.debtText())) }
                                debt.accountId?.let { id -> Text(state.accounts.find { it.id == id }?.name ?: id) }
                                debt.attachments.forEach{ref->TextButton(onClick={preview=ref}){Text(stringResource(R.string.core_detail_open_attachment)+" · "+ref)}}
                                FlowRow {
                                    TextButton(onClick = { viewModel.editDebt(debt) }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_edit)) }
                                    TextButton(onClick = { deleteDebt = debt }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_delete), color = MaterialTheme.colorScheme.error) }
                                    if (debt.transactionId != null) {
                                        TextButton(onClick = { unlinkDebt = debt }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_unlink)) }
                                        TextButton(onClick = { viewModel.transactionTarget(debt)?.let(onOpenTransactions) }, enabled = !state.saving) { Text(stringResource(R.string.core_debts_open_transaction)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.personForm?.let { form ->
        DebtEditor(stringResource(R.string.core_debts_add_person), state, viewModel::dismissEditor, viewModel::savePerson) {
            CoreTextField(form.name, stringResource(R.string.core_debts_name), state.saving) { viewModel.personChanged(form.copy(name = it)) }
            CoreTextField(form.phone, stringResource(R.string.core_debts_phone), state.saving) { viewModel.personChanged(form.copy(phone = it)) }
            CoreTextField(form.notes, stringResource(R.string.core_debts_notes), state.saving) { viewModel.personChanged(form.copy(notes = it)) }
            CoreTextField(form.color, stringResource(R.string.core_debts_color), state.saving) { viewModel.personChanged(form.copy(color = it)) }
            DebtChoice(stringResource(R.string.core_debts_relation), relationLabel(form.relation), !state.saving,
                listOf(null, PersonRelation.FRIEND, PersonRelation.FAMILY, PersonRelation.COLLEAGUE, PersonRelation.OTHER, PersonRelation.UNKNOWN).map { it to relationLabel(it) }) { viewModel.personChanged(form.copy(relation = it)) }
            form.avatar?.let{ref->viewModel.attachmentStore?.let{store->CoreAttachmentImage(ref,store,Modifier.size(88.dp))}}
            TextButton(onClick={avatar.launch(arrayOf("image/*"))},enabled=!state.saving&&!state.assetLoading){Text(stringResource(R.string.core_detail_avatar))}
            TextButton(onClick=viewModel::removeAvatar,enabled=!state.saving&&!state.assetLoading){Text(stringResource(R.string.core_detail_clear))}
            TextButton(onClick={contact.launch(Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI))},enabled=!state.saving&&!state.assetLoading){Text(stringResource(R.string.core_detail_contact))}
        }
    }
    state.debtForm?.let { form ->
        DebtEditor(stringResource(R.string.core_debts_add_record), state, viewModel::dismissEditor, viewModel::saveDebt) {
            DebtChoice(stringResource(R.string.core_debts_person), state.people.find { it.id == form.personId }?.name ?: state.person?.takeIf { it.id == form.personId }?.name ?: form.personId,
                !state.saving, (state.people + listOfNotNull(state.person)).distinctBy { it.id }.map { it.id to it.name }) { viewModel.debtChanged(form.copy(personId = it)) }
            DebtChoice(stringResource(R.string.core_debts_record_title), debtKindLabel(form.kind), !state.saving,
                listOf(DebtKind.LENT, DebtKind.BORROWED, DebtKind.REPAID_TO_ME, DebtKind.REPAID_BY_ME).map { it to debtKindLabel(it) }) {
                viewModel.debtChanged(form.copy(kind = it, dueDate = if (it == DebtKind.LENT || it == DebtKind.BORROWED) form.dueDate else ""))
            }
            CoreTextField(form.title, stringResource(R.string.core_debts_record_title), state.saving) { viewModel.debtChanged(form.copy(title = it)) }
            CoreTextField(form.amount, stringResource(R.string.core_debts_amount), state.saving) { viewModel.debtChanged(form.copy(amount = it)) }
            AccountChooser(state.accounts, form.accountId, !state.saving) { viewModel.debtChanged(form.copy(accountId = it)) }
            CurrencyField(state.accounts, form.accountId, form.currency, state.saving) { viewModel.debtChanged(form.copy(currency = it)) }
            CorePlanDateTimeField(form.date, stringResource(R.string.core_debts_date), state.saving) { viewModel.debtChanged(form.copy(date = it)) }
            if (form.kind == DebtKind.LENT || form.kind == DebtKind.BORROWED) CorePlanDateField(form.dueDate, stringResource(R.string.core_debts_due), state.saving) { viewModel.debtChanged(form.copy(dueDate = it)) }
            Text(stringResource(R.string.core_debts_account_hint), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick={attachment.launch(arrayOf("*/*"))},enabled=!state.saving&&!state.assetLoading){Text(stringResource(R.string.core_detail_attachments))}
            form.attachments.forEach{ref->Row{TextButton(onClick={preview=ref}){Text(stringResource(R.string.core_detail_open_attachment))};TextButton(onClick={viewModel.removeAttachment(ref)},enabled=!state.saving&&!state.assetLoading){Text(stringResource(R.string.core_detail_remove_attachment))}}}
        }
    }
    state.settlement?.let { form ->
        DebtEditor(stringResource(R.string.core_debts_settle), state, viewModel::dismissEditor, viewModel::saveSettlement) {
            Row { Checkbox(form.fullAmount, onCheckedChange = { viewModel.settlementChanged(form.copy(fullAmount = it)) }, enabled = !state.saving)
                Text(stringResource(R.string.core_debts_full_settle), Modifier.weight(1f)) }
            AccountChooser(state.accounts, form.accountId, !state.saving) { viewModel.settlementChanged(form.copy(accountId = it)) }
            if (!form.fullAmount) {
                CoreTextField(form.amount, stringResource(R.string.core_debts_amount), state.saving) { viewModel.settlementChanged(form.copy(amount = it)) }
                CurrencyField(state.accounts, form.accountId, form.currency, state.saving) { viewModel.settlementChanged(form.copy(currency = it)) }
            }
            CoreTextField(form.title, stringResource(R.string.core_debts_record_title), state.saving) { viewModel.settlementChanged(form.copy(title = it)) }
            CorePlanDateTimeField(form.date, stringResource(R.string.core_debts_date), state.saving) { viewModel.settlementChanged(form.copy(date = it)) }
            Text(stringResource(R.string.core_debts_settle_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.core_debts_account_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    preview?.let{ref->viewModel.attachmentStore?.let{store->CoreAttachmentPreview(ref,store){preview=null}}}
    deletePerson?.let { person -> DebtConfirmation(stringResource(R.string.core_debts_delete_person_title, person.name), stringResource(R.string.core_debts_delete_person_warning), state, closeConfirmation) { viewModel.deletePerson(person) } }
    deleteDebt?.let { debt -> DebtConfirmation(stringResource(R.string.core_debts_delete_record_title), stringResource(R.string.core_debts_delete_record_warning), state, closeConfirmation) { viewModel.deleteDebt(debt) } }
    unlinkDebt?.let { debt -> DebtConfirmation(stringResource(R.string.core_debts_unlink_title), stringResource(R.string.core_debts_unlink_warning), state, closeConfirmation) { viewModel.unlinkDebt(debt) } }
}

@Composable private fun PersonTotalsView(person: Person) {
    val totals = person.totals
    DebtTotals(totals.receivable.toPlainString(), totals.payable.toPlainString(), totals.net.toPlainString(), totals.currency, totals.complete)
    if (!totals.complete) Text(stringResource(R.string.core_debts_missing, totals.missingCurrencies.joinToString()))
    if (totals.overdue) Text(stringResource(R.string.core_debts_overdue), color = MaterialTheme.colorScheme.error)
    if (person.archived) Text(stringResource(R.string.core_debts_archived_status))
}
@Composable private fun DebtTotals(receivable: String, payable: String, net: String, currency: String, complete: Boolean) {
    Text(stringResource(R.string.core_debts_summary, receivable, payable, net, currency))
    if (!complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error)
}
@Composable private fun debtKindLabel(kind: DebtKind): String = stringResource(when (kind) {
    DebtKind.LENT -> R.string.core_debts_lent; DebtKind.BORROWED -> R.string.core_debts_borrowed
    DebtKind.REPAID_TO_ME -> R.string.core_debts_repaid_to_me; DebtKind.REPAID_BY_ME -> R.string.core_debts_repaid_by_me
    DebtKind.UNKNOWN -> R.string.core_debts_unknown
})
@Composable private fun relationLabel(relation: PersonRelation?): String = stringResource(when (relation) {
    null -> R.string.core_debts_relation_none; PersonRelation.FRIEND -> R.string.core_debts_friend
    PersonRelation.FAMILY -> R.string.core_debts_family; PersonRelation.COLLEAGUE -> R.string.core_debts_colleague
    PersonRelation.OTHER -> R.string.core_debts_other; PersonRelation.UNKNOWN -> R.string.core_debts_unknown
})
@Composable private fun <T> DebtChoice(label: String, selected: String, enabled: Boolean, options: List<Pair<T, String>>, select: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; select(value) }, enabled = enabled) }
        }
    }
}
@Composable private fun AccountChooser(accounts: List<Account>, id: String?, enabled: Boolean, choose: (String?) -> Unit) {
    val none = stringResource(R.string.core_debts_no_account)
    // Archived/missing current links remain selectable/preserved, never cleared merely by querying.
    val options = listOf(null to none) + accounts.filter { !it.archived || it.id == id }.map { it.id to it.name }
    DebtChoice(stringResource(R.string.core_debts_account), accounts.find { it.id == id }?.name ?: id ?: none, enabled, options, choose)
    if (id != null && accounts.none { it.id == id }) Text(stringResource(R.string.core_debts_account_missing, id))
}
@Composable private fun CurrencyField(accounts: List<Account>, accountId: String?, currency: String, saving: Boolean, change: (String) -> Unit) {
    CoreTextField(currency, stringResource(R.string.core_debts_currency), saving, change)
    val account = accounts.find { it.id == accountId }
    if (account != null) FlowRow {
        account.pockets.forEach { pocket -> SuggestionChip(onClick = { change(pocket.currency) }, label = { Text(pocket.currency) }, enabled = !saving) }
    }
}
@Composable private fun DebtEditor(title: String, state: CoreDebtsUiState, dismiss: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.saving) Text(stringResource(R.string.core_debts_saving))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            content()
        }
    }, dismissButton = { TextButton(onClick = dismiss, enabled = !state.saving) { Text(stringResource(R.string.core_debts_cancel)) } },
        confirmButton = { TextButton(onClick = save, enabled = !state.saving&&!state.assetLoading) { Text(stringResource(R.string.core_debts_save)) } })
}
@Composable private fun DebtConfirmation(title: String, warning: String, state: CoreDebtsUiState, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column {
        Text(warning)
        if (state.saving) Text(stringResource(R.string.core_debts_saving))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, dismissButton = { TextButton(onClick = dismiss, enabled = !state.saving) { Text(stringResource(R.string.core_debts_cancel)) } },
        confirmButton = { TextButton(onClick = confirm, enabled = !state.saving) { Text(stringResource(R.string.core_debts_delete), color = MaterialTheme.colorScheme.error) } })
}
