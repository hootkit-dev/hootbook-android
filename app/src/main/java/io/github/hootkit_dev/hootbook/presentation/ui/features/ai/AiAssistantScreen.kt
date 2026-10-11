@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import java.net.URI
import kotlinx.coroutines.delay

/**
 * AI bookkeeping: files shared to Hootbook (or picked here) and a request in plain words go to the
 * user's own cloud model, which proposes additions, edits and deletions; nothing is saved until the
 * user ticks what to keep.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiAssistantScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiAssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The proposed change whose details are open
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    // Whether the finished run's steps are shown under the review
    var runLogOpen by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    // Follow the run as steps come in
    val runningSteps = state.steps.size.takeIf { state.phase == AiPhase.RUNNING }
    LaunchedEffect(runningSteps) {
        if (runningSteps != null && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }
    val defaultRequest = stringResource(R.string.ai_default_request)
    val pickFiles =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.addFiles(uris)
        }

    // Leaving an unsaved review asks first: the results would otherwise be lost
    var confirmDiscard by remember { mutableStateOf<(() -> Unit)?>(null) }
    val unsavedReview = state.phase == AiPhase.REVIEW && state.items.isNotEmpty()
    val leave = {
        if (viewModel.back()) Unit
        else if (unsavedReview) confirmDiscard = onNavigateBack else onNavigateBack()
    }

    // Back from a review or a running request returns to the request first
    BackHandler(
        enabled =
            state.phase != AiPhase.COMPOSE || state.saving || state.configBusy || state.fileBusy
    ) {
        if (!viewModel.back())
            when (state.phase) {
                AiPhase.PREPARING,
                AiPhase.RUNNING -> viewModel.cancel()
                AiPhase.REVIEW ->
                    if (unsavedReview) confirmDiscard = viewModel::restart else viewModel.restart()
                else -> viewModel.restart()
            }
    }
    confirmDiscard?.let { discard ->
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text(stringResource(R.string.ai_discard_title)) },
            text = { Text(stringResource(R.string.ai_discard_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = null
                        discard()
                    }
                ) {
                    Text(stringResource(R.string.ai_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = null }) {
                    Text(stringResource(R.string.ai_cancel))
                }
            },
        )
    }

    // Saving ends in a snackbar with Undo, as deleting a transaction does elsewhere
    val snackbarHostState = remember { SnackbarHostState() }
    val savedPhase = state.applied.takeIf { state.phase == AiPhase.SAVED }
    val savedMessage = savedPhase?.let { stringResource(R.string.ai_saved, it.saved.toInt()) }
    val undoLabel = stringResource(R.string.ai_undo)
    LaunchedEffect(savedPhase) {
        if (savedPhase != null && savedMessage != null) {
            val result =
                snackbarHostState.showSnackbar(
                    savedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Shrink above the keyboard so the field being typed in and the action bar stay visible
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).imePadding(),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.ai_assistant_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(leave) },
            )
        },
        bottomBar = {
            AiBottomBar(
                state = state,
                onAnalyze = {
                    if (state.request.isBlank()) viewModel.request(defaultRequest)
                    viewModel.analyze()
                },
                onCancel = viewModel::cancel,
                onSave = viewModel::apply,
                onStartOver = viewModel::restart,
                onDone = {
                    viewModel.restart()
                    onNavigateBack()
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            contentPadding =
                PaddingValues(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = Dimensions.Padding.content + padding.calculateTopPadding(),
                    bottom = Dimensions.Padding.content + padding.calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.error?.let { error ->
                item(key = "error") { ErrorCard(error, onDismiss = viewModel::clearError) }
            }
            when (val phase = state.phase) {
                AiPhase.COMPOSE -> {
                    item(key = "provider") {
                        ProviderCard(
                            state.config,
                            state.models,
                            state.modelLoading,
                            state.modelError,
                            state.settings?.configured == true,
                            busy = state.configBusy,
                            onLoadModels = { draft ->
                                viewModel.configChanged { draft }
                                viewModel.loadModels()
                            },
                            onSave = { draft ->
                                viewModel.configChanged { draft }
                                viewModel.saveConfig()
                            },
                        )
                    }
                    item(key = "files") {
                        FilesSection(
                            names = state.files.map { it.name to it.mimeType.startsWith("image/") },
                            onAdd = {
                                pickFiles.launch(arrayOf("image/*", "application/pdf", "text/*"))
                            },
                            onRemove = { index -> viewModel.removeFile(state.files[index].id) },
                        )
                    }
                    item(key = "request") {
                        OutlinedTextField(
                            value = state.request,
                            onValueChange = viewModel::request,
                            placeholder = { Text(stringResource(R.string.ai_request_hint)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if ((state.settings?.configured == true && !state.configDirty)) {
                        item(key = "privacy") {
                            Text(
                                text =
                                    stringResource(
                                        R.string.ai_privacy_note,
                                        host(state.config.baseUrl),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                AiPhase.PREPARING,
                AiPhase.RUNNING -> aiProgress(state, state.config.model)
                AiPhase.REVIEW,
                AiPhase.SAVED -> {
                    val saved = state.phase == AiPhase.SAVED
                    // Once saved, the page is a record of what was written, not a proposal
                    val header =
                        if (saved) savedMessage
                        else state.review?.summary?.takeIf { it.isNotBlank() }
                    header?.let {
                        item(key = "summary") {
                            Text(
                                it,
                                style =
                                    if (saved) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (!saved && state.review?.stoppedAtLimit == true) {
                        item(key = "turn_limit") {
                            Text(
                                stringResource(R.string.core_ai_limit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (state.items.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.ai_review_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    reviewItems(
                        proposals =
                            if (saved)
                                state.items.filter { state.decisions[it.index]?.keep == true }
                            else state.items,
                        state = state,
                        onOpen =
                            if (saved || state.saving) null
                            else { index -> opened = index.toString() },
                    )
                    if (state.finishedAt != null)
                        aiRunLog(
                            state,
                            expanded = runLogOpen,
                            onToggle = { runLogOpen = !runLogOpen },
                        )
                }
            }
        }
    }

    opened?.let { index ->
        state.items
            .find { it.index.toString() == index }
            ?.let { proposal ->
                ReviewDetailSheet(
                    proposal = proposal,
                    state = state,
                    onEdit = { viewModel.edit(proposal.index, it) },
                    onIncludedChange = { viewModel.keep(proposal.index, it) },
                    onDismiss = { opened = null },
                )
            }
    }
    state.passwordFile?.let { file ->
        PasswordDialog(name = file.name, onResult = viewModel::password)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiBottomBar(
    state: AiAssistantUiState,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onStartOver: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (val phase = state.phase) {
                AiPhase.COMPOSE ->
                    Button(
                        onClick = onAnalyze,
                        shapes = ButtonDefaults.shapes(),
                        enabled =
                            !state.loading &&
                                !state.configBusy &&
                                !state.fileBusy &&
                                !state.saving &&
                                (state.settings?.configured == true && !state.configDirty) &&
                                (state.files.isNotEmpty() || state.request.isNotBlank()),
                    ) {
                        Text(stringResource(R.string.ai_analyze))
                    }
                AiPhase.PREPARING,
                AiPhase.RUNNING ->
                    OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.ai_cancel))
                    }
                AiPhase.REVIEW -> {
                    TextButton(
                        enabled = !state.saving,
                        onClick = onStartOver,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.ai_start_over))
                    }
                    val count = state.decisions.values.count { it.keep }
                    Button(
                        onClick = onSave,
                        enabled = count > 0 && !state.saving,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.ai_save_selected, count))
                    }
                }
                AiPhase.SAVED ->
                    Button(
                        enabled = !state.saving,
                        onClick = onDone,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.ai_done))
                    }
            }
        }
    }
}

/** The provider in one line once it is set up; the form until then, or when changing it. */
@Composable
private fun ProviderCard(
    config: AiConfigDraft,
    models: List<AiModel>,
    modelLoading: Boolean,
    modelError: String?,
    configured: Boolean,
    busy: Boolean,
    onLoadModels: (AiConfigDraft) -> Unit,
    onSave: (AiConfigDraft) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Card(
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.ai_provider),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text =
                            if (configured) "${config.model} · ${host(config.baseUrl)}"
                            else stringResource(R.string.ai_not_configured),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (configured && !editing) {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { editing = true }) {
                        Text(stringResource(R.string.ai_edit))
                    }
                }
            }
            if (!configured || editing) {
                ProviderForm(
                    config,
                    models,
                    modelLoading,
                    modelError,
                    configured,
                    busy,
                    onLoadModels,
                ) {
                    onSave(it)
                    editing = false
                }
            }
        }
    }
}

