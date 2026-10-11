@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.TransactionItem
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.core.analytics.CoreKindLabel
import io.github.hootkit_dev.hootbook.presentation.ui.components.*
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import kotlinx.coroutines.delay

/** Destination-scoped VM retains native session through configuration changes, never process death. */
@Composable
fun CoreAiScreen(onBack: () -> Unit, viewModel: CoreAiViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var confirmBack by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CoreAiReviewItem?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val back = {
        if (!viewModel.back()) {
            if (state.phase in setOf(CoreAiPhase.REVIEW, CoreAiPhase.SAVED)) confirmBack = true else onBack()
        }
    }
    BackHandler(onBack = back)
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), viewModel::addFiles)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    var logOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.core_ai_saved, state.applied?.saved?.toInt() ?: 0)
    val undoLabel = stringResource(R.string.core_ai_undo)
    LaunchedEffect(state.phase) {
        if (state.phase == CoreAiPhase.SAVED && snackbar.showSnackbar(savedMessage, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.undo()
    }
    LaunchedEffect(state.steps.size) {
        if (state.phase == CoreAiPhase.RUNNING && listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection).imePadding(), contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { CustomTitleTopAppBar(title = stringResource(R.string.ai_assistant_title), scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(), scrollBehaviorLarge = scroll,
            hasBackButton = true, navigationContent = { NavigationContent(back) }) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    when (state.phase) {
                        CoreAiPhase.COMPOSE -> Button(onClick = viewModel::analyze, enabled = !state.loading && !state.fileBusy && !state.configBusy && !state.configDirty && state.settings?.configured == true && (state.request.isNotBlank() || state.files.isNotEmpty())) { Text(stringResource(R.string.core_ai_analyze)) }
                        CoreAiPhase.PREPARING, CoreAiPhase.RUNNING -> OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.core_ai_cancel)) }
                        CoreAiPhase.REVIEW -> {
                            TextButton(onClick = { confirmRestart = true }, enabled = !state.saving) { Text(stringResource(R.string.core_ai_restart)) }
                            Button(onClick = { confirmSave = true }, enabled = !state.saving && state.items.any { it.keep }) { Text(stringResource(R.string.core_ai_apply)) }
                        }
                        CoreAiPhase.SAVED -> {
                            TextButton(onClick = viewModel::undo, enabled = !state.saving) { Text(stringResource(R.string.core_ai_undo)) }
                            OutlinedButton(onClick = { confirmRestart = true }, enabled = !state.saving) { Text(stringResource(R.string.core_ai_restart)) }
                            Button(onClick = onBack, enabled = !state.saving) { Text(stringResource(R.string.core_ai_done)) }
                        }
                    }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 880.dp).fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                state.error?.let { error -> item { ErrorBanner(error, viewModel::clearError, actionLabel = stringResource(R.string.core_ai_refresh), onAction = viewModel::refresh) } }
                if (state.navigationBlocked) item { Text(stringResource(R.string.core_ai_wait_write)) }
                if (state.phase == CoreAiPhase.COMPOSE) item {
                    val configured = state.settings?.configured == true
                    ListSection {
                        item(leading = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                            headline = { Text(stringResource(R.string.core_ai_configuration)) },
                            supporting = { Text(if (configured) state.settings?.model.orEmpty() else stringResource(R.string.ai_not_configured),
                                color = if (configured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) },
                            chevron = true, onClick = { if (!state.configBusy) settingsOpen = true })
                    }
                }
                when (state.phase) {
                    CoreAiPhase.COMPOSE -> {
                        item { OutlinedTextField(state.request, viewModel::request, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.core_ai_request)) }, minLines = 3) }
                        item { ListSection(title = stringResource(R.string.txn_section_attachments)) {
                            state.files.forEach { file ->
                                item(leading = { Icon(if (file.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.Description, null) },
                                    headline = { Text(file.name, maxLines = 1) },
                                    supporting = { Text(android.text.format.Formatter.formatShortFileSize(LocalContext.current, file.size.toLong())) },
                                    trailing = { TooltipIconButton(Icons.Rounded.Close, stringResource(R.string.core_ai_remove), { viewModel.removeFile(file.id) }, enabled = !state.fileBusy) })
                            }
                            item(leading = { Icon(Icons.Rounded.AttachFile, null) }, headline = { Text(stringResource(R.string.core_ai_add_files)) },
                                onClick = { if (!state.fileBusy && !state.configBusy) files.launch(arrayOf("image/*", "application/pdf", "text/*")) })
                        } }
                        if (state.configDirty) item { Text(stringResource(R.string.core_ai_save_config_first)) }
                        item { Text(stringResource(R.string.core_ai_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    CoreAiPhase.PREPARING, CoreAiPhase.RUNNING -> {
                        item { RunningProgress(state) }
                    }
                    CoreAiPhase.REVIEW, CoreAiPhase.SAVED -> {
                        state.review?.summary?.let { summary -> item { Text(summary) } }
                        if (state.review?.stoppedAtLimit == true) item { Text(stringResource(R.string.core_ai_limit)) }
                        if (state.phase == CoreAiPhase.SAVED) item { Text(stringResource(R.string.core_ai_saved, state.applied?.saved?.toInt() ?: 0)) }
                        val groups = listOf(0 to R.string.core_ai_accounts, 1 to R.string.core_ai_additions, 2 to R.string.core_ai_updates, 3 to R.string.core_ai_deletions)
                        groups.forEach { (group, label) ->
                            val entries = state.items.filter { proposalGroup(it.proposal.change) == group && (state.phase != CoreAiPhase.SAVED || it.keep) }
                            if (entries.isNotEmpty()) item(key = "group-$group") { ListSection(title = stringResource(label)) {
                                entries.forEach { entry -> row { shape -> ProposalCard(entry, state, shape, { viewModel.keep(entry.proposal.index, it) }, { editing = entry }) } }
                            } }
                        }
                        if (state.items.isEmpty()) item { Text(stringResource(R.string.core_ai_no_changes)) }
                    }
                }
                if (state.steps.isNotEmpty()) item {
                    TextButton(onClick = { logOpen = !logOpen }) {
                        Icon(if (logOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                        Text(stringResource(R.string.core_ai_timeline))
                    }
                }
                if (state.phase in setOf(CoreAiPhase.PREPARING, CoreAiPhase.RUNNING) || logOpen) items(state.steps.size, key = { "step-$it" }) { index -> StepCard(state.steps[index]) }
            }
        }
    }
    if (confirmBack) ConfirmDialog(stringResource(R.string.core_ai_leave_title), stringResource(R.string.core_ai_leave_body), stringResource(R.string.core_ai_leave),
        { confirmBack = false; onBack() }, { confirmBack = false }, destructive = true)
    if (confirmSave) ConfirmDialog(stringResource(R.string.core_ai_apply), stringResource(R.string.core_ai_apply_warning, state.items.count { it.keep }),
        stringResource(R.string.core_ai_apply), { confirmSave = false; viewModel.apply() }, { confirmSave = false })
    if (confirmRestart) ConfirmDialog(stringResource(R.string.core_ai_restart), stringResource(R.string.core_ai_restart_warning), stringResource(R.string.core_ai_restart),
        { confirmRestart = false; viewModel.restart() }, { confirmRestart = false }, destructive = true)
    if (settingsOpen) ConfigurationSheet(state, viewModel) { settingsOpen = false }
    state.passwordFile?.let { file ->
        var password by remember(file.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { viewModel.password(null) }, title = { Text(stringResource(R.string.core_ai_pdf_password)) },
            text = { Column { Text(file.name); OutlinedTextField(password, { password = it }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)) } },
            confirmButton = { TextButton(onClick = { viewModel.password(password); password = "" }) { Text(stringResource(R.string.core_ai_continue)) } },
            dismissButton = { TextButton(onClick = { viewModel.password(null); password = "" }) { Text(stringResource(R.string.core_ai_cancel)) } })
    }
    editing?.let { item ->
        val add = item.proposal.change as? AiChange.AddTransaction
        if (add != null) DraftDialog(item.proposal.index, item.edited ?: add.draft, state, onDismiss = { editing = null },
            onSave = { form -> if (viewModel.edit(item.proposal.index, form)) editing = null })
    }
}
private tailrec fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }

/** Provider preset and protocol, address, model (picked from the provider's list), API key. */
@Composable private fun ConfigurationSheet(state: CoreAiUiState, vm: CoreAiViewModel, onDismiss: () -> Unit) {
    val enabled = !state.loading && !state.configBusy && state.phase == CoreAiPhase.COMPOSE
    var pickingModel by remember { mutableStateOf(false) }
    FormSheet(stringResource(R.string.core_ai_configuration), onDismiss, { vm.saveConfig() }, busy = state.configBusy,
        confirmEnabled = enabled && state.configDirty, confirmLabel = stringResource(R.string.core_ai_save_configuration), error = state.error) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.config.protocol == AiProtocol.CLAUDE, { vm.protocol(AiProtocol.CLAUDE) }, enabled = enabled, label = { Text(stringResource(R.string.ai_preset_claude)) })
            FilterChip(state.config.protocol == AiProtocol.OPEN_AI_COMPATIBLE && state.config.baseUrl.contains("openrouter.ai"), { vm.protocol(AiProtocol.OPEN_AI_COMPATIBLE, "https://openrouter.ai/api/v1") }, enabled = enabled, label = { Text(stringResource(R.string.ai_preset_openrouter)) })
            FilterChip(state.config.protocol == AiProtocol.OPEN_AI_COMPATIBLE && !state.config.baseUrl.contains("openrouter.ai"), { vm.protocol(AiProtocol.OPEN_AI_COMPATIBLE, "") }, enabled = enabled, label = { Text(stringResource(R.string.ai_preset_custom)) })
        }
        OutlinedTextField(state.config.baseUrl, { value -> vm.configChanged { it.copy(baseUrl = value) } }, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            label = { Text(stringResource(R.string.core_ai_base_url)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(state.config.model, { value -> vm.configChanged { it.copy(model = value) } }, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            label = { Text(stringResource(R.string.core_ai_model)) },
            trailingIcon = { TooltipIconButton(Icons.AutoMirrored.Rounded.List, stringResource(R.string.core_ai_models), { vm.loadModels(); pickingModel = true },
                enabled = enabled && !state.modelLoading && !state.config.removeKey) })
        OutlinedTextField(state.config.apiKey, { value -> vm.configChanged { it.copy(apiKey = value, removeKey = false) } }, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            label = { Text(stringResource(if (state.settings?.hasKey == true) R.string.core_ai_key_replace else R.string.core_ai_key)) },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (state.settings?.hasKey == true) ListSection {
            item(headline = { Text(stringResource(R.string.core_ai_delete_key)) }, trailing = { HootbookSwitch(state.config.removeKey, null, enabled = enabled) },
                toggled = state.config.removeKey, onToggle = { value -> if (enabled) vm.configChanged { it.copy(removeKey = value, apiKey = "") } })
        }
    }
    if (pickingModel) HootbookModalBottomSheet(onDismissRequest = { pickingModel = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.core_ai_models), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmall)
            SearchBarBox(searchQuery = androidx.compose.ui.text.input.TextFieldValue(state.modelSearch), onSearchQueryChange = { vm.modelSearch(it.text) },
                label = { Text(stringResource(R.string.core_ai_search_models)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            val models = state.models.filter { state.modelSearch.isBlank() || it.id.contains(state.modelSearch, true) || it.displayName.contains(state.modelSearch, true) }
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(models.size, key = { models[it].id }) { index -> val model = models[index]
                    io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem(headline = { Text(model.displayName) },
                        supporting = { Text(model.id) },
                        trailing = { Row { if (model.readsImages) Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.config.model == model.id) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) } },
                        selected = state.config.model == model.id, shape = ListItemPosition.from(index, models.size).toShape(), padding = PaddingValues(0.dp),
                        onClick = { if (enabled) { vm.selectModel(model.id); pickingModel = false } })
                }
            }
        }
    }
}
@Composable private fun RunningProgress(state: CoreAiUiState) {
    var seconds by remember { mutableLongStateOf(0) }
    LaunchedEffect(state.phase) { val start = SystemClock.elapsedRealtime()
        while (true) { seconds = (SystemClock.elapsedRealtime() - start) / 1000; delay(1000) } }
    val turn = state.steps.filterIsInstance<AiStep.Asking>().lastOrNull()?.turn ?: 0u
    Text(stringResource(R.string.core_ai_progress, state.settings?.model.orEmpty(), turn.toInt(), seconds.toInt()))
}
private fun proposalGroup(change: AiChange) = when (change) { is AiChange.AddTransaction -> 1; is AiChange.UpdateTransaction -> 2; is AiChange.DeleteTransaction -> 3; else -> 0 }
/** One proposed change: a checkbox to keep it, what it is, its amount; a new transaction can be corrected. */
@Composable private fun ProposalCard(item: CoreAiReviewItem, state: CoreAiUiState, shape: androidx.compose.foundation.shape.CornerBasedShape,
    onKeep: (Boolean) -> Unit, onEdit: () -> Unit) {
    val change = item.proposal.change
    val draft = item.edited ?: (change as? AiChange.AddTransaction)?.draft
    val reviewing = state.phase == CoreAiPhase.REVIEW
    io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem(
        leading = { if (reviewing) Checkbox(item.keep, onKeep, enabled = !state.saving)
            else draft?.let { d -> CoreRecordIcon(state.categories.find { it.id == d.categoryId }?.icon.orEmpty(), Modifier.size(40.dp)) } },
        headline = { Text(when {
            draft != null -> draft.merchant.ifBlank { state.categories.find { it.id == draft.categoryId }?.displayName ?: CoreKindLabel(draft.kind) }
            change is AiChange.UpdateTransaction -> "${change.before.merchant} → ${change.after.merchant}"
            change is AiChange.DeleteTransaction -> change.before.merchant
            else -> proposalText(change, state).lineSequence().first()
        }, maxLines = 2) },
        supporting = { Column {
            when {
                draft != null -> {
                    Text(listOfNotNull(draft.dateTime.let { io.github.hootkit_dev.hootbook.utils.DateFormats.shortDate(java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt())) },
                        state.categories.find { it.id == draft.categoryId }?.displayName).joinToString(" · "))
                    (change as? AiChange.AddTransaction)?.possibleDuplicate?.let { Text(stringResource(R.string.core_ai_duplicate, it), color = MaterialTheme.colorScheme.error) }
                }
                change is AiChange.DeleteTransaction -> change.reason?.let { Text(it) }
                change is AiChange.UpdateTransaction -> Text(proposalText(change, state).lines().drop(1).take(2).joinToString("\n"))
                else -> Text(proposalText(change, state).lines().drop(1).filter { it.isNotBlank() }.joinToString("\n"))
            }
        } },
        trailing = { Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                draft != null -> Text(CoreMoneyPresentation.format(draft.amount, draft.currency), style = MaterialTheme.typography.titleMedium)
                change is AiChange.DeleteTransaction -> Text(CoreMoneyPresentation.format(change.before.amount, change.before.currency), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error)
                change is AiChange.UpdateTransaction -> Text(CoreMoneyPresentation.format(change.before.amount, change.before.currency), style = MaterialTheme.typography.titleMedium)
            }
            if (draft != null && reviewing) TooltipIconButton(Icons.Rounded.Edit, stringResource(R.string.core_ai_edit), onEdit, enabled = !state.saving)
        } },
        shape = shape, padding = PaddingValues(0.dp),
    )
}
private fun proposalText(change: AiChange, state: CoreAiUiState): String {
    fun account(id: String) = state.accounts.find { it.id == id }?.name ?: id
    fun ref(ref: AiAccountRef?) = when (ref) { is AiAccountRef.Existing -> account(ref.accountId); is AiAccountRef.New -> "${ref.reference} (new)"; null -> "—" }
    fun category(id: String?) = state.categories.find { it.id == id }?.displayName ?: id.orEmpty()
    return when (change) {
        is AiChange.AddTransaction -> change.draft.let { "${it.kind} · ${it.amount.toPlainString()} ${it.currency}\n${it.merchant}\n${it.dateTime}\n${ref(it.account)} · ${category(it.categoryId)}\n${it.notes.orEmpty()}" +
            if (it.kind == TransactionKind.TRANSFER) "\n→ ${ref(it.toAccount)} · ${it.toAmount?.toPlainString().orEmpty()} ${it.toCurrency.orEmpty()}" else "" }
        is AiChange.UpdateTransaction -> "${change.before.merchant} → ${change.after.merchant}\n${category(change.before.categoryId)} → ${category(change.after.categoryId)}\n${change.before.notes.orEmpty()} → ${change.after.notes.orEmpty()}\n${change.transactionId}"
        is AiChange.DeleteTransaction -> "${change.before.merchant} · ${change.before.amount.toPlainString()} ${change.before.currency}\n${change.transactionId}\n${change.reason.orEmpty()}"
        is AiChange.CreateAccount -> "${change.reference} · ${change.name} · ${change.kind}\n${change.balance.toPlainString()} ${change.currency}\n${change.last4.orEmpty()} · ${change.institutionKey.orEmpty()}\ncredit limit: ${change.creditLimit?.toPlainString().orEmpty()}\n${change.icon} · ${change.color}"
        is AiChange.SetBalance -> "${account(change.accountId)} · ${change.balance.toPlainString()} ${change.currency}\ncredit limit: ${change.creditLimit?.toPlainString().orEmpty()}"
        is AiChange.UpdateAccount -> "${account(change.accountId)} → ${change.name.orEmpty()} · ${change.kind ?: ""}\ncredit limit: ${change.creditLimit?.toPlainString().orEmpty()}\n+ ${change.addCurrencies.joinToString()}"
    }
}
@Composable private fun StepCard(step: AiStep) {
    var expanded by remember { mutableStateOf(false) }
    val title = when (step) { is AiStep.Sent -> stringResource(R.string.core_ai_step_sent)
        is AiStep.Asking -> stringResource(R.string.core_ai_step_asking, step.turn.toInt())
        is AiStep.Replied -> stringResource(R.string.core_ai_step_replied, step.turn.toInt(), step.durationMs.toInt())
        is AiStep.Searched -> stringResource(R.string.core_ai_step_searched, step.found?.toString() ?: "—")
        is AiStep.Proposed -> "${step.tool} · #${step.changeIndexes.joinToString()}" + if (step.rejected) " ⚠" else "" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(when (step) { is AiStep.Sent -> Icons.Rounded.Send; is AiStep.Asking -> Icons.Rounded.AutoAwesome; is AiStep.Replied -> Icons.Rounded.ChatBubbleOutline; is AiStep.Searched -> Icons.Rounded.Search; is AiStep.Proposed -> Icons.Rounded.Check }, null, Modifier.size(18.dp)) }
            }
            Box(Modifier.width(2.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.weight(1f)) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(title, Modifier.weight(1f)); Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) }
        if (expanded) when (step) {
            is AiStep.Sent -> { Text(step.userText); Text(step.systemPrompt) }
            is AiStep.Asking -> Text(title)
            is AiStep.Replied -> { Text(step.text.orEmpty()); Text(step.thinking.orEmpty())
                Text("tokens: ${step.inputTokens ?: "—"} / ${step.outputTokens ?: "—"} · truncated: ${step.truncated}")
                step.toolCalls.forEach { Text("${it.id} · ${it.name}"); Text(it.arguments) } }
            is AiStep.Searched -> { Text("${step.from.orEmpty()} → ${step.to.orEmpty()} · ${step.text.orEmpty()}"); Text(step.arguments); Text(step.result) }
            is AiStep.Proposed -> { Text(step.arguments); Text(step.result) }
        }
    } }
}
/** Correct a proposed transaction before saving it. */
@Composable private fun DraftDialog(index: UInt, draft: AiTransactionDraft, state: CoreAiUiState, onDismiss: () -> Unit, onSave: (CoreAiDraftForm) -> Unit) {
    var form by remember(index) { mutableStateOf(CoreAiDraftForm.from(draft)) }
    FormSheet(stringResource(R.string.core_ai_edit), onDismiss, { onSave(form) }, confirmLabel = stringResource(R.string.core_ai_done), error = state.error) {
        SelectField(stringResource(R.string.core_ai_kind), CoreKindLabel(form.kind), listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER).map { it to CoreKindLabel(it) },
            { form = form.copy(kind = it) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(form.amount, stringResource(R.string.core_ai_amount), { form = form.copy(amount = it) }, Modifier.weight(1.4f), decimal = true)
            FormTextField(form.currency, stringResource(R.string.core_ai_currency), { form = form.copy(currency = it.uppercase()) }, Modifier.weight(1f))
        }
        DateTimeField(form.dateTime, stringResource(R.string.core_ai_date), { form = form.copy(dateTime = it) }, allowClear = false)
        FormTextField(form.merchant, stringResource(R.string.core_ai_merchant), { form = form.copy(merchant = it) })
        val refs: List<Pair<AiAccountRef?, String>> = listOf(null to stringResource(R.string.core_ai_default)) +
            state.accounts.map { AiAccountRef.Existing(it.id) to it.name } + state.items.mapNotNull { (it.proposal.change as? AiChange.CreateAccount)?.let { account -> AiAccountRef.New(account.reference) to "${account.reference} · ${account.name}" } }
        SelectField(stringResource(R.string.core_ai_account), refs.find { it.first == form.account }?.second ?: form.account.toString(), refs, { form = form.copy(account = it) })
        val categories = state.categories.filter { it.kind == if (form.kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }
        SelectField(stringResource(R.string.core_ai_category), state.categories.find { it.id == form.categoryId }?.displayName ?: form.categoryId ?: "—",
            listOf(null to "—") + categories.map { it.id to it.displayName }, { form = form.copy(categoryId = it, subcategoryId = null) })
        val subcategories = state.categories.find { it.id == form.categoryId }?.subcategories.orEmpty()
        if (subcategories.isNotEmpty()) SelectField(stringResource(R.string.core_ai_subcategory), subcategories.find { it.id == form.subcategoryId }?.displayName ?: form.subcategoryId ?: "—",
            listOf(null to "—") + subcategories.map { it.id to it.displayName }, { form = form.copy(subcategoryId = it) })
        FormTextField(form.notes, stringResource(R.string.core_ai_notes), { form = form.copy(notes = it) })
        if (form.kind == TransactionKind.TRANSFER) {
            SelectField(stringResource(R.string.core_ai_to_account), refs.find { it.first == form.toAccount }?.second ?: form.toAccount.toString(), refs, { form = form.copy(toAccount = it) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(form.toAmount, stringResource(R.string.core_ai_to_amount), { form = form.copy(toAmount = it) }, Modifier.weight(1.4f), decimal = true)
                FormTextField(form.toCurrency, stringResource(R.string.core_ai_to_currency), { form = form.copy(toCurrency = it.uppercase()) }, Modifier.weight(1f))
            }
        }
    }
}
