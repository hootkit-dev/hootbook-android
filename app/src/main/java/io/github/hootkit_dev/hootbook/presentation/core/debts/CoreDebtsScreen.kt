@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.debts

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentImage
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentPreview
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreColorPickerRow
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.plans.CorePlanDateField
import io.github.hootkit_dev.hootbook.presentation.core.plans.CorePlanDateTimeField
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem as HootbookListItem
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowTransactionActionDialog

/**
 * People and what is lent or borrowed: totals and a filterable list of people, one person's page
 * (contact, totals, records), and the person, record and settlement forms. A long press selects
 * people or records for deletion.
 */
@Composable
fun CoreDebtsScreen(
    onBack: () -> Unit, initialPersonId: String? = null,
    onOpenTransactions: (String) -> Unit = {}, viewModel: CoreDebtsViewModel = hiltViewModel(),
    initialCreatePerson: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var preview by remember { mutableStateOf<String?>(null) }
    var selectedPeople by remember { mutableStateOf(setOf<String>()) }
    var selectedDebts by remember { mutableStateOf(setOf<String>()) }
    var confirmBulk by remember { mutableStateOf(false) }
    var recordActions by remember { mutableStateOf<DebtEntry?>(null) }
    val selectionMode = selectedPeople.isNotEmpty() || selectedDebts.isNotEmpty()
    LaunchedEffect(state.people, state.debts, state.loading) {
        if (!state.loading) { selectedPeople = selectedPeople.intersect(state.people.map { it.id }.toSet()); selectedDebts = selectedDebts.intersect(state.debts.map { it.id }.toSet()) }
    }
    val avatar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::avatar)
    val attachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::attachment)
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data?.let { uri ->
            try { val selected = selectedPhoneContact(context.contentResolver, uri); viewModel.selectedContact(selected.first, selected.second) } catch (_: Exception) { viewModel.platformFailure() }
        }
    }
    LaunchedEffect(initialPersonId) { viewModel.initialize(initialPersonId) }
    var routedCreate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialCreatePerson, state.loading) {
        if (initialCreatePerson && !state.loading && !routedCreate) { routedCreate = true; if (state.personForm == null) viewModel.editPerson() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val goBack: () -> Unit = { if (!viewModel.back()) onBack() }
    var deletePerson by remember { mutableStateOf<Person?>(null) }
    var deleteDebt by remember { mutableStateOf<DebtEntry?>(null) }
    var unlinkDebt by remember { mutableStateOf<DebtEntry?>(null) }
    // Confirmations dismiss before underlying draft/detail and stay put during a write.
    val closeConfirmation: () -> Unit = { if (!state.saving) { deletePerson = null; deleteDebt = null; unlinkDebt = null } }
    val clearSelection = { selectedPeople = emptySet(); selectedDebts = emptySet() }
    BackHandler {
        when {
            recordActions != null -> recordActions = null
            confirmBulk -> if (!state.saving) confirmBulk = false
            deletePerson != null || deleteDebt != null || unlinkDebt != null -> closeConfirmation()
            selectionMode -> clearSelection()
            else -> goBack()
        }
    }
    LaunchedEffect(state.commandRevision) { closeConfirmation(); confirmBulk = false }
    val settlementTitle = stringResource(R.string.core_debts_settle_title)
    val person = state.person
    val enabled = !state.saving
    SecondaryScreen(
        title = when {
            selectionMode -> stringResource(R.string.txn_selected, selectedPeople.size + selectedDebts.size)
            state.selectedId != null -> person?.name ?: stringResource(R.string.core_debts_title)
            else -> stringResource(R.string.core_debts_title)
        },
        onBack = { if (selectionMode) { if (enabled) clearSelection() } else goBack() },
        backIcon = if (selectionMode) Icons.Rounded.Close else null,
        backLabel = if (selectionMode) stringResource(R.string.close) else null,
        actions = {
            when {
                selectionMode -> {
                    TooltipIconButton(Icons.Rounded.SelectAll, stringResource(R.string.select_all), {
                        if (state.selectedId == null) selectedPeople = state.people.map { it.id }.toSet() else selectedDebts = state.debts.map { it.id }.toSet()
                    }, enabled = enabled)
                    TooltipIconButton(Icons.Rounded.Delete, stringResource(R.string.delete_selected), { confirmBulk = true }, enabled = enabled)
                }
                state.selectedId != null && person != null -> {
                    TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.core_debts_edit), { viewModel.editPerson(person) }, enabled = enabled)
                    OverflowMenu(enabled = enabled, actions = listOf(
                        MenuAction(stringResource(if (person.archived) R.string.core_debts_unarchive else R.string.core_debts_archive),
                            if (person.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive) { viewModel.archive(person) },
                        MenuAction(stringResource(R.string.core_debts_delete), Icons.Rounded.Delete, destructive = true) { deletePerson = person },
                    ))
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode && state.personForm == null && state.debtForm == null && state.settlement == null) ExtendedFloatingActionButton(
                onClick = { if (state.selectedId == null) viewModel.editPerson() else viewModel.editDebt() },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(if (state.selectedId == null) R.string.core_debts_add_person else R.string.core_debts_add_record)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.navigationBlocked) Text(stringResource(R.string.core_debts_saving), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            (state.error ?: state.platformError?.let { stringResource(it) })?.let {
                ErrorBanner(it, viewModel::clearError, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (state.selectedId == null) PeopleList(state, viewModel, selectedPeople, selectionMode) { id ->
                selectedPeople = if (id in selectedPeople) selectedPeople - id else selectedPeople + id
            } else PersonDetail(state, viewModel, selectedDebts, selectionMode, settlementTitle, onPreview = { preview = it },
                onToggle = { id -> selectedDebts = if (id in selectedDebts) selectedDebts - id else selectedDebts + id },
                onOpen = { debt -> viewModel.transactionTarget(debt)?.let(onOpenTransactions) ?: run { recordActions = debt } },
                onActions = { recordActions = it })
        }
    }
    recordActions?.let { debt -> LendBorrowTransactionActionDialog(
        onEdit = { recordActions = null; viewModel.editDebt(debt) },
        onDelete = { recordActions = null; deleteDebt = debt },
        onSelectMultiple = { recordActions = null; selectedDebts = selectedDebts + debt.id },
        onDismiss = { recordActions = null },
        details = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(debt.title, style = MaterialTheme.typography.titleSmall)
                Text(debt.date.debtDisplay(), style = MaterialTheme.typography.bodySmall)
                debt.accountId?.let { id -> Text(state.accounts.find { it.id == id }?.name ?: id, style = MaterialTheme.typography.bodySmall) }
                debt.attachments.forEachIndexed { index, ref -> TextButton(onClick = { preview = ref }) { Icon(Icons.Rounded.AttachFile, null); Text(stringResource(R.string.core_detail_open_attachment) + " ${index + 1}") } }
                if (debt.transactionId != null) {
                    TextButton(onClick = { recordActions = null; unlinkDebt = debt }) { Text(stringResource(R.string.core_debts_unlink)) }
                    TextButton(onClick = { recordActions = null; viewModel.transactionTarget(debt)?.let(onOpenTransactions) }) { Text(stringResource(R.string.core_debts_open_transaction)) }
                }
            }
        }) }
    state.personForm?.let { form ->
        FormSheet(stringResource(if (form.id == null) R.string.core_debts_add_person else R.string.core_debts_edit_person), viewModel::dismissEditor, viewModel::savePerson, busy = state.saving || state.assetLoading,
            confirmEnabled = form.name.isNotBlank(), error = state.error) {
            FormTextField(form.name, stringResource(R.string.core_debts_name), { viewModel.personChanged(form.copy(name = it)) }, enabled = enabled)
            FormTextField(form.phone, stringResource(R.string.core_debts_phone), { viewModel.personChanged(form.copy(phone = it)) }, enabled = enabled)
            SelectField(stringResource(R.string.core_debts_relation), relationLabel(form.relation),
                listOf(null, PersonRelation.FRIEND, PersonRelation.FAMILY, PersonRelation.COLLEAGUE, PersonRelation.OTHER).map { it to relationLabel(it) },
                { viewModel.personChanged(form.copy(relation = it)) }, enabled = enabled)
            FormTextField(form.notes, stringResource(R.string.core_debts_notes), { viewModel.personChanged(form.copy(notes = it)) }, enabled = enabled)
            CoreColorPickerRow(form.color, enabled) { viewModel.personChanged(form.copy(color = it)) }
            ListSection {
                item(headline = { Text(stringResource(R.string.core_detail_avatar)) },
                    leading = { form.avatar?.let { ref -> viewModel.attachmentStore?.let { store -> CoreAttachmentImage(ref, store, Modifier.size(40.dp).clip(CircleShape)) } }
                        ?: Icon(Icons.Rounded.AccountCircle, null) },
                    trailing = if (form.avatar != null) ({ TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.core_debts_remove_avatar), viewModel::removeAvatar,
                        enabled = enabled && !state.assetLoading) }) else null,
                    onClick = { if (enabled && !state.assetLoading) avatar.launch(arrayOf("image/*")) })
                item(headline = { Text(stringResource(R.string.core_detail_contact)) }, leading = { Icon(Icons.Rounded.Contacts, null) }, chevron = true,
                    onClick = { if (enabled && !state.assetLoading) contact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) })
            }
        }
    }
    state.debtForm?.let { form ->
        FormSheet(stringResource(if (form.id == null) R.string.core_debts_add_record else R.string.core_debts_edit_record), viewModel::dismissEditor, viewModel::saveDebt, busy = state.saving || state.assetLoading,
            confirmEnabled = form.amount.isNotBlank(), error = state.error) {
            SelectField(stringResource(R.string.core_debts_person), state.people.find { it.id == form.personId }?.name ?: state.person?.takeIf { it.id == form.personId }?.name ?: form.personId,
                (state.people + listOfNotNull(state.person)).distinctBy { it.id }.map { it.id to it.name }, { viewModel.debtChanged(form.copy(personId = it)) }, enabled = enabled)
            SelectField(stringResource(R.string.core_debts_kind), debtKindLabel(form.kind),
                listOf(DebtKind.LENT, DebtKind.BORROWED, DebtKind.REPAID_TO_ME, DebtKind.REPAID_BY_ME).map { it to debtKindLabel(it) }, {
                    viewModel.debtChanged(form.copy(kind = it, dueDate = if (it == DebtKind.LENT || it == DebtKind.BORROWED) form.dueDate else ""))
                }, enabled = enabled)
            FormTextField(form.title, stringResource(R.string.core_debts_record_title), { viewModel.debtChanged(form.copy(title = it)) }, enabled = enabled)
            AmountAndCurrency(form.amount, form.currency, state.accounts, form.accountId, enabled, { viewModel.debtChanged(form.copy(amount = it)) }) { viewModel.debtChanged(form.copy(currency = it)) }
            AccountSelect(state.accounts, form.accountId, enabled) { viewModel.debtChanged(form.copy(accountId = it)) }
            CorePlanDateTimeField(form.date, stringResource(R.string.core_debts_date), state.saving) { viewModel.debtChanged(form.copy(date = it)) }
            if (form.kind == DebtKind.LENT || form.kind == DebtKind.BORROWED) CorePlanDateField(form.dueDate, stringResource(R.string.core_debts_due), state.saving) { viewModel.debtChanged(form.copy(dueDate = it)) }
            Text(stringResource(R.string.core_debts_account_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListSection(title = stringResource(R.string.txn_section_attachments)) {
                form.attachments.forEachIndexed { index, ref ->
                    item(headline = { Text(stringResource(R.string.txn_attachment, index + 1)) }, onClick = { preview = ref },
                        trailing = { TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.core_detail_remove_attachment), { viewModel.removeAttachment(ref) }, enabled = enabled && !state.assetLoading) })
                }
                item(headline = { Text(stringResource(R.string.core_detail_attachments)) }, leading = { Icon(Icons.Rounded.AttachFile, null) },
                    onClick = { if (enabled && !state.assetLoading) attachment.launch(arrayOf("*/*")) })
            }
        }
    }
    state.settlement?.let { form ->
        FormSheet(stringResource(R.string.core_debts_settle), viewModel::dismissEditor, viewModel::saveSettlement, busy = state.saving, error = state.error) {
            Text(stringResource(R.string.core_debts_settle_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListSection {
                item(headline = { Text(stringResource(R.string.core_debts_full_settle)) }, trailing = { HootbookSwitch(form.fullAmount, null, enabled = enabled) },
                    toggled = form.fullAmount, onToggle = { viewModel.settlementChanged(form.copy(fullAmount = it)) })
            }
            if (!form.fullAmount) AmountAndCurrency(form.amount, form.currency, state.accounts, form.accountId, enabled,
                { viewModel.settlementChanged(form.copy(amount = it)) }) { viewModel.settlementChanged(form.copy(currency = it)) }
            AccountSelect(state.accounts, form.accountId, enabled) { viewModel.settlementChanged(form.copy(accountId = it)) }
            FormTextField(form.title, stringResource(R.string.core_debts_record_title), { viewModel.settlementChanged(form.copy(title = it)) }, enabled = enabled)
            CorePlanDateTimeField(form.date, stringResource(R.string.core_debts_date), state.saving) { viewModel.settlementChanged(form.copy(date = it)) }
            Text(stringResource(R.string.core_debts_account_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmBulk) ConfirmDialog(stringResource(R.string.delete_selected), stringResource(R.string.core_parity_debt_bulk), stringResource(R.string.delete),
        { if (state.selectedId == null) viewModel.deletePeople(selectedPeople) else viewModel.deleteDebts(selectedDebts) }, { if (enabled) confirmBulk = false },
        destructive = true, enabled = enabled)
    preview?.let { ref -> viewModel.attachmentStore?.let { store -> CoreAttachmentPreview(ref, store) { preview = null } } }
    deletePerson?.let { p -> ConfirmDialog(stringResource(R.string.core_debts_delete_person_title, p.name), stringResource(R.string.core_debts_delete_person_warning),
        stringResource(R.string.delete), { viewModel.deletePerson(p) }, closeConfirmation, destructive = true, enabled = enabled) }
    deleteDebt?.let { debt -> ConfirmDialog(stringResource(R.string.core_debts_delete_record_title), stringResource(R.string.core_debts_delete_record_warning),
        stringResource(R.string.delete), { viewModel.deleteDebt(debt) }, closeConfirmation, destructive = true, enabled = enabled) }
    unlinkDebt?.let { debt -> ConfirmDialog(stringResource(R.string.core_debts_unlink_title), stringResource(R.string.core_debts_unlink_warning),
        stringResource(R.string.core_debts_unlink), { viewModel.unlinkDebt(debt) }, closeConfirmation, destructive = true, enabled = enabled) }
}

/** Totals, then search and filters (balance, relation, archived, name order), then the people. */
@Composable
private fun PeopleList(state: CoreDebtsUiState, viewModel: CoreDebtsViewModel, selected: Set<String>, selectionMode: Boolean, toggle: (String) -> Unit) {
    var nameFilter by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(false) }
    var peopleFilter by rememberSaveable { mutableIntStateOf(0) }
    var relationFilter by remember { mutableStateOf<PersonRelation?>(null) }
    val visible = state.people.filter { person ->
        person.name.contains(nameFilter, ignoreCase = true) && (relationFilter == null || person.relation == relationFilter) &&
            when (peopleFilter) { 1 -> person.totals.net.signum() > 0; 2 -> person.totals.net.signum() < 0; 3 -> person.totals.overdue; else -> true }
    }.let { if (sortName) it.sortedBy { row -> row.name.lowercase(java.util.Locale.ROOT) } else it }
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.summary?.let { summary -> item(key = "totals") {
            DebtTotals(summary.receivable.toPlainString(), summary.payable.toPlainString(), summary.net.toPlainString(), summary.currency, summary.complete)
            Text(stringResource(R.string.core_debts_summary_people, summary.people.toInt(), summary.peopleReceivable.toInt(), summary.peoplePayable.toInt(), summary.peopleOverdue.toInt()),
                Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item(key = "balance") {
            GenericTypeSwitcher(peopleFilter, { peopleFilter = it }, listOf(stringResource(R.string.type_all), stringResource(R.string.lend_borrow_filter_lent),
                stringResource(R.string.lend_borrow_filter_owed), stringResource(R.string.overdue)), Modifier.fillMaxWidth())
        }
        item(key = "search") {
            SearchBarBox(searchQuery = TextFieldValue(nameFilter), onSearchQueryChange = { nameFilter = it.text }, label = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (nameFilter.isNotEmpty()) TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.clear_search), { nameFilter = "" }) })
        }
        item(key = "chips") {
            var relationMenu by remember { mutableStateOf(false) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Box {
                    FilterChip(relationFilter != null, { relationMenu = true }, label = { Text(relationFilter?.let { relationLabel(it) } ?: stringResource(R.string.core_debts_relation)) },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize)) })
                    DropdownMenu(relationMenu, { relationMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.category_all)) }, onClick = { relationFilter = null; relationMenu = false })
                        PersonRelation.entries.forEach { relation -> DropdownMenuItem(text = { Text(relationLabel(relation)) }, onClick = { relationFilter = relation; relationMenu = false }) }
                    }
                } }
                item { FilterChip(state.includeArchived, { viewModel.includeArchived(!state.includeArchived) }, enabled = !state.saving,
                    label = { Text(stringResource(R.string.core_debts_archived)) }) }
                item { FilterChip(sortName, { sortName = !sortName }, label = { Text(stringResource(R.string.core_detail_sort_name)) },
                    leadingIcon = { Icon(Icons.Rounded.SortByAlpha, null, Modifier.size(FilterChipDefaults.IconSize)) }) }
            }
        }
        if (!state.loading && state.people.isEmpty()) item(key = "empty") {
            Text(stringResource(R.string.core_debts_empty), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (visible.isNotEmpty()) item(key = "people") {
            ListSection {
                visible.forEach { person -> row { shape ->
                    CorePersonRow(person, avatar = {
                        person.avatar?.let { ref -> viewModel.attachmentStore?.let { store -> CoreAttachmentImage(ref, store, Modifier.size(40.dp).clip(CircleShape)) } }
                    }, selected = if (selectionMode) person.id in selected else null, shape = shape,
                        onLongClick = { if (!state.saving) toggle(person.id) },
                        onClick = { if (!state.saving) { if (selectionMode) toggle(person.id) else viewModel.select(person.id) } })
                } }
            }
        }
    }
}