/** Shortcuts for the provider form: each fills the protocol and address. */
private enum class AiPreset(val labelRes: Int, val protocol: AiProtocol, val baseUrl: String) {
    CLAUDE(R.string.ai_preset_claude, AiProtocol.CLAUDE, "https://api.anthropic.com"),
    // One key for Claude, Gemini, GPT, DeepSeek, Qwen…
    OPENROUTER(
        R.string.ai_preset_openrouter,
        AiProtocol.OPEN_AI_COMPATIBLE,
        "https://openrouter.ai/api/v1",
    ),
    CUSTOM(R.string.ai_preset_custom, AiProtocol.OPEN_AI_COMPATIBLE, "");

    companion object {
        fun of(config: AiConfigDraft): AiPreset =
            when {
                config.protocol == AiProtocol.CLAUDE -> CLAUDE
                host(config.baseUrl) == "openrouter.ai" -> OPENROUTER
                else -> CUSTOM
            }
    }
}

/**
 * Provider, key, then model: once the key is in, the provider's models load and the picker opens,
 * so the model is chosen from what the key can actually use.
 */
@Composable
private fun ProviderForm(
    config: AiConfigDraft,
    models: List<AiModel>,
    modelLoading: Boolean,
    modelError: String?,
    configured: Boolean,
    busy: Boolean,
    onLoadModels: (AiConfigDraft) -> Unit,
    onSave: (AiConfigDraft) -> Unit,
) {
    var preset by rememberSaveable { mutableStateOf(AiPreset.of(config)) }
    var baseUrl by rememberSaveable { mutableStateOf(config.baseUrl) }
    var model by rememberSaveable { mutableStateOf(config.model) }
    // Not saveable: the key must not land in the saved-state bundle
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var picking by remember { mutableStateOf(false) }
    val ready = (apiKey.isNotBlank() || configured) && baseUrl.isNotBlank()

    // List what the key can use once typing pauses; open the picker if nothing usable is chosen
    LaunchedEffect(preset, baseUrl, apiKey) {
        if (!ready) return@LaunchedEffect
        delay(600)
        onLoadModels(AiConfigDraft(preset.protocol, baseUrl.trim().trimEnd('/'), "", apiKey.trim()))
    }
    // On first setup, or when the chosen model is not on the list, open the picker once per list
    var offered by rememberSaveable { mutableStateOf(configured) }
    LaunchedEffect(models) {
        if (models.isEmpty()) return@LaunchedEffect
        if (!offered || models.none { it.id == model }) picking = true
        offered = true
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        AiPreset.entries.forEachIndexed { index, p ->
            SegmentedButton(
                selected = preset == p,
                onClick = {
                    if (preset != p) {
                        preset = p
                        baseUrl = p.baseUrl
                        model = ""
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index, AiPreset.entries.size),
                // No check mark: the filled segment already shows the choice
                icon = {},
            ) {
                Text(stringResource(p.labelRes), maxLines = 1)
            }
        }
    }
    // Claude keeps its address editable for a proxy or relay
    if (preset != AiPreset.OPENROUTER) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            singleLine = true,
            label = { Text(stringResource(R.string.ai_base_url)) },
            placeholder = {
                Text(
                    if (preset == AiPreset.CLAUDE) "https://api.anthropic.com"
                    else "https://api.deepseek.com/v1"
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        singleLine = true,
        label = { Text(stringResource(R.string.ai_api_key)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    if (ready) {
        ModelField(
            model = model,
            models = models,
            loading = modelLoading,
            error = modelError,
            onClick = { picking = true },
            onRetry = {
                onLoadModels(
                    AiConfigDraft(preset.protocol, baseUrl.trim().trimEnd('/'), "", apiKey.trim())
                )
            },
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
            shapes = ButtonDefaults.shapes(),
            onClick = { onSave(AiConfigDraft(preset.protocol, baseUrl, model, apiKey)) },
            enabled = !busy && ready && model.isNotBlank(),
        ) {
            Text(stringResource(R.string.ai_save))
        }
    }

    if (picking) {
        ModelPicker(
            models = models,
            selected = model,
            onPick = {
                model = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** The chosen model as a row that opens the picker, with loading and error states. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelField(
    model: String,
    models: List<AiModel>,
    loading: Boolean,
    error: String?,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val chosen = models.firstOrNull { it.id == model }
    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ai_model),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        when {
                            loading -> stringResource(R.string.ai_models_loading)
                            chosen != null -> chosen.displayName
                            model.isNotBlank() -> model
                            else -> stringResource(R.string.ai_choose_model)
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail =
                    when {
                        error != null -> error
                        chosen != null && chosen.displayName != chosen.id -> chosen.id
                        models.isNotEmpty() && chosen == null ->
                            stringResource(R.string.ai_models_count, models.size)
                        else -> null
                    }
                detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                loading -> LoadingIndicator(modifier = Modifier.size(32.dp))
                error != null ->
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = onRetry) {
                        Text(stringResource(R.string.ai_retry))
                    }
                else -> Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
        }
    }
}

/** The provider's models, searchable by name or id; those that read images are marked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(
    models: List<AiModel>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown =
        remember(models, query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) models
            else models.filter { q in it.displayName.lowercase() || q in it.id.lowercase() }
        }
    HootbookModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.ai_search_models)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.content),
        )
        LazyColumn(contentPadding = PaddingValues(vertical = Spacing.sm)) {
            // An id the list does not have (a provider without a model list, a brand-new model)
            val typed = query.trim()
            if (typed.isNotEmpty() && models.none { it.id == typed }) {
                item(key = "typed") {
                    Text(
                        text = stringResource(R.string.ai_use_model_id, typed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { onPick(typed) }
                                .padding(
                                    horizontal = Dimensions.Padding.content,
                                    vertical = Spacing.md,
                                ),
                    )
                }
            }
            items(shown, key = { it.id }) { m ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onPick(m.id) }
                            .padding(
                                horizontal = Dimensions.Padding.content,
                                vertical = Spacing.sm,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            m.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight =
                                if (m.id == selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            m.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (m.readsImages) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = stringResource(R.string.ai_reads_images),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilesSection(
    names: List<Pair<String, Boolean>>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ai_files),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onAdd) {
                Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.ai_add_files))
            }
        }
        if (names.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                names.forEachIndexed { index, (name, isImage) ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(index) },
                        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(
                                if (isImage) Icons.Rounded.Image else Icons.Rounded.Description,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.heightIn(min = 32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.md),
            )
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = null)
            }
        }
    }
}

@Composable
private fun PasswordDialog(name: String, onResult: (String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(stringResource(R.string.ai_pdf_password_title, name)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text(stringResource(R.string.ai_pdf_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            DialogConfirmButton(
                stringResource(R.string.ai_unlock),
                onClick = { onResult(password) },
                enabled = password.isNotEmpty(),
            )
        },
        dismissButton = {
            DialogDismissButton(stringResource(R.string.ai_cancel), onClick = { onResult(null) })
        },
    )
}

private fun host(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
