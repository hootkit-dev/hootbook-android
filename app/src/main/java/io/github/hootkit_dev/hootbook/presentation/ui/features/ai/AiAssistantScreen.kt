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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.hootkit_dev.hootbook.presentation.ui.components.hootbookTextFieldColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.input.ImeAction
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
        else if (unsavedReview) confirmDiscard = { viewModel.restart(); onNavigateBack() }
        else onNavigateBack()
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
                    state.pendingReview?.let { pending ->
                        item(key = "pending_review") {
                            PendingReviewCard(
                                pending = pending,
                                busy = state.saving,
                                onResume = viewModel::resumePendingReview,
                                onDiscard = viewModel::discardPendingReview,
                            )
                        }
                    }
                    item(key = "provider") {
                        ProviderCard(
                            state.config,
                            state.models,
                            state.modelLoading,
                            state.modelError,
                            state.reasoningLevels,
                            state.settings?.configured == true,
                            state.settings?.hasKey == true,
                            state.syncActive,
                            busy = state.configBusy,
                            onClearModels = viewModel::clearModels,
                            onLoadModels = viewModel::loadModels,
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
                        TextField(
                            value = state.request,
                            onValueChange = viewModel::request,
                            placeholder = { Text(stringResource(R.string.ai_request_hint)) },
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = hootbookTextFieldColors(),
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

@Composable
private fun PendingReviewCard(
    pending: AiPendingReview,
    busy: Boolean,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm + Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.core_ai_pending_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            val createdAtText =
                "%04d-%02d-%02d %02d:%02d".format(
                    pending.createdAt.year,
                    pending.createdAt.month.toInt(),
                    pending.createdAt.day.toInt(),
                    pending.createdAt.hour.toInt(),
                    pending.createdAt.minute.toInt(),
                )
            Text(
                stringResource(
                    R.string.core_ai_pending_body,
                    createdAtText,
                    pending.review.summary ?: "${pending.review.changes.size}",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                TextButton(
                    onClick = onDiscard,
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.core_ai_pending_discard))
                }
                Button(
                    onClick = onResume,
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.core_ai_pending_resume))
                }
            }
        }
    }
}

/** The provider in one line once it is set up; the form until then, or when changing it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProviderCard(
    config: AiConfigDraft,
    models: List<AiModel>,
    modelLoading: Boolean,
    modelError: String?,
    reasoningLevels: List<AiReasoning>,
    configured: Boolean,
    hasKey: Boolean,
    syncActive: Boolean,
    busy: Boolean,
    onClearModels: () -> Unit,
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.ai_provider),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text =
                            if (configured || config.model.isNotBlank())
                                "${config.model} · ${host(config.baseUrl)}"
                            else stringResource(R.string.ai_not_configured),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (hasKey) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.core_ai_key_saved_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            if (syncActive) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.core_ai_key_synced_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (configured && !editing) {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { editing = true }) {
                        Text(stringResource(R.string.ai_edit))
                    }
                }
            }
            if (config.model.isNotBlank() && !hasKey) {
                Text(
                    text = stringResource(R.string.core_ai_synced_missing_key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (!configured || editing) {
                ProviderForm(
                    config = config,
                    models = models,
                    modelLoading = modelLoading,
                    modelError = modelError,
                    reasoningLevels = reasoningLevels,
                    configured = configured,
                    hasKey = hasKey,
                    syncActive = syncActive,
                    busy = busy,
                    onClearModels = onClearModels,
                    onLoadModels = onLoadModels,
                    onCancel =
                        if (configured) {
                            {
                                editing = false
                                onClearModels()
                            }
                        } else null,
                ) {
                    onSave(it)
                    editing = false
                }
            }
        }
    }
}

/** Shortcuts for the provider form: each fills the protocol and address. */
private enum class AiPreset(val labelRes: Int, val protocol: AiApiProtocol, val baseUrl: String) {
    CLAUDE(R.string.ai_preset_claude, AiApiProtocol.CLAUDE, "https://api.anthropic.com"),
    // One key for Claude, Gemini, GPT, DeepSeek, Qwen…
    OPENROUTER(
        R.string.ai_preset_openrouter,
        AiApiProtocol.OPEN_AI_COMPATIBLE,
        "https://openrouter.ai/api/v1",
    ),
    CUSTOM(R.string.ai_preset_custom, AiApiProtocol.OPEN_AI_COMPATIBLE, "");

    companion object {
        fun of(config: AiConfigDraft): AiPreset =
            when {
                config.protocol == AiApiProtocol.CLAUDE -> CLAUDE
                host(config.baseUrl) == "openrouter.ai" -> OPENROUTER
                else -> CUSTOM
            }
    }
}