/** The person's avatar, relation and contact actions, their totals and settling, then their records. */
@Composable
private fun PersonDetail(state: CoreDebtsUiState, viewModel: CoreDebtsViewModel, selected: Set<String>, selectionMode: Boolean, settlementTitle: String,
    onPreview: (String) -> Unit, onToggle: (String) -> Unit, onOpen: (DebtEntry) -> Unit, onActions: (DebtEntry) -> Unit) {
    val context = LocalContext.current
    val person = state.person
    var debtKind by rememberSaveable { mutableStateOf<DebtKind?>(null) }
    val records = state.debts.filter { debtKind == null || it.kind == debtKind }.sortedByDescending { it.date.debtText() }
    LazyColumn(contentPadding = ScreenContentPadding, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        item(key = "person") {
            if (person == null) Text(stringResource(R.string.core_debts_orphan), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(debtColor(person.color)), contentAlignment = Alignment.Center) {
                    if (person.avatar == null) Text(person.name.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    person.avatar?.let { ref -> viewModel.attachmentStore?.let { store -> CoreAttachmentImage(ref, store, Modifier.fillMaxSize().clickable { onPreview(ref) }) } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusLabel(relationLabel(person.relation))
                    if (person.totals.overdue) StatusLabel(stringResource(R.string.core_debts_overdue), error = true)
                    if (person.archived) StatusLabel(stringResource(R.string.core_debts_archived_status))
                }
                person.phone?.let { number ->
                    Text(number, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }.onFailure { viewModel.platformFailure() } },
                            label = { Text(stringResource(R.string.core_detail_dial)) }, leadingIcon = { Icon(Icons.Rounded.Call, null, Modifier.size(AssistChipDefaults.IconSize)) })
                        AssistChip(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number.filter(Char::isDigit)))) }.onFailure { viewModel.platformFailure() } },
                            label = { Text("WhatsApp") })
                        AssistChip(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+" + number.filter(Char::isDigit)))) }.onFailure { viewModel.platformFailure() } },
                            label = { Text("Telegram") })
                    }
                }
            }
        }
        if (person != null) item(key = "totals") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DebtTotals(person.totals.receivable.toPlainString(), person.totals.payable.toPlainString(), person.totals.net.toPlainString(), person.totals.currency, person.totals.complete)
                if (!person.totals.complete) Text(stringResource(R.string.core_debts_missing, person.totals.missingCurrencies.joinToString()),
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { viewModel.settle(settlementTitle) }, enabled = !state.saving, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Handshake, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.core_debts_settle)) }
                person.notes?.takeIf { it.isNotBlank() }?.let { ListSection { item(headline = { Text(stringResource(R.string.core_debts_notes)) },
                    supporting = { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }) } }
            }
        }
        item(key = "kinds") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(debtKind == null, { debtKind = null }, label = { Text(stringResource(R.string.core_detail_all)) }) }
                items(DebtKind.entries.filter { it != DebtKind.UNKNOWN }) { kind -> FilterChip(debtKind == kind, { debtKind = kind }, label = { Text(debtKindLabel(kind)) }) }
            }
        }
        item(key = "records") {
            if (!state.loading && records.isEmpty()) Text(stringResource(R.string.core_debts_history_empty), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                records.forEachIndexed { index, debt ->
                    CoreDebtRecordRow(debt, ListItemPosition.from(index, records.size), selectionMode, debt.id in selected,
                        onClick = { if (!state.saving) { if (selectionMode) onToggle(debt.id) else onOpen(debt) } },
                        onLongClick = { if (!state.saving) { if (selectionMode) onToggle(debt.id) else onActions(debt) } })
                }
            }
        }
    }
}

