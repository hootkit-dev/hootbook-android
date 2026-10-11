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
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
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

                if (state.loading || state.configBusy || state.fileBusy || state.saving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error -> item { Surface(color = MaterialTheme.colorScheme.errorContainer) { Column(Modifier.padding(12.dp)) {
                    Text(error); FlowRow { TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.core_ai_dismiss)) }
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.core_ai_refresh)) } }
                } } } }
                if (state.navigationBlocked) item { Text(stringResource(R.string.core_ai_wait_write)) }
                if (state.phase == CoreAiPhase.COMPOSE) item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(stringResource(R.string.core_ai_configuration), style = MaterialTheme.typography.titleMedium)
                                    Text(state.settings?.model ?: "—", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { settingsOpen = !settingsOpen }, enabled = !state.configBusy) { Text(stringResource(R.string.core_ai_edit)) }
                            }
                            if (settingsOpen || state.settings?.configured != true) Configuration(state, viewModel)
                        }
                    }
                }
                when (state.phase) {
                    CoreAiPhase.COMPOSE -> {
                        item { OutlinedTextField(state.request, viewModel::request, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.core_ai_request)) }, minLines = 3) }
                        item { Button(onClick = { files.launch(arrayOf("image/*", "application/pdf", "text/*")) }, enabled = !state.fileBusy && !state.configBusy) { Text(stringResource(R.string.core_ai_add_files)) } }
                        items(state.files, key = { it.id }) { file -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text(file.name); Text("${file.mimeType} · ${file.size} B")
                            TextButton(onClick = { viewModel.removeFile(file.id) }, enabled = !state.fileBusy) { Text(stringResource(R.string.core_ai_remove)) }
                        } } }
                        if (state.configDirty) item { Text(stringResource(R.string.core_ai_save_config_first)) }
                        item { Text(stringResource(R.string.core_ai_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    CoreAiPhase.PREPARING, CoreAiPhase.RUNNING -> {
                        item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        item { RunningProgress(state) }
                    }
                    CoreAiPhase.REVIEW, CoreAiPhase.SAVED -> {
                        state.review?.summary?.let { summary -> item { Text(summary) } }
                        if (state.review?.stoppedAtLimit == true) item { Text(stringResource(R.string.core_ai_limit)) }
                        if (state.phase == CoreAiPhase.SAVED) item { Text(stringResource(R.string.core_ai_saved, state.applied?.saved?.toInt() ?: 0)) }
                        val groups = listOf(0 to R.string.core_ai_accounts, 1 to R.string.core_ai_additions, 2 to R.string.core_ai_updates, 3 to R.string.core_ai_deletions)
                        groups.forEach { (group, label) ->
                            val entries = state.items.filter { proposalGroup(it.proposal.change) == group && (state.phase != CoreAiPhase.SAVED || it.keep) }
                            if (entries.isNotEmpty()) item { Text(stringResource(label), style = MaterialTheme.typography.titleLarge) }
                            items(entries, key = { it.proposal.index.toString() }) { entry -> ProposalCard(entry, state, { viewModel.keep(entry.proposal.index, it) }, { editing = entry }) }
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
    if (confirmBack) AlertDialog(onDismissRequest = { confirmBack = false }, title = { Text(stringResource(R.string.core_ai_leave_title)) },
        text = { Text(stringResource(R.string.core_ai_leave_body)) },
        confirmButton = { TextButton(onClick = { confirmBack = false; onBack() }) { Text(stringResource(R.string.core_ai_leave)) } },
        dismissButton = { TextButton(onClick = { confirmBack = false }) { Text(stringResource(R.string.core_ai_keep_reviewing)) } })
    if (confirmSave) AlertDialog(onDismissRequest = { confirmSave = false }, title = { Text(stringResource(R.string.core_ai_apply)) },
        text = { Text(stringResource(R.string.core_ai_apply_warning, state.items.count { it.keep })) },
        confirmButton = { TextButton(onClick = { confirmSave = false; viewModel.apply() }) { Text(stringResource(R.string.core_ai_apply)) } },
        dismissButton = { TextButton(onClick = { confirmSave = false }) { Text(stringResource(R.string.core_ai_cancel)) } })
    if (confirmRestart) AlertDialog(onDismissRequest = { confirmRestart = false }, title = { Text(stringResource(R.string.core_ai_restart)) },
        text = { Text(stringResource(R.string.core_ai_restart_warning)) },
        confirmButton = { TextButton(onClick = { confirmRestart = false; viewModel.restart() }) { Text(stringResource(R.string.core_ai_restart)) } },
        dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.core_ai_cancel)) } })
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

@Composable private fun Configuration(state: CoreAiUiState, vm: CoreAiViewModel) {
    val enabled = !state.loading && !state.configBusy && state.phase == CoreAiPhase.COMPOSE
    var pickingModel by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.config.protocol == AiProtocol.CLAUDE, { vm.protocol(AiProtocol.CLAUDE) }, enabled = enabled, label = { Text(stringResource(R.string.ai_preset_claude)) })
            FilterChip(state.config.protocol == AiProtocol.OPEN_AI_COMPATIBLE && state.config.baseUrl.contains("openrouter.ai"), { vm.protocol(AiProtocol.OPEN_AI_COMPATIBLE, "https://openrouter.ai/api/v1") }, enabled = enabled, label = { Text(stringResource(R.string.ai_preset_openrouter)) })
            FilterChip(state.config.protocol == AiProtocol.OPEN_AI_COMPATIBLE && !state.config.baseUrl.contains("openrouter.ai"), { vm.protocol(AiProtocol.OPEN_AI_COMPATIBLE, "") }, enabled = enabled, label = { Text(stringResource(R.string.ai_preset_custom)) })
        }
        FlowRow { AiProtocol.entries.forEach { protocol -> FilterChip(selected = state.config.protocol == protocol, onClick = { vm.protocol(protocol) }, enabled = enabled,
            label = { Text(if (protocol == AiProtocol.CLAUDE) "Claude" else "OpenAI compatible") }) } }
        OutlinedTextField(state.config.baseUrl, { value -> vm.configChanged { it.copy(baseUrl = value) } }, Modifier.fillMaxWidth(), enabled = enabled,
            label = { Text(stringResource(R.string.core_ai_base_url)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(state.config.model, { value -> vm.configChanged { it.copy(model = value) } }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(stringResource(R.string.core_ai_model)) })
        OutlinedTextField(state.config.apiKey, { value -> vm.configChanged { it.copy(apiKey = value, removeKey = false) } }, Modifier.fillMaxWidth(), enabled = enabled,
            label = { Text(stringResource(if (state.settings?.hasKey == true) R.string.core_ai_key_replace else R.string.core_ai_key)) },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(state.config.removeKey, { value -> vm.configChanged { it.copy(removeKey = value, apiKey = "") } }, enabled = enabled)
            Text(stringResource(R.string.core_ai_delete_key), Modifier.weight(1f)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::saveConfig, enabled = enabled && state.configDirty) { Text(stringResource(R.string.core_ai_save_configuration)) }
            OutlinedButton(onClick = { vm.loadModels(); pickingModel = true }, enabled = enabled && !state.modelLoading && !state.config.removeKey) { Text(stringResource(R.string.core_ai_models)) }
        }
        if (state.modelLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (pickingModel) HootbookModalBottomSheet(onDismissRequest = { pickingModel = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.core_ai_models), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(state.modelSearch, vm::modelSearch, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.core_ai_search_models)) })
                if (state.modelLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(state.models.filter { state.modelSearch.isBlank() || it.id.contains(state.modelSearch, true) || it.displayName.contains(state.modelSearch, true) }, key = { it.id }) { model ->
                        ListItem(headlineContent = { Text(model.displayName) }, supportingContent = { Text(model.id + if (model.readsImages) " ◉" else "") },
                            leadingContent = { RadioButton(state.config.model == model.id, onClick = { vm.selectModel(model.id); pickingModel = false }, enabled = enabled) })
                    }
                }
            }
        }
    } }
}
@Composable private fun RunningProgress(state: CoreAiUiState) {
    var seconds by remember { mutableLongStateOf(0) }
    LaunchedEffect(state.phase) { val start = SystemClock.elapsedRealtime()
        while (true) { seconds = (SystemClock.elapsedRealtime() - start) / 1000; delay(1000) } }
    val turn = state.steps.filterIsInstance<AiStep.Asking>().lastOrNull()?.turn ?: 0u
    Text(stringResource(R.string.core_ai_progress, state.settings?.model.orEmpty(), turn.toInt(), seconds.toInt()))
}
private fun proposalGroup(change: AiChange) = when (change) { is AiChange.AddTransaction -> 1; is AiChange.UpdateTransaction -> 2; is AiChange.DeleteTransaction -> 3; else -> 0 }
@Composable private fun ProposalCard(item: CoreAiReviewItem, state: CoreAiUiState, onKeep: (Boolean) -> Unit, onEdit: () -> Unit) {
    val change = item.proposal.change
    val draft = item.edited ?: (change as? AiChange.AddTransaction)?.draft
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.phase == CoreAiPhase.REVIEW) Checkbox(item.keep, onKeep, enabled = !state.saving)
            if (draft != null) {
                val category = state.categories.find { it.id == draft.categoryId }
                CoreRecordIcon(category?.icon.orEmpty(), Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(draft.merchant.ifBlank { category?.displayName ?: CoreKindLabel(draft.kind) }, style = MaterialTheme.typography.titleSmall)
                    Text("${draft.dateTime.year}-${draft.dateTime.month}-${draft.dateTime.day} · ${category?.displayName.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    (change as? AiChange.AddTransaction)?.possibleDuplicate?.let { Text(stringResource(R.string.core_ai_duplicate, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                }
                Text(CurrencyFormatter.formatCurrency(draft.amount, draft.currency), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onEdit, enabled = !state.saving && state.phase == CoreAiPhase.REVIEW) { Icon(Icons.Rounded.Edit, stringResource(R.string.core_ai_edit)) }
            } else Column(Modifier.weight(1f)) {
                when (change) {
                    is AiChange.UpdateTransaction -> {
                        Text("${change.before.merchant} → ${change.after.merchant}", style = MaterialTheme.typography.titleSmall)
                        Text(CurrencyFormatter.formatCurrency(change.before.amount, change.before.currency))
                        Text(proposalText(change, state), style = MaterialTheme.typography.bodySmall)
                    }
                    is AiChange.DeleteTransaction -> {
                        Text(change.before.merchant, style = MaterialTheme.typography.titleSmall)
                        Text(CurrencyFormatter.formatCurrency(change.before.amount, change.before.currency), color = MaterialTheme.colorScheme.error)
                        change.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    else -> Text(proposalText(change, state), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
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
@Composable private fun DraftDialog(index: UInt, draft: AiTransactionDraft, state: CoreAiUiState, onDismiss: () -> Unit, onSave: (CoreAiDraftForm) -> Unit) {
    var form by remember(index) { mutableStateOf(CoreAiDraftForm.from(draft)) }
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.core_ai_edit), style = MaterialTheme.typography.headlineSmall)
            Picker(stringResource(R.string.core_ai_kind), form.kind.name, listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER).map { it to it.name }) { form = form.copy(kind = it) }
            AiField(stringResource(R.string.core_ai_amount), form.amount, true) { form = form.copy(amount = it) }
            AiField(stringResource(R.string.core_ai_currency), form.currency) { form = form.copy(currency = it) }
            AiField(stringResource(R.string.core_ai_date), form.dateTime) { form = form.copy(dateTime = it) }
            AiField(stringResource(R.string.core_ai_merchant), form.merchant) { form = form.copy(merchant = it) }
            val refs: List<Pair<AiAccountRef?, String>> = listOf(null to stringResource(R.string.core_ai_default)) +
                state.accounts.map { AiAccountRef.Existing(it.id) to it.name } + state.items.mapNotNull { (it.proposal.change as? AiChange.CreateAccount)?.let { account -> AiAccountRef.New(account.reference) to "${account.reference} · ${account.name}" } }
            Picker(stringResource(R.string.core_ai_account), refs.find { it.first == form.account }?.second ?: form.account.toString(), refs) { form = form.copy(account = it) }
            val categories = state.categories.filter { it.kind == if (form.kind == TransactionKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE }
            Picker(stringResource(R.string.core_ai_category), state.categories.find { it.id == form.categoryId }?.displayName ?: form.categoryId ?: "—",
                listOf(null to "—") + categories.map { it.id to it.displayName }) { form = form.copy(categoryId = it, subcategoryId = null) }
            val subcategories = state.categories.find { it.id == form.categoryId }?.subcategories.orEmpty()
            Picker(stringResource(R.string.core_ai_subcategory), subcategories.find { it.id == form.subcategoryId }?.displayName ?: form.subcategoryId ?: "—",
                listOf(null to "—") + subcategories.map { it.id to it.displayName }) { form = form.copy(subcategoryId = it) }
            AiField(stringResource(R.string.core_ai_notes), form.notes) { form = form.copy(notes = it) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (form.kind == TransactionKind.TRANSFER) {
                Picker(stringResource(R.string.core_ai_to_account), refs.find { it.first == form.toAccount }?.second ?: form.toAccount.toString(), refs) { form = form.copy(toAccount = it) }
                AiField(stringResource(R.string.core_ai_to_currency), form.toCurrency) { form = form.copy(toCurrency = it) }
                AiField(stringResource(R.string.core_ai_to_amount), form.toAmount, true) { form = form.copy(toAmount = it) }
            }
            Button(onClick = { onSave(form) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.core_ai_done)) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.core_ai_cancel)) }
        }
    }
}
@Composable private fun AiField(label: String, value: String, decimal: Boolean = false, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable private fun <T> Picker(label: String, selected: String, values: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text("$label · $selected") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { values.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(id) }) } } }
}
