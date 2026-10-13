package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.ai.*
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update

enum class AiPhase {
    COMPOSE,
    PREPARING,
    RUNNING,
    REVIEW,
    SAVED,
}

data class AiAssistantUiState(
    val loading: Boolean = true,
    val settings: AiSettings? = null,
    val config: AiConfigDraft = AiConfigDraft(),
    val configDirty: Boolean = false,
    val configBusy: Boolean = false,
    val modelLoading: Boolean = false,
    val models: List<AiModel> = emptyList(),
    val modelSearch: String = "",
    val modelError: String? = null,
    val request: String = "",
    val files: List<CoreAiInboxFile> = emptyList(),
    val fileBusy: Boolean = false,
    val phase: AiPhase = AiPhase.COMPOSE,
    val steps: List<AiStep> = emptyList(),
    val review: AiReview? = null,
    val items: List<AiProposal> = emptyList(),
    val decisions: Map<UInt, AiDecision> = emptyMap(),
    val duplicates: Map<String, Transaction> = emptyMap(),
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
    val preparedImages: Int? = null,
    val applied: AiApplied? = null,
    val saving: Boolean = false,
    val passwordFile: CoreAiInboxFile? = null,
    val accounts: Map<String, Account> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val subcategories: Map<String, Subcategory> = emptyMap(),
    val error: String? = null,
    val navigationBlocked: Boolean = false,
)

