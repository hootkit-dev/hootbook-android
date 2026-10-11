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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.LendBorrowTransactionActionDialog
import androidx.compose.ui.text.input.TextFieldValue
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import io.github.hootkit_dev.hootbook.presentation.ui.components.SearchBarBox
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem as HootbookListItem
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.AmountInput
import io.github.hootkit_dev.hootbook.presentation.ui.features.add.DecimalAmountPad
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
    initialCreatePerson: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var preview by remember{mutableStateOf<String?>(null)}
    var nameFilter by rememberSaveable{mutableStateOf("")}
    var sortName by rememberSaveable{mutableStateOf(false)}
    var peopleFilter by rememberSaveable { mutableIntStateOf(0) }
    var relationFilter by remember { mutableStateOf<PersonRelation?>(null) }
    var relationMenu by remember { mutableStateOf(false) }
    var selectedPeople by remember { mutableStateOf(setOf<String>()) }
    var selectedDebts by remember { mutableStateOf(setOf<String>()) }
    var confirmBulk by remember { mutableStateOf(false) }
    var recordActions by remember { mutableStateOf<DebtEntry?>(null) }
    val selectionMode = selectedPeople.isNotEmpty() || selectedDebts.isNotEmpty()
    val visiblePeople = state.people.filter { person ->
        person.name.contains(nameFilter, ignoreCase = true) && (relationFilter == null || person.relation == relationFilter) &&
            when (peopleFilter) { 1 -> person.totals.net.signum() > 0; 2 -> person.totals.net.signum() < 0; 3 -> person.totals.overdue; else -> true }
    }.let { if (sortName) it.sortedBy { row -> row.name.lowercase(java.util.Locale.ROOT) } else it }
    LaunchedEffect(state.people, state.debts, state.loading) {
        if (!state.loading) { selectedPeople = selectedPeople.intersect(state.people.map { it.id }.toSet()); selectedDebts = selectedDebts.intersect(state.debts.map { it.id }.toSet()) }
    }
    fun togglePerson(id: String) { selectedPeople = if (id in selectedPeople) selectedPeople - id else selectedPeople + id }
    fun toggleDebt(id: String) { selectedDebts = if (id in selectedDebts) selectedDebts - id else selectedDebts + id }
    var debtKind by rememberSaveable{mutableStateOf<DebtKind?>(null)}
    val avatar=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),viewModel::avatar)
    val attachment=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(),viewModel::attachment)
    val contact=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==android.app.Activity.RESULT_OK)result.data?.data?.let{uri->
            try{val selected=selectedPhoneContact(context.contentResolver,uri);viewModel.selectedContact(selected.first,selected.second)}catch(_:Exception){viewModel.platformFailure()}
        }
    }
    LaunchedEffect(initialPersonId) { viewModel.initialize(initialPersonId) }
    var routedCreate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialCreatePerson, state.loading) {
        if (initialCreatePerson && !state.loading && !routedCreate) {
            routedCreate = true
            if (state.personForm == null) viewModel.editPerson()
        }
    }
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
        if (recordActions != null) { recordActions = null } else if (confirmBulk) { if (!state.saving) confirmBulk = false } else if (deletePerson != null || deleteDebt != null || unlinkDebt != null) closeConfirmation() else if (selectionMode) { selectedPeople = emptySet(); selectedDebts = emptySet() } else goBack()
    }
    LaunchedEffect(state.commandRevision) { closeConfirmation(); confirmBulk = false }
    val settlementTitle = stringResource(R.string.core_debts_settle_title)
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(topBar = {
        CustomTitleTopAppBar(title = if (selectionMode) stringResource(R.string.items_selected_format, selectedPeople.size + selectedDebts.size) else state.person?.name ?: stringResource(R.string.core_debts_title),
            scrollBehaviorSmall = scroll, scrollBehaviorLarge = scroll, hasBackButton = true,
            navigationContent = { NavigationContent { if (selectionMode && !state.saving) { selectedPeople = emptySet(); selectedDebts = emptySet() } else goBack() } }, actionContent = {
                if (selectionMode) {
                    IconButton(onClick = { if (state.selectedId == null) selectedPeople = visiblePeople.map { it.id }.toSet() else selectedDebts = state.debts.map { it.id }.toSet() }, enabled = !state.saving) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.select_all)) }
                    IconButton(onClick = { confirmBulk = true }, enabled = !state.saving) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete_selected)) }
                }
                IconButton(onClick = viewModel::refresh, enabled = !state.saving) { Icon(Icons.Rounded.Refresh, stringResource(R.string.core_debts_refresh)) }
            })
    }, floatingActionButton = {
        if (!selectionMode && state.personForm == null && state.debtForm == null && state.settlement == null) ExtendedFloatingActionButton(
            onClick = { if (state.selectedId == null) viewModel.editPerson() else viewModel.editDebt() },
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(if (state.selectedId == null) R.string.core_debts_add_person else R.string.core_debts_add_record)) })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
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

                    } }
                    item { GenericTypeSwitcher(selectedIndex = peopleFilter, onIndexChange = { peopleFilter = it }, options = listOf(stringResource(R.string.type_all), stringResource(R.string.lend_borrow_filter_lent), stringResource(R.string.lend_borrow_filter_owed), stringResource(R.string.overdue))) }
                    item {
                        SearchBarBox(searchQuery = TextFieldValue(nameFilter), onSearchQueryChange = { nameFilter = it.text }, label = { Text(stringResource(R.string.search)) },
                            trailingIcon = { Box {
                                IconButton(onClick = { relationMenu = true }) { Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.category_filter)) }
                                DropdownMenu(relationMenu, { relationMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.category_all)) }, onClick = { relationFilter = null; relationMenu = false })
                                    PersonRelation.entries.forEach { relation -> DropdownMenuItem(text = { Text(relationLabel(relation)) }, onClick = { relationFilter = relation; relationMenu = false }) }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.core_detail_sort_name)) }, onClick = { sortName = !sortName; relationMenu = false })
                                }
                            } })
                    }
                    if (!state.loading && state.people.isEmpty()) item { Text(stringResource(R.string.core_debts_empty)) }
                    items(visiblePeople, key = { it.id }) { person ->
                        CorePersonRow(person, avatar = {
                            person.avatar?.let { ref -> viewModel.attachmentStore?.let { store -> CoreAttachmentImage(ref, store, Modifier.size(40.dp).clip(CircleShape)) } }
                        }, selected = if (selectionMode) person.id in selectedPeople else null,
                            onLongClick = { if (!state.saving) togglePerson(person.id) },
                            onClick = { if (!state.saving) { if (selectionMode) togglePerson(person.id) else viewModel.select(person.id) } })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                val person = state.person
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        if (person == null) Text(stringResource(R.string.core_debts_orphan))
                        else {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(88.dp).clip(CircleShape).background(debtColor(person.color)), contentAlignment = Alignment.Center) {
                                if (person.avatar == null) Text(person.name.take(1).uppercase(), fontSize = 36.sp, color = Color.White)
                                person.avatar?.let { ref -> viewModel.attachmentStore?.let { store -> CoreAttachmentImage(ref, store, Modifier.fillMaxSize().clickable { preview = ref }) } }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(person.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(relationLabel(person.relation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            person.phone?.let { number ->
                                Text(number)
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    FilledTonalIconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }.onFailure { viewModel.platformFailure() } }) { Icon(Icons.Rounded.Call, stringResource(R.string.core_detail_dial)) }
                                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number.filter(Char::isDigit)))) }.onFailure { viewModel.platformFailure() } }) { Text("WhatsApp") }
                                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+" + number.filter(Char::isDigit)))) }.onFailure { viewModel.platformFailure() } }) { Text("Telegram") }
                                }
                            }
                        }
                        PersonTotalsView(person)
                        person.notes?.let { Text(it) }
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
                    val records = state.debts.filter { debtKind == null || it.kind == debtKind }.sortedByDescending { it.date.debtText() }
                    items(records, key = { it.id }) { debt ->
                        CoreDebtRecordRow(debt, ListItemPosition.from(records.indexOf(debt), records.size), selectionMode, debt.id in selectedDebts,
                            onClick = {
                                if (!state.saving) {
                                    if (selectionMode) toggleDebt(debt.id)
                                    else viewModel.transactionTarget(debt)?.let(onOpenTransactions) ?: run { recordActions = debt }
                                }
                            }, onLongClick = { if (!state.saving) { if (selectionMode) toggleDebt(debt.id) else recordActions = debt } })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
    recordActions?.let { debt -> LendBorrowTransactionActionDialog(
        onEdit = { recordActions = null; viewModel.editDebt(debt) },
        onDelete = { recordActions = null; deleteDebt = debt },
        onSelectMultiple = { recordActions = null; toggleDebt(debt.id) },
        onDismiss = { recordActions = null },
        details = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(debt.title, style = MaterialTheme.typography.titleSmall)
                Text(debt.date.debtText(), style = MaterialTheme.typography.bodySmall)
                debt.accountId?.let { id -> Text(state.accounts.find { it.id == id }?.name ?: id, style = MaterialTheme.typography.bodySmall) }
                debt.attachments.forEachIndexed { index, ref -> TextButton(onClick = { preview = ref }) { Icon(Icons.Rounded.AttachFile, null); Text(stringResource(R.string.core_detail_open_attachment) + " ${index + 1}") } }
                if (debt.transactionId != null) {
                    TextButton(onClick = { recordActions = null; unlinkDebt = debt }) { Text(stringResource(R.string.core_debts_unlink)) }
                    TextButton(onClick = { recordActions = null; viewModel.transactionTarget(debt)?.let(onOpenTransactions) }) { Text(stringResource(R.string.core_debts_open_transaction)) }
                }
            }
        }) }
    state.personForm?.let { form ->
        DebtEditor(stringResource(R.string.core_debts_add_person), state, viewModel::dismissEditor, viewModel::savePerson) {
            CoreTextField(form.name, stringResource(R.string.core_debts_name), state.saving) { viewModel.personChanged(form.copy(name = it)) }
            CoreTextField(form.phone, stringResource(R.string.core_debts_phone), state.saving) { viewModel.personChanged(form.copy(phone = it)) }
            CoreTextField(form.notes, stringResource(R.string.core_debts_notes), state.saving) { viewModel.personChanged(form.copy(notes = it)) }
            ColorPickerContent(initialColor = runCatching { android.graphics.Color.parseColor(form.color) }.getOrDefault(0xFF6750A4.toInt()), onColorChanged = { if (!state.saving) viewModel.personChanged(form.copy(color = "#%06X".format(it and 0xFFFFFF))) })
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
            DebtAmountInput(form.amount, form.currency, !state.saving) { viewModel.debtChanged(form.copy(amount = it)) }
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
                DebtAmountInput(form.amount, form.currency, !state.saving) { viewModel.settlementChanged(form.copy(amount = it)) }
                CurrencyField(state.accounts, form.accountId, form.currency, state.saving) { viewModel.settlementChanged(form.copy(currency = it)) }
            }
            CoreTextField(form.title, stringResource(R.string.core_debts_record_title), state.saving) { viewModel.settlementChanged(form.copy(title = it)) }
            CorePlanDateTimeField(form.date, stringResource(R.string.core_debts_date), state.saving) { viewModel.settlementChanged(form.copy(date = it)) }
            Text(stringResource(R.string.core_debts_settle_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.core_debts_account_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmBulk) AlertDialog(onDismissRequest = { if (!state.saving) confirmBulk = false },
        title = { Text(stringResource(R.string.delete_selected)) },
        text = { Column { Text(stringResource(R.string.core_parity_debt_bulk)); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        dismissButton = { TextButton(onClick = { confirmBulk = false }, enabled = !state.saving) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = { if (state.selectedId == null) viewModel.deletePeople(selectedPeople) else viewModel.deleteDebts(selectedDebts) }, enabled = !state.saving) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } })
    preview?.let{ref->viewModel.attachmentStore?.let{store->CoreAttachmentPreview(ref,store){preview=null}}}
    deletePerson?.let { person -> DebtConfirmation(stringResource(R.string.core_debts_delete_person_title, person.name), stringResource(R.string.core_debts_delete_person_warning), state, closeConfirmation) { viewModel.deletePerson(person) } }
    deleteDebt?.let { debt -> DebtConfirmation(stringResource(R.string.core_debts_delete_record_title), stringResource(R.string.core_debts_delete_record_warning), state, closeConfirmation) { viewModel.deleteDebt(debt) } }
    unlinkDebt?.let { debt -> DebtConfirmation(stringResource(R.string.core_debts_unlink_title), stringResource(R.string.core_debts_unlink_warning), state, closeConfirmation) { viewModel.unlinkDebt(debt) } }
}

/** Original compact record row and selection affordance, with generated Core debt fields. */
@Composable internal fun CoreDebtRecordRow(debt: DebtEntry, position: ListItemPosition, selectionMode: Boolean = false,
    selected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit) {
    val incomeLike = debt.kind == DebtKind.BORROWED || debt.kind == DebtKind.REPAID_TO_ME
    val amountColor = if (incomeLike) Color(0xFF4CAF50) else Color(0xFFE57373)
    HootbookListItem(headline = {
        Text(debt.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }, supporting = {
        Row(Modifier.basicMarquee(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(debtKindLabel(debt.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            debt.dueDate?.let { Text(stringResource(R.string.core_debts_due_display, it.debtText()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold) }
        }
    }, leading = {
        if (selectionMode) Checkbox(selected, { onClick() }, Modifier.size(40.dp))
        else Box(Modifier.size(40.dp).clip(CircleShape).background(amountColor.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(if (incomeLike) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast, null, Modifier.size(22.dp), tint = amountColor)
        }
    }, trailing = {
        Text(CurrencyFormatter.formatCurrency(debt.amount.amount, debt.amount.currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = amountColor)
    }, onClick = onClick, onLongClick = onLongClick, shape = position.toShape(),
        listColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .3f) else MaterialTheme.colorScheme.surfaceContainerLow,
        padding = PaddingValues(0.dp))
}

@Composable private fun PersonTotalsView(person: Person) {
    val totals = person.totals
    DebtTotals(totals.receivable.toPlainString(), totals.payable.toPlainString(), totals.net.toPlainString(), totals.currency, totals.complete)
    if (!totals.complete) Text(stringResource(R.string.core_debts_missing, totals.missingCurrencies.joinToString()))
    if (totals.overdue) Text(stringResource(R.string.core_debts_overdue), color = MaterialTheme.colorScheme.error)
    if (person.archived) Text(stringResource(R.string.core_debts_archived_status))
}
/** Presentation of Core's authoritative totals; never combines or converts ledger values. */
@Composable internal fun DebtTotals(receivable: String, payable: String, net: String, currency: String, complete: Boolean) {
    val netValue = net.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
    val color = if (netValue.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val status = stringResource(when { netValue.signum() > 0 -> R.string.lend_borrow_you_get; netValue.signum() < 0 -> R.string.lend_borrow_you_owe; else -> R.string.lend_borrow_settled })
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(shape = CircleShape, color = color.copy(alpha = .12f)) { Text(status, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = color, style = MaterialTheme.typography.labelMedium) }
            }
            Text(CurrencyFormatter.formatCurrency(netValue.abs(), currency), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple(R.string.total_lent, receivable, MaterialTheme.colorScheme.primary), Triple(R.string.total_borrowed, payable, MaterialTheme.colorScheme.secondary)).forEach { (label, amount, tint) ->
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = tint.copy(alpha = .10f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = tint)
                            Text(CurrencyFormatter.formatCurrency(amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO, currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (!complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error)
        }
    }
}
@Composable internal fun CorePersonRow(person: Person, avatar: @Composable () -> Unit = {}, selected: Boolean? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val balance = person.totals.net
    HootbookListItem(headline = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supporting = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(relationLabel(person.relation)); if (person.totals.overdue) Text(stringResource(R.string.core_debts_overdue), color = MaterialTheme.colorScheme.error) } },
        leading = { if (selected != null) Checkbox(selected, null) else Box(Modifier.size(40.dp).clip(CircleShape).background(debtColor(person.color)), contentAlignment = Alignment.Center) { Text(person.name.take(1).uppercase(), color = Color.White); avatar() } },
        trailing = { Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(when { balance.signum() > 0 -> R.string.lend_borrow_you_get; balance.signum() < 0 -> R.string.lend_borrow_you_owe; else -> R.string.lend_borrow_settled }), style = MaterialTheme.typography.labelSmall)
            Text(CurrencyFormatter.formatCurrency(balance.abs(), person.totals.currency), color = if (balance.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (!person.totals.complete) Text(stringResource(R.string.core_debts_incomplete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        } }, onClick = onClick, onLongClick = onLongClick, selected = selected == true, padding = PaddingValues(0.dp))
}
private fun debtColor(value: String?): Color = Color(runCatching { android.graphics.Color.parseColor(value) }.getOrDefault(0xFF6750A4.toInt()))
@Composable private fun DebtAmountInput(value: String, currency: String, enabled: Boolean, change: (String) -> Unit) {
    var keypad by remember { mutableStateOf(false) }
    AmountInput(value, currency, { if (enabled) keypad = true }, enabled = enabled)
    if (keypad) HootbookModalBottomSheet(onDismissRequest = { keypad = false }) { DecimalAmountPad(value, change); Button(onClick = { keypad = false }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.done)) } }
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
    HootbookModalBottomSheet(onDismissRequest = { if (!state.saving) dismiss() }) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            content()
            Button(onClick = save, enabled = !state.saving && !state.assetLoading, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.core_debts_save)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
@Composable private fun DebtConfirmation(title: String, warning: String, state: CoreDebtsUiState, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column {
        Text(warning)
        if (state.saving) Text(stringResource(R.string.core_debts_saving))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, dismissButton = { TextButton(onClick = dismiss, enabled = !state.saving) { Text(stringResource(R.string.core_debts_cancel)) } },
        confirmButton = { TextButton(onClick = confirm, enabled = !state.saving) { Text(stringResource(R.string.core_debts_delete), color = MaterialTheme.colorScheme.error) } })
}
