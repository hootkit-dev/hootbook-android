package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import io.github.hootkit_dev.hootbook.data.ai.aiMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.ai.AiAttachment
import io.github.hootkit_dev.hootbook.data.ai.AiAttachmentReader
import io.github.hootkit_dev.hootbook.data.ai.AiConfig
import io.github.hootkit_dev.hootbook.data.ai.AiException
import io.github.hootkit_dev.hootbook.data.ai.AiChat
import io.github.hootkit_dev.hootbook.data.ai.AiLedgerSession
import io.github.hootkit_dev.hootbook.data.ai.AiModel
import io.github.hootkit_dev.hootbook.data.ai.AiPart
import io.github.hootkit_dev.hootbook.data.ai.AiStep
import io.github.hootkit_dev.hootbook.data.ai.AiSettings
import io.github.hootkit_dev.hootbook.data.ai.AppliedChanges
import io.github.hootkit_dev.hootbook.data.ai.LedgerChange
import io.github.hootkit_dev.hootbook.data.ai.LedgerTools
import io.github.hootkit_dev.hootbook.data.ai.PdfPasswordRequired
import io.github.hootkit_dev.hootbook.data.ai.TransactionDraft
import io.github.hootkit_dev.hootbook.presentation.common.TransactionLookups
import io.github.hootkit_dev.hootbook.presentation.common.TransactionLookupsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The files in the AI request. Shared files are copied in as they arrive, while the sharing
 * app's permission lasts, and stay until used or removed, so they survive the lock screen and
 * the screen being closed.
 */