private enum class AiCustomEndpoint(val labelRes: Int, val baseUrl: String) {
    DEEPSEEK(R.string.core_ai_endpoint_deepseek, "https://api.deepseek.com/v1"),
    OPENAI(R.string.core_ai_endpoint_openai, "https://api.openai.com/v1"),
    GEMINI(
        R.string.core_ai_endpoint_gemini,
        "https://generativelanguage.googleapis.com/v1beta/openai",
    ),
    QWEN(R.string.core_ai_endpoint_qwen, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    SILICONFLOW(R.string.core_ai_endpoint_siliconflow, "https://api.siliconflow.cn/v1"),
    KIMI(R.string.core_ai_endpoint_kimi, "https://api.moonshot.cn/v1"),
    GLM(R.string.core_ai_endpoint_glm, "https://open.bigmodel.cn/api/paas/v4"),
    VOLCENGINE(R.string.core_ai_endpoint_volcengine, "https://ark.cn-beijing.volces.com/api/v3"),
    XAI(R.string.core_ai_endpoint_xai, "https://api.x.ai/v1"),
    GROQ(R.string.core_ai_endpoint_groq, "https://api.groq.com/openai/v1"),
}

/**
 * Provider, key, then model: once the key and address are ready, the provider's models load in the
 * background, and the picker also allows entering any model ID directly.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProviderForm(
    config: AiConfigDraft,
    models: List<AiModel>,
    modelLoading: Boolean,
    modelError: String?,
    reasoningLevels: List<AiReasoning>,
    configured: Boolean,
    hasKey: Boolean,
    syncActive: Boolean,
    busy: Boolean,
    onClearModels: () -> Unit,
    onLoadModels: (AiConfigDraft) -> Unit,
    onCancel: (() -> Unit)? = null,
    onSave: (AiConfigDraft) -> Unit,
) {
    val savedPreset = remember(config.protocol, config.baseUrl) { AiPreset.of(config) }
    val savedHost = remember(config.baseUrl) { host(cleanBaseUrl(config.baseUrl)) }
    var preset by
        rememberSaveable(config.protocol, config.baseUrl) { mutableStateOf(AiPreset.of(config)) }
    var baseUrl by
        rememberSaveable(config.protocol, config.baseUrl) { mutableStateOf(config.baseUrl) }
    var model by
        rememberSaveable(config.protocol, config.baseUrl, config.model) {
            mutableStateOf(config.model)
        }
    var reasoning by
        rememberSaveable(config.protocol, config.baseUrl, config.model, config.reasoning) {
            mutableStateOf(config.reasoning)
        }
    // Not saveable: the key must not land in the saved-state bundle
    var apiKey by remember(config.protocol, config.baseUrl) { mutableStateOf(config.apiKey) }
    var editingKey by
        remember(hasKey, config.protocol, config.baseUrl) { mutableStateOf(!hasKey) }
    var picking by remember { mutableStateOf(false) }

    val cleanedUrl = cleanBaseUrl(baseUrl)
    val currentHost = host(cleanedUrl)
    val matchesSavedProvider =
        hasKey &&
            preset == savedPreset &&
            currentHost.isNotEmpty() &&
            currentHost.equals(savedHost, ignoreCase = true)
    val usingSavedKey = hasKey && !editingKey && matchesSavedProvider
    val effectiveKey = if (usingSavedKey) "" else cleanApiKey(apiKey)
    val hasUsableKey = usingSavedKey || effectiveKey.isNotEmpty()
    val ready = hasUsableKey && isReadyHttpsUrl(cleanedUrl)

    // List what the key can use once typing pauses
    LaunchedEffect(preset, cleanedUrl, effectiveKey, usingSavedKey) {
        if (!ready) return@LaunchedEffect
        delay(800)
        onLoadModels(
            AiConfigDraft(
                preset.protocol,
                cleanedUrl,
                "",
                effectiveKey,
                reasoning = reasoning,
            )
        )
    }
    LaunchedEffect(models) {
        if (models.size == 1 && model.isBlank()) {
            model = models.first().id
        }
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        AiPreset.entries.forEachIndexed { index, p ->
            SegmentedButton(
                selected = preset == p,
                onClick = {
                    if (preset != p) {
                        preset = p
                        if (p == savedPreset && config.baseUrl.isNotBlank()) {
                            baseUrl = config.baseUrl
                            model = config.model
                            reasoning = config.reasoning
                            if (hasKey && apiKey.isBlank()) editingKey = false
                        } else {
                            baseUrl = p.baseUrl
                            model = ""
                            reasoning = AiReasoning.PROVIDER_DEFAULT
                            if (hasKey) editingKey = true
                        }
                        onClearModels()
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
    if (preset == AiPreset.CUSTOM) {
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(AiCustomEndpoint.entries) { endpoint ->
                val selected = cleanedUrl.equals(endpoint.baseUrl, ignoreCase = true)
                androidx.compose.material3.FilterChip(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            baseUrl = endpoint.baseUrl
                            val matchesSaved =
                                hasKey &&
                                    savedPreset == AiPreset.CUSTOM &&
                                    host(endpoint.baseUrl).equals(savedHost, ignoreCase = true)
                            if (matchesSaved && config.baseUrl.isNotBlank()) {
                                model = config.model
                                reasoning = config.reasoning
                                if (apiKey.isBlank()) editingKey = false
                            } else {
                                model = ""
                                reasoning = AiReasoning.PROVIDER_DEFAULT
                                if (hasKey) editingKey = true
                            }
                            onClearModels()
                        }
                    },
                    label = { Text(stringResource(endpoint.labelRes), maxLines = 1) },
                )
            }
        }
    }
    // Claude keeps its address editable for a proxy or relay
    if (preset != AiPreset.OPENROUTER) {
        TextField(
            value = baseUrl,
            onValueChange = {
                baseUrl = it
                if (
                    hasKey &&
                        !editingKey &&
                        !host(cleanBaseUrl(it)).equals(savedHost, ignoreCase = true)
                ) {
                    editingKey = true
                }
                onClearModels()
            },
            singleLine = true,
            label = { Text(stringResource(R.string.ai_base_url)) },
            placeholder = {
                Text(
                    if (preset == AiPreset.CLAUDE) "https://api.anthropic.com"
                    else "https://api.deepseek.com/v1"
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(12.dp),
            colors = hootbookTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (usingSavedKey) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = "••••••••••••••••",
                    onValueChange = {},
                    enabled = false,
                    singleLine = true,
                    label = { Text(stringResource(R.string.ai_api_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    colors = hootbookTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        editingKey = true
                        apiKey = ""
                        onClearModels()
                    },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.core_ai_key_modify))
                }
            }
            Text(
                text = stringResource(
                    if (syncActive) R.string.core_ai_key_masked_synced_hint
                    else R.string.core_ai_key_masked_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    onClearModels()
                },
                singleLine = true,
                label = { Text(stringResource(R.string.ai_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
                colors = hootbookTextFieldColors(),
                modifier = Modifier.weight(1f),
            )
            if (matchesSavedProvider) {
                TextButton(
                    onClick = {
                        editingKey = false
                        apiKey = ""
                        onClearModels()
                    },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.core_ai_key_cancel_modify))
                }
            }
        }
    }
    val retryLoadModels = {
        if (ready) {
            onLoadModels(
                AiConfigDraft(
                    preset.protocol,
                    cleanedUrl,
                    "",
                    effectiveKey,
                    reasoning = reasoning,
                )
            )
        }
    }
    ModelField(
        model = model,
        models = models,
        loading = modelLoading,
        error = modelError,
        enabled = !busy,
        onClick = {
            picking = true
            if (ready && models.isEmpty() && !modelLoading) {
                retryLoadModels()
            }
        },
        onRetry = retryLoadModels,
    )
    if (model.isNotBlank()) {
        val chosenModel = models.firstOrNull { it.id == model }
        val modelReasoning = chosenModel?.reasoning
        val endpointLevels =
            if (matchesSavedProvider && reasoningLevels.isNotEmpty()) {
                reasoningLevels
            } else {
                endpointReasoningLevels(preset.protocol, cleanedUrl)
            }
        val availableLevels =
            if (modelReasoning?.known == true) {
                if (modelReasoning.levels.isEmpty()) emptyList()
                else
                    listOf(AiReasoning.PROVIDER_DEFAULT) +
                        modelReasoning.levels.filter { it != AiReasoning.PROVIDER_DEFAULT }
            } else {
                listOf(AiReasoning.PROVIDER_DEFAULT) +
                    endpointLevels.filter { it != AiReasoning.PROVIDER_DEFAULT }
            }
        if (availableLevels.isNotEmpty()) {
            val effectiveReasoning =
                if (reasoning in availableLevels) reasoning else AiReasoning.PROVIDER_DEFAULT
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.core_ai_reasoning),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    availableLevels.forEachIndexed { index, level ->
                        SegmentedButton(
                            selected = effectiveReasoning == level,
                            onClick = { reasoning = level },
                            shape = SegmentedButtonDefaults.itemShape(index, availableLevels.size),
                            icon = {},
                        ) {
                            Text(
                                reasoningLabel(level),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (
                    modelReasoning?.known != true &&
                        effectiveReasoning != AiReasoning.PROVIDER_DEFAULT
                ) {
                    Text(
                        stringResource(R.string.core_ai_reasoning_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
    ) {
        if (onCancel != null) {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onCancel,
                enabled = !busy,
            ) {
                Text(stringResource(R.string.ai_cancel))
            }
        }
        Button(
            shapes = ButtonDefaults.shapes(),
            onClick = {
                onSave(
                    AiConfigDraft(
                        preset.protocol,
                        cleanedUrl,
                        model.trim(),
                        effectiveKey,
                        reasoning = reasoning,
                    )
                )
            },
            enabled = !busy && hasUsableKey && cleanedUrl.isNotBlank() && model.isNotBlank(),
        ) {
            Text(stringResource(R.string.ai_save))
        }
    }

    if (picking) {
        ModelPicker(
            models = models,
            selected = model,
            loading = modelLoading,
            error = modelError,
            onRetry = if (ready) retryLoadModels else null,
            onPick = {
                model = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun reasoningLabel(level: AiReasoning): String =
    when (level) {
        AiReasoning.PROVIDER_DEFAULT, AiReasoning.UNKNOWN -> stringResource(R.string.core_ai_reasoning_default)
        AiReasoning.OFF -> stringResource(R.string.core_ai_reasoning_none)
        AiReasoning.LOW -> stringResource(R.string.core_ai_reasoning_low)
        AiReasoning.MEDIUM -> stringResource(R.string.core_ai_reasoning_medium)
        AiReasoning.HIGH -> stringResource(R.string.core_ai_reasoning_high)
    }

/** The chosen model as a row that opens the picker, with loading and error states. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelField(
    model: String,
    models: List<AiModel>,
    loading: Boolean,
    error: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val chosen = models.firstOrNull { it.id == model }
    Surface(
        onClick = onClick,
        enabled = enabled,
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
                            chosen != null -> chosen.displayName
                            model.isNotBlank() -> model
                            loading -> stringResource(R.string.ai_models_loading)
                            else -> stringResource(R.string.ai_choose_model)
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail =
                    when {
                        error != null -> error
                        loading && model.isNotBlank() -> stringResource(R.string.ai_models_loading)
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelPicker(
    models: List<AiModel>,
    selected: String,
    loading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val typed = query.trim()
    val shown =
        remember(models, query) {
            val q = typed.lowercase()
            if (q.isEmpty()) models
            else models.filter { q in it.displayName.lowercase() || q in it.id.lowercase() }
        }
    HootbookModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.ai_search_models)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (typed.isNotEmpty()) {
                            onPick(typed)
                        }
                    }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = hootbookTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.content),
        )
        LazyColumn(contentPadding = PaddingValues(vertical = Spacing.sm)) {
            // An id the list does not have (a provider without a model list, a brand-new model)
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
            if (loading && models.isEmpty()) {
                item(key = "loading") {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    horizontal = Dimensions.Padding.content,
                                    vertical = Spacing.md,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            stringResource(R.string.ai_models_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (error != null && models.isEmpty()) {
                item(key = "error") {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(
                                    horizontal = Dimensions.Padding.content,
                                    vertical = Spacing.sm,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        if (onRetry != null) {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = onRetry) {
                                Text(stringResource(R.string.ai_retry))
                            }
                        }
                    }
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
            TextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text(stringResource(R.string.ai_pdf_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
                colors = hootbookTextFieldColors(),
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

private fun cleanBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    return if (
        trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

private fun cleanApiKey(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
        trimmed.substring(7).trim()
    } else {
        trimmed
    }
}

private fun isReadyHttpsUrl(url: String): Boolean =
    runCatching {
        val u = URI(url)
        val h = u.host ?: return false
        u.scheme.equals("https", ignoreCase = true) &&
            (h.equals("localhost", ignoreCase = true) ||
                ('.' in h && !h.startsWith('.') && !h.endsWith('.') && h.substringAfterLast('.').length >= 2))
    }.getOrDefault(false)

private fun endpointReasoningLevels(protocol: AiApiProtocol, baseUrl: String): List<AiReasoning> =
    if (
        protocol == AiApiProtocol.CLAUDE ||
            host(baseUrl).equals("openrouter.ai", ignoreCase = true)
    ) {
        listOf(AiReasoning.OFF, AiReasoning.LOW, AiReasoning.MEDIUM, AiReasoning.HIGH)
    } else {
        listOf(AiReasoning.LOW, AiReasoning.MEDIUM, AiReasoning.HIGH)
    }

private fun host(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