/** AI business is exclusively generated ai()/AiSession; this owns only UI and file preparation. */
@HiltViewModel
class AiAssistantViewModel
private constructor(
    private val core: CoreSession?,
    private val changes: ChangeBus,
    private val inbox: CoreAiInboxPort,
    private val prepare: suspend (List<CoreAiInboxFile>, Map<String, String>) -> List<AiAttachment>,
    private val testAi: (suspend () -> AiInterface)?,
    private val testLookups: (suspend () -> Pair<List<Account>, List<Category>>)?,
    private val nativeDispatcher: CoroutineDispatcher,
    private val testDuplicates: (suspend (AiReview) -> Map<String, Transaction>)?,
    private val invalidMessage: () -> String,
) : ViewModel() {
    @Inject
    constructor(
        core: CoreSession,
        changes: ChangeBus,
        inbox: CoreAiInbox,
        reader: CoreAiAttachmentReader,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    ) : this(
        core,
        changes,
        inbox,
        { files, passwords -> reader.prepare(files, inbox, passwords) },
        null,
        null,
        CoreAiNativeDispatcher,
        null,
        { context.getString(io.github.hootkit_dev.hootbook.R.string.onboarding_operation_failed) },
    )

    internal constructor(
        changes: ChangeBus,
        inbox: CoreAiInboxPort,
        prepare: suspend (List<CoreAiInboxFile>, Map<String, String>) -> List<AiAttachment> =
            { _, _ ->
                emptyList()
            },
        lookups: suspend () -> Pair<List<Account>, List<Category>> = {
            emptyList<Account>() to emptyList()
        },
        nativeDispatcher: CoroutineDispatcher = CoreAiNativeDispatcher,
        duplicates: suspend (AiReview) -> Map<String, Transaction> = { emptyMap() },
        ai: suspend () -> AiInterface,
    ) : this(null, changes, inbox, prepare, ai, lookups, nativeDispatcher, duplicates, { "Something went wrong. Please try again." })

    private fun failureText(error: Throwable): String =
        if (error is CoreException.Invalid) invalidMessage() else aiFailureText(error)

    private val mutable = MutableStateFlow(AiAssistantUiState())
    val state = mutable.asStateFlow()
    private var query: Job? = null
    private var queryRevision = 0L
    private var modelsJob: Job? = null
    private var modelsRevision = 0L
    private var runJob: Job? = null
    private val runRevision = AtomicLong()
    private val sessionLock = Any()
    private var activeSession: AiSession? = null
    // Cleanup must survive ViewModelStore.clear(), which cancels viewModelScope first.
    private val sessionCleanup = CoroutineScope(SupervisorJob() + nativeDispatcher)
    private var cleared = false
    private val passwords = mutableMapOf<String, String>()

    private suspend fun <T> withAi(block: suspend (AiInterface) -> T): T =
        withContext(nativeDispatcher) {
            val handle = testAi?.invoke() ?: core!!.awaitCore().ai()
            if (handle is Disposable) handle.use { block(handle) } else block(handle)
        }

    init {
        viewModelScope.launch {
            try {
                inbox.reload()
            } catch (_: Throwable) {
                mutable.update { it.copy(error = "Inbox storage could not be read.") }
            }
            inbox.files.collect { files -> mutable.update { it.copy(files = files) } }
        }
        refresh()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun refresh() {
        if (state.value.configBusy) return
        val request = ++queryRevision
        query?.cancel()
        query =
            viewModelScope.launch {
                val tables =
                    setOf(Table.SETTINGS, Table.ACCOUNTS, Table.CATEGORIES, Table.SUBCATEGORIES)
                val snapshots =
                    if (core != null)
                        core.observe(changes, tables) { root ->
                            coreQuery {
                                val settings = root.ai().use { it.settings() }
                                val accounts = root.accounts().use { it.overview().accounts }
                                root.categories().use { categories ->
                                    val all = categories.list(null, true)
                                    AiAssistantUiState(
                                        settings = settings,
                                        accounts = accounts.associateBy { it.id },
                                        categories = all.associateBy { it.id },
                                        subcategories =
                                            all.flatMap { it.subcategories }.associateBy { it.id },
                                    )
                                }
                            }
                        }
                    else
                        changes.observe(tables).mapLatest {
                            coreQuery {
                                val settings = withAi { it.settings() }
                                val refs = testLookups!!.invoke()
                                AiAssistantUiState(
                                    settings = settings,
                                    accounts = refs.first.associateBy { it.id },
                                    categories = refs.second.associateBy { it.id },
                                )
                            }
                        }
                snapshots.collect { result ->
                    if (request == queryRevision)
                        result.fold(
                            onSuccess = { loaded ->
                                val config = loaded.settings!!
                                mutable.update {
                                    it.copy(
                                        loading = false,
                                        settings = config,
                                        config =
                                            if (it.configDirty) it.config
                                            else
                                                AiConfigDraft(
                                                    config.protocol,
                                                    config.baseUrl,
                                                    config.model,
                                                ),
                                        accounts = loaded.accounts,
                                        categories = loaded.categories,
                                        subcategories = loaded.subcategories,
                                    )
                                }
                            },
                            onFailure = { error ->
                                mutable.update {
                                    it.copy(loading = false, error = failureText(error))
                                }
                            },
                        )
                }
            }
    }

    fun configChanged(change: (AiConfigDraft) -> AiConfigDraft) {
        if (
            state.value.configBusy ||
                state.value.phase in setOf(AiPhase.PREPARING, AiPhase.RUNNING) ||
                state.value.saving
        )
            return
        ++modelsRevision
        modelsJob?.cancel()
        mutable.update {
            it.copy(
                config = change(it.config),
                configDirty = true,
                modelLoading = false,
                models = emptyList(),
                modelError = null,
            )
        }
    }

    fun protocol(protocol: AiProtocol) {
        if (state.value.configBusy || state.value.saving || state.value.phase != AiPhase.COMPOSE)
            return
        val revision = ++modelsRevision
        modelsJob?.cancel()
        viewModelScope.launch {
            try {
                val defaults = withAi { it.protocolDefaults(protocol) }
                if (
                    revision != modelsRevision ||
                        state.value.phase != AiPhase.COMPOSE ||
                        state.value.configBusy
                )
                    return@launch
                configChanged {
                    it.copy(protocol = protocol, baseUrl = defaults.baseUrl, model = defaults.model)
                }
            } catch (error: Throwable) {
                mutable.update { it.copy(error = failureText(error)) }
            }
        }
    }

    fun saveConfig() {
        val s = state.value
        if (s.configBusy || s.saving || s.loading || s.phase != AiPhase.COMPOSE) return
        ++queryRevision
        query?.cancel()
        ++modelsRevision
        modelsJob?.cancel()
        val draft = s.config
        mutable.update { it.copy(configBusy = true, error = null, modelLoading = false) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    withAi { ai ->
                        // Separate Core commands; if settings succeeds and key fails, the error is
                        // explicit.
                        ai.setSettings(draft.protocol, draft.baseUrl, draft.model)
                        if (draft.removeKey || draft.apiKey.isNotBlank())
                            ai.setApiKey(if (draft.removeKey) null else draft.apiKey)
                        val actual = ai.settings()
                        mutable.update {
                            it.copy(
                                settings = actual,
                                config =
                                    AiConfigDraft(actual.protocol, actual.baseUrl, actual.model),
                                configDirty = false,
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                mutable.update {
                    it.copy(error = failureText(error), config = it.config.copy(apiKey = ""))
                }
            } finally {
                mutable.update { it.copy(configBusy = false, navigationBlocked = false) }
                if (!cleared) refresh()
            }
        }
    }

    fun loadModels() {
        if (state.value.configBusy || state.value.saving || state.value.phase != AiPhase.COMPOSE)
            return
        val revision = ++modelsRevision
        modelsJob?.cancel()
        val draft = state.value.config
        mutable.update { it.copy(modelLoading = true, error = null, modelError = null) }
        modelsJob =
            viewModelScope.launch {
                try {
                    val models = withAi {
                        it.listModels(
                            draft.protocol,
                            draft.baseUrl,
                            draft.apiKey.takeIf(String::isNotBlank),
                        )
                    }
                    if (revision == modelsRevision)
                        mutable.update { it.copy(models = models, modelLoading = false) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (revision == modelsRevision)
                        mutable.update {
                            it.copy(
                                modelLoading = false,
                                error = failureText(error),
                                modelError = failureText(error),
                            )
                        }
                }
            }
    }

    fun modelSearch(value: String) {
        mutable.update { it.copy(modelSearch = value) }
    }

    fun selectModel(id: String) {
        if (state.value.configBusy || state.value.phase != AiPhase.COMPOSE) return
        ++modelsRevision
        modelsJob?.cancel()
        mutable.update {
            it.copy(config = it.config.copy(model = id), configDirty = true, modelLoading = false)
        }
    }

    fun request(value: String) {
        if (state.value.phase == AiPhase.COMPOSE && !state.value.saving)
            mutable.update { it.copy(request = value) }
    }

    fun addFiles(uris: List<Uri>) {
        val real = inbox as? CoreAiInbox ?: return
        filesCommand { real.copy(uris) }
    }

    fun addSharedText(text: String) {
        val real = inbox as? CoreAiInbox ?: return
        filesCommand { real.addSharedText(text) }
    }

    fun removeFile(id: String) {
        filesCommand {
            inbox.remove(id)
            passwords.remove(id)
        }
    }

    private fun filesCommand(finishOnClear: Boolean = false, block: suspend () -> Unit) {
        if (state.value.phase != AiPhase.COMPOSE || state.value.fileBusy || state.value.saving)
            return
        mutable.update { it.copy(fileBusy = true, error = null) }
        viewModelScope.launch(
            start = if (finishOnClear) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT
        ) {
            try {
                if (finishOnClear) withContext(NonCancellable + nativeDispatcher) { block() }
                else block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutable.update {
                    it.copy(
                        error =
                            "File preparation failed. Use a file up to 20 MB; CSV, PDF, or screenshots are supported."
                    )
                }
            } finally {
                mutable.update { it.copy(fileBusy = false) }
            }
        }
    }

    fun analyze() {
        val s = state.value
        if (
            s.phase != AiPhase.COMPOSE ||
                s.loading ||
                s.configBusy ||
                s.fileBusy ||
                s.saving ||
                s.configDirty ||
                (s.request.isBlank() && s.files.isEmpty())
        )
            return
        val revision = runRevision.incrementAndGet()
        mutable.update {
            it.copy(
                phase = AiPhase.PREPARING,
                error = null,
                passwordFile = null,
                steps = emptyList(),
                review = null,
                items = emptyList(),
                decisions = emptyMap(),
                duplicates = emptyMap(),
                applied = null,
                startedAt = SystemClock.elapsedRealtime(),
                finishedAt = null,
                preparedImages = null,
            )
        }
        val passwordSnapshot = passwords.toMap()
        runJob =
            viewModelScope.launch {
                var native: AiSession? = null
                var handedToReview = false
                try {
                    val attachments = prepare(s.files, passwordSnapshot)
                    if (revision != runRevision.get()) return@launch
                    mutable.update {
                        it.copy(
                            preparedImages =
                                attachments.count { a -> a.kind == AiAttachmentKind.IMAGE }
                        )
                    }
                    val listener =
                        object : AiStepListener {
                            override fun onStep(step: AiStep) {
                                try {
                                    mutable.update { current ->
                                        if (
                                            revision == runRevision.get() &&
                                                current.phase == AiPhase.RUNNING
                                        )
                                            current.copy(steps = current.steps + step)
                                        else current
                                    }
                                } catch (_: Throwable) {
                                    /* Foreign callback must never unwind into Rust. */
                                }
                            }
                        }
                    mutable.update { it.copy(phase = AiPhase.RUNNING) }
                    val (review, steps) =
                        withContext(nativeDispatcher) {
                            // Assign inside the dispatched block: cancellation at its return
                            // boundary
                            // must not lose a newly allocated native pointer.
                            val running = withAi {
                                it.start(AiRequest(s.request, attachments), listener).also {
                                    allocated ->
                                    native = allocated
                                }
                            }
                            ensureActive()
                            val result = running.run()
                            result to running.steps()
                        }
                    val duplicates =
                        testDuplicates?.invoke(review)
                            ?: core!!.withCore { root ->
                                root.ledger().use { ledger ->
                                    val records = linkedMapOf<String, Transaction>()
                                    review.changes
                                        .mapNotNull {
                                            (it.change as? AiChange.AddTransaction)
                                                ?.possibleDuplicate
                                        }
                                        .distinct()
                                        .forEach { id ->
                                            try {
                                                records[id] = ledger.get(id)
                                            } catch (_: CoreException.NotFound) {}
                                        }
                                    records
                                }
                            }
                    if (revision != runRevision.get() || cleared) return@launch
                    // Running pointers belong only to this coroutine. Main hands ownership
                    // to review after run has fully unwound; no native call occurs under this lock.
                    synchronized(sessionLock) { activeSession = native }
                    handedToReview = true
                    mutable.update {
                        it.copy(
                            phase = AiPhase.REVIEW,
                            review = review,
                            duplicates = duplicates,
                            items = review.changes,
                            decisions =
                                review.changes.associate { proposal ->
                                    proposal.index to
                                        AiDecision(
                                            proposal.index,
                                            (proposal.change as? AiChange.AddTransaction)
                                                ?.possibleDuplicate == null,
                                            null,
                                        )
                                },
                            steps = steps,
                            finishedAt = SystemClock.elapsedRealtime(),
                        )
                    }
                } catch (password: CoreAiPdfPasswordRequired) {
                    if (revision == runRevision.get())
                        mutable.update {
                            it.copy(
                                phase = AiPhase.COMPOSE,
                                passwordFile =
                                    s.files.firstOrNull { file -> file.id == password.fileId },
                            )
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (revision == runRevision.get())
                        mutable.update { current ->
                            current.copy(
                                phase = AiPhase.COMPOSE,
                                error =
                                    if (current.phase == AiPhase.PREPARING)
                                        "File preparation failed. Check file size (20 MB), PDF password, image readability, and long screenshots (up to 12 tiles). No request was sent."
                                    else failureText(error),
                            )
                        }
                } finally {
                    if (!handedToReview) native?.let { releaseNative(it) }
                }
            }
    }

    fun password(value: String?) {
        val file = state.value.passwordFile ?: return
        mutable.update { it.copy(passwordFile = null) }
        if (value != null) {
            passwords[file.id] = value
            analyze()
        }
    }

    fun cancel() {
        if (state.value.phase !in setOf(AiPhase.PREPARING, AiPhase.RUNNING)) return
        runRevision.incrementAndGet()
        // Cancel the Kotlin continuation first. Its UniFFI finally frees the run
        // future off Main before our finally requests native cancel and releases it.
        runJob?.cancel()
        mutable.update {
            it.copy(
                phase = AiPhase.COMPOSE,
                steps = emptyList(),
                items = emptyList(),
                review = null,
                error = null,
            )
        }
    }

    fun keep(index: UInt, keep: Boolean) {
        if (state.value.saving || state.value.phase != AiPhase.REVIEW) return
        mutable.update { value ->
            value.decisions[index]?.let { decision ->
                value.copy(decisions = value.decisions + (index to decision.copy(keep = keep)))
            } ?: value
        }
    }

    fun edit(index: UInt, form: AiDraftForm): Boolean {
        if (
            state.value.saving ||
                state.value.phase != AiPhase.REVIEW ||
                state.value.items.none { it.index == index && it.change is AiChange.AddTransaction }
        )
            return false
        return try {
            val draft = form.toDraft()
            mutable.update { value ->
                value.copy(
                    decisions =
                        value.decisions +
                            (index to value.decisions.getValue(index).copy(edited = draft))
                )
            }
            true
        } catch (_: Exception) {
            mutable.update {
                it.copy(error = "Check amount, date/time, and transfer amount input.")
            }
            false
        }
    }

    fun apply() {
        val s = state.value
        if (s.phase != AiPhase.REVIEW || s.saving) return
        val native = synchronized(sessionLock) { activeSession } ?: return
        val decisions = s.items.map { s.decisions.getValue(it.index) }
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable + nativeDispatcher) {
                    val applied = native.apply(decisions)
                    mutable.update { it.copy(phase = AiPhase.SAVED, applied = applied) }
                }
            } catch (error: Throwable) {
                mutable.update { it.copy(error = failureText(error)) }
            } finally {
                mutable.update { it.copy(saving = false, navigationBlocked = false) }
                if (cleared) releaseSession()
            }
        }
    }

    fun undo() {
        val s = state.value
        if (s.phase != AiPhase.SAVED || s.saving) return
        val token = s.applied?.undoToken ?: return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable + nativeDispatcher) {
                    withAi { it.undo(token) }
                    mutable.update { it.copy(phase = AiPhase.REVIEW, applied = null) }
                }
            } catch (error: Throwable) {
                mutable.update { it.copy(error = failureText(error)) }
            } finally {
                mutable.update { it.copy(saving = false, navigationBlocked = false) }
                if (cleared) releaseSession()
            }
        }
    }

    fun restart() {
        if (state.value.saving || state.value.configBusy || state.value.fileBusy) return
        cancel()
        releaseSession()
        passwords.clear()
        mutable.update {
            it.copy(
                phase = AiPhase.COMPOSE,
                request = "",
                review = null,
                items = emptyList(),
                decisions = emptyMap(),
                duplicates = emptyMap(),
                steps = emptyList(),
                applied = null,
                passwordFile = null,
                error = null,
            )
        }
        filesCommand(finishOnClear = true) { inbox.clear() }
    }

    fun clearError() {
        mutable.update { it.copy(error = null) }
    }

    fun back(): Boolean {
        if (state.value.saving || state.value.configBusy || state.value.fileBusy) {
            mutable.update { it.copy(navigationBlocked = true) }
            return true
        }
        if (state.value.phase in setOf(AiPhase.PREPARING, AiPhase.RUNNING)) {
            cancel()
            return true
        }
        return false
    }

    private suspend fun releaseNative(native: AiSession) =
        withContext(NonCancellable + nativeDispatcher) {
            try {
                native.cancel()
            } finally {
                native.destroy()
            }
        }

    private fun releaseSession() {
        val native =
            synchronized(sessionLock) { activeSession.also { activeSession = null } } ?: return
        sessionCleanup.launch { releaseNative(native) }
    }

    override fun onCleared() {
        cleared = true
        runRevision.incrementAndGet()
        modelsRevision++
        queryRevision++
        runJob?.cancel()
        passwords.clear()
        if (!state.value.saving) releaseSession()
        mutable.update { it.copy(config = it.config.copy(apiKey = "")) }
        super.onCleared()
    }
}