@Singleton
class AiShareInbox @Inject constructor(
    private val reader: AiAttachmentReader,
    @ApplicationContext private val context: Context
) {
    private val _attachments = MutableStateFlow<List<AiAttachment>>(emptyList())
    val attachments: StateFlow<List<AiAttachment>> = _attachments.asStateFlow()

    // Why the last shared file could not be read, for the screen to show
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Copies [uris] in; a file that cannot be read is reported through [error]. */
    suspend fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        try {
            val copied = reader.copy(uris)
            _attachments.update { it + copied }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.aiMessage(context)
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun remove(attachment: AiAttachment) {
        _attachments.update { it - attachment }
    }

    fun clear() {
        _attachments.value = emptyList()
    }
}

/** The provider's models, loaded once a key is entered. */
data class ModelListState(
    val loading: Boolean = false,
    val models: List<AiModel> = emptyList(),
    val error: String? = null
)

/** A proposed change and whether it will be saved; the user can leave one out or edit it. */
data class ReviewItem(val change: LedgerChange, val included: Boolean)

sealed interface AiPhase {
    data object Compose : AiPhase
    /**
     * The model is at work. [images] is how many images the files became (long screenshots are cut
     * into tiles), null while they are still being read.
     */
    data class Running(
        val files: Int,
        val images: Int? = null,
        val steps: List<AiStep> = emptyList(),
        val startedAt: Long = SystemClock.elapsedRealtime(),
        // Set once the run is over, when it is kept for the review
        val finishedAt: Long? = null
    ) : AiPhase {
        val proposed get() = steps.sumOf { (it as? AiStep.Proposed)?.changes?.size ?: 0 }
    }
    data object Review : AiPhase
    data class Saved(val applied: AppliedChanges) : AiPhase
}

data class AiAssistantUiState(
    val config: AiConfig = AiConfig(),
    val attachments: List<AiAttachment> = emptyList(),
    val request: String = "",
    val phase: AiPhase = AiPhase.Compose,
    val review: List<ReviewItem> = emptyList(),
    val summary: String = "",
    // The run hit its turn limit, so the proposals may be incomplete
    val stoppedAtLimit: Boolean = false,
    val error: String? = null,
    // A PDF that needs its password before it can be read
    val passwordFor: String? = null,
    val models: ModelListState = ModelListState(),
    // The steps of the run that produced the review, kept so they can be read afterwards
    val lastRun: AiPhase.Running? = null,
    // Saving or undoing: Save and Undo wait, so a second tap cannot write everything twice
    val busy: Boolean = false
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val settings: AiSettings,
    private val reader: AiAttachmentReader,
    private val session: AiLedgerSession,
    private val tools: LedgerTools,
    private val inbox: AiShareInbox,
    private val chat: AiChat,
    lookupsSource: TransactionLookupsSource,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(AiAssistantUiState(config = settings.config.value))

    /** False when the keystore is unusable: the key then lasts only until the app closes. */
    val keyStoredSecurely: Boolean get() = settings.keyStoredSecurely
    val state: StateFlow<AiAssistantUiState> = _state.asStateFlow()

    // Categories and accounts, to show proposed transactions the way the lists do
    val lookups: StateFlow<TransactionLookups> = lookupsSource.lookups

    private val passwords = mutableMapOf<String, String>()
    private var lastDefaultRequest = ""
    private var running: Job? = null
    private var modelsJob: Job? = null

    init {
        viewModelScope.launch {
            inbox.attachments.collect { files -> _state.update { it.copy(attachments = files) } }
        }
        viewModelScope.launch {
            inbox.error.collect { error ->
                if (error != null) {
                    _state.update { it.copy(error = error) }
                    inbox.clearError()
                }
            }
        }
    }

    fun saveConfig(config: AiConfig) {
        settings.save(config)
        _state.update { it.copy(config = settings.config.value) }
    }

    /** Lists the models [config]'s key can use; a newer call replaces one still loading. */
    fun loadModels(config: AiConfig) {
        modelsJob?.cancel()
        modelsJob = viewModelScope.launch {
            _state.update { it.copy(models = it.models.copy(loading = true, error = null)) }
            val result = try {
                ModelListState(models = chat.listModels(config))
            } catch (e: AiException) {
                ModelListState(error = e.aiMessage(context))
            }
            _state.update { it.copy(models = result) }
        }
    }

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch { inbox.add(uris) }
    }

    fun removeAttachment(attachment: AiAttachment) {
        inbox.remove(attachment)
    }

    fun setRequest(text: String) {
        _state.update { it.copy(request = text) }
    }

    /** Sends the files and request to the model. [defaultRequest] is used when the box is empty. */
    fun analyze(defaultRequest: String) {
        lastDefaultRequest = defaultRequest
        val s = _state.value
        if (running?.isActive == true || (s.attachments.isEmpty() && s.request.isBlank())) return
        running = viewModelScope.launch {
            val started = AiPhase.Running(files = s.attachments.size)
            _state.update { it.copy(phase = started, error = null, passwordFor = null) }
            fun progress(change: (AiPhase.Running) -> AiPhase.Running) =
                _state.update { state -> (state.phase as? AiPhase.Running)?.let { state.copy(phase = change(it)) } ?: state }
            try {
                val parts = reader.parts(s.attachments, passwords)
                progress { it.copy(images = parts.count { part -> part is AiPart.Image }) }
                val proposal = session.run(parts, s.request.ifBlank { defaultRequest }) { step ->
                    progress { it.copy(steps = it.steps + step) }
                }
                _state.update {
                    it.copy(
                        phase = AiPhase.Review,
                        lastRun = (it.phase as? AiPhase.Running)?.copy(finishedAt = SystemClock.elapsedRealtime()),
                        summary = proposal.summary,
                        stoppedAtLimit = proposal.stoppedAtLimit,
                        // Likely duplicates start left out
                        review = proposal.changes.map { change ->
                            ReviewItem(change, included = (change as? LedgerChange.Add)?.draft?.possibleDuplicate == null)
                        }
                    )
                }
            } catch (e: PdfPasswordRequired) {
                _state.update { it.copy(phase = AiPhase.Compose, passwordFor = e.name) }
            } catch (e: AiException) {
                _state.update { it.copy(phase = AiPhase.Compose, error = e.aiMessage(context)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(phase = AiPhase.Compose) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = AiPhase.Compose, error = e.aiMessage(context)) }
            }
        }
    }

    fun cancel() {
        running?.cancel()
    }

    fun submitPassword(password: String?) {
        val name = _state.value.passwordFor ?: return
        if (password == null) {
            _state.update { it.copy(passwordFor = null) }
            return
        }
        passwords[name] = password
        analyze(lastDefaultRequest)
    }

    fun setIncluded(index: Int, included: Boolean) {
        _state.update { s ->
            s.copy(review = s.review.mapIndexed { i, item -> if (i == index) item.copy(included = included) else item })
        }
    }

    /** Replaces a proposed addition with the user's corrected version. */
    fun editDraft(index: Int, draft: TransactionDraft) {
        _state.update { s ->
            s.copy(review = s.review.mapIndexed { i, item ->
                if (i == index && item.change is LedgerChange.Add) item.copy(change = LedgerChange.Add(draft)) else item
            })
        }
    }

    fun save() {
        val chosen = _state.value.review.filter { it.included }.map { it.change }
        if (chosen.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                // All or nothing: a failure leaves nothing written, so Save can simply be tried again
                val applied = tools.apply(chosen)
                _state.update { it.copy(phase = AiPhase.Saved(applied)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.aiMessage(context)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun undo() {
        val applied = (_state.value.phase as? AiPhase.Saved)?.applied ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                tools.undo(applied)
                _state.update { it.copy(phase = AiPhase.Review) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.aiMessage(context)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Back to an empty request and no files, keeping the provider settings. */
    fun startOver() {
        passwords.clear()
        inbox.clear()
        _state.update { AiAssistantUiState(config = it.config, attachments = emptyList()) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}

/** How many changes a save wrote. */
fun AppliedChanges.count(): Int =
    createdAccounts.size + balanceRowIds.size + addedIds.size + updated.size + deleted.size