/** A loan or repayment: direction arrow, title, kind and due date, and the amount in its direction's color. */
@Composable internal fun CoreDebtRecordRow(debt: DebtEntry, position: ListItemPosition, selectionMode: Boolean = false,
    selected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit) {
    val incomeLike = debt.kind == DebtKind.BORROWED || debt.kind == DebtKind.REPAID_TO_ME
    val amountColor = transactionColor(if (incomeLike) TransactionKind.INCOME else TransactionKind.EXPENSE)
    HootbookListItem(headline = {
        Text(debt.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }, supporting = {
        Row(Modifier.basicMarquee(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(listOf(debtKindLabel(debt.kind), debt.date.debtDisplay()).joinToString(" · "), maxLines = 1)
            debt.dueDate?.let { Text(stringResource(R.string.core_debts_due_display, it.debtDisplay()), color = MaterialTheme.colorScheme.tertiary) }
        }
    }, leading = {
        if (selectionMode) Checkbox(selected, { onClick() }, Modifier.size(40.dp))
        else Box(Modifier.size(40.dp).clip(CircleShape).background(amountColor.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(if (incomeLike) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast, null, Modifier.size(22.dp), tint = amountColor)
        }
    }, trailing = {
        Text(CoreMoneyPresentation.format(debt.amount.amount, debt.amount.currency), style = MaterialTheme.typography.titleMedium, color = amountColor)
    }, onClick = onClick, onLongClick = onLongClick, shape = position.toShape(), selected = selected, padding = PaddingValues(0.dp))
}

/** The net balance (who owes whom), with what was lent and borrowed beneath it. */
@Composable internal fun DebtTotals(receivable: String, payable: String, net: String, currency: String, complete: Boolean) {
    val netValue = net.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
    val color = if (netValue.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val status = stringResource(when { netValue.signum() > 0 -> R.string.lend_borrow_you_get; netValue.signum() < 0 -> R.string.lend_borrow_you_owe; else -> R.string.lend_borrow_settled })
    Surface(shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium)
                Surface(shape = CircleShape, color = color.copy(alpha = .12f)) { Text(status, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = color, style = MaterialTheme.typography.labelMedium) }
            }
            Text(CoreMoneyPresentation.format(netValue.abs(), currency), style = MaterialTheme.typography.displaySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple(R.string.total_lent, receivable, MaterialTheme.colorScheme.primary), Triple(R.string.total_borrowed, payable, MaterialTheme.colorScheme.secondary)).forEach { (label, amount, tint) ->
                    Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.large, color = tint.copy(alpha = .10f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = tint)
                            Text(CoreMoneyPresentation.format(amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO, currency), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (!complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A person: avatar (or initial on their color), relation and overdue, and the net with who owes whom. */
@Composable internal fun CorePersonRow(person: Person, avatar: @Composable () -> Unit = {}, selected: Boolean? = null, onLongClick: (() -> Unit)? = null,
    shape: androidx.compose.foundation.shape.CornerBasedShape? = listSingleItemShape, onClick: () -> Unit) {
    val balance = person.totals.net
    HootbookListItem(headline = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supporting = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(relationLabel(person.relation)); if (person.totals.overdue) Text(stringResource(R.string.core_debts_overdue), color = MaterialTheme.colorScheme.error) } },
        leading = { if (selected != null) Checkbox(selected, null) else Box(Modifier.size(40.dp).clip(CircleShape).background(debtColor(person.color)), contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase(), color = Color.White); avatar() } },
        trailing = { Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(when { balance.signum() > 0 -> R.string.lend_borrow_you_get; balance.signum() < 0 -> R.string.lend_borrow_you_owe; else -> R.string.lend_borrow_settled }), style = MaterialTheme.typography.labelMedium)
            Text(CoreMoneyPresentation.format(balance.abs(), person.totals.currency), style = MaterialTheme.typography.titleMedium,
                color = if (balance.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (!person.totals.complete) Text(stringResource(R.string.core_debts_incomplete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        } }, onClick = onClick, onLongClick = onLongClick, selected = selected == true, shape = shape, padding = PaddingValues(0.dp))
}

private fun debtColor(value: String?): Color = Color(runCatching { android.graphics.Color.parseColor(value) }.getOrDefault(0xFF6750A4.toInt()))

@Composable private fun LocalDateTime.debtDisplay(): String = java.time.LocalDateTime.of(year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt()).let {
    io.github.hootkit_dev.hootbook.utils.DateFormats.shortDate(it.toLocalDate()) }
@Composable private fun LocalDate.debtDisplay(): String =
    io.github.hootkit_dev.hootbook.utils.DateFormats.shortDate(java.time.LocalDate.of(year, month.toInt(), day.toInt()))

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

/** The linked account, or none; an archived or missing current link stays selectable and is kept. */
@Composable private fun AccountSelect(accounts: List<Account>, id: String?, enabled: Boolean, choose: (String?) -> Unit) {
    val none = stringResource(R.string.core_debts_no_account)
    val options = listOf(null to none) + accounts.filter { !it.archived || it.id == id }.map { it.id to it.name }
    SelectField(stringResource(R.string.core_debts_account), accounts.find { it.id == id }?.name ?: id ?: none, options, choose, enabled = enabled,
        supporting = if (id != null && accounts.none { it.id == id }) stringResource(R.string.core_debts_account_missing, id) else null)
}

/** Amount and currency side by side; the linked account's currencies are offered as chips. */
@Composable private fun AmountAndCurrency(amount: String, currency: String, accounts: List<Account>, accountId: String?, enabled: Boolean,
    onAmount: (String) -> Unit, onCurrency: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTextField(amount, stringResource(R.string.core_debts_amount), onAmount, Modifier.weight(1.4f), enabled = enabled, decimal = true)
        FormTextField(currency, stringResource(R.string.core_debts_currency), { onCurrency(it.uppercase()) }, Modifier.weight(1f), enabled = enabled)
    }
    accounts.find { it.id == accountId }?.takeIf { it.pockets.size > 1 }?.let { account ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            account.pockets.forEach { pocket -> FilterChip(pocket.currency == currency, { onCurrency(pocket.currency) }, label = { Text(pocket.currency) }, enabled = enabled) }
        }
    }
}
