package io.github.hootkit_dev.hootbook.presentation.core.transactions

import androidx.lifecycle.SavedStateHandle
import android.net.Uri
import android.os.Bundle
import java.io.File
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreMoneyPresentation
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreDisplayedMoney
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50u
internal val TRANSACTION_TABLES = setOf(
    Table.TRANSACTIONS, Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.BALANCE_ANCHORS,
    Table.CATEGORIES, Table.SUBCATEGORIES, Table.SETTINGS, Table.EXCHANGE_RATES, Table.BROKERAGE,
    Table.SUBSCRIPTIONS, Table.QUICK_TEMPLATES, Table.DEBTS, Table.PEOPLE,
)

data class CoreTransactionsUiState(
    val page: TransactionPage? = null,
    val summary: LedgerSummary? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val people: List<Person> = emptyList(),
    val mainCurrency: String = "CNY",
    val defaultAccountId: String? = null,
    val query: CoreTransactionQuery = CoreTransactionQuery(),
    val offset: UInt = 0u,
    val detailId: String? = null,
    val detail: Transaction? = null,
    val form: CoreTransactionForm? = null,
    val loading: Boolean = true,
    val writing: Boolean = false,
    val loadingSource: Boolean = false,
    val error: String? = null,
    val queryFailure: String? = null,
    val commandFailure: String? = null,
    val notice: String? = null,
    val undoToken: String? = null,
    val selectedIds: Set<String> = emptySet(),
    val duplicateCandidates: List<Transaction> = emptyList(),
    val checkingDuplicates: Boolean = false,
    val merchants: List<String> = emptyList(),
    val importedDraftRefs: Set<String> = emptySet(),
    val importingAttachment: Boolean = false,
    val exportFile: File? = null,
    val exportCount: UInt = 0u,
    val exporting: Boolean = false,
    val createdSubscriptionId: String? = null,
    val customSymbols: Map<String, String> = emptyMap(),
    val displayValues: Map<String, CoreDisplayedMoney> = emptyMap(),
)

@HiltViewModel
class CoreTransactionsViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val savedState: SavedStateHandle? = null,
    private val uiPreferences: CoreUiPreferencesStore? = null,
    val attachmentStore: CoreAttachmentStore? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CoreTransactionsUiState(form = restoreTransactionForm(savedState?.get("transaction_draft"))))
    val state = mutableState.asStateFlow()
    private var queryJob: Job? = null
    private var version = 0L
    private var initialized = false
    private var sourceJob: Job? = null
    private var sourceVersion = 0L
    private var referencesReady = false
    private var draftDefaultsReady = true
    private var writeJob: Job? = null
    private var auxiliaryJob: Job? = null
    private var auxiliaryVersion = 0L
    private var exportJob: Job? = null
    private var editorIdentity = 0L

    init { viewModelScope.launch { changes.observe(TRANSACTION_TABLES).collect { refresh() } } }

    fun initialize(accountId: String?, transactionId: String?, subscriptionId: String? = null, templateId: String? = null) {
        if (initialized) return
        initialized = true
        mutableState.update { it.copy(query = it.query.copy(accountId = accountId), detailId = transactionId) }
        refresh()
        if (state.value.form != null) return // Saved draft outranks a replayed launch source after process recreation.
        if (listOfNotNull(transactionId, subscriptionId, templateId).size > 1) {
            mutableState.update { it.copy(error = "交易详情、订阅付款和模板来源只能指定一个") }
        } else if (subscriptionId != null) startSubscriptionPayment(subscriptionId)
        else if (templateId != null) applyTemplate(templateId)
    }

    fun applyQuery(query: CoreTransactionQuery) {
        if (state.value.writing) return
        mutableState.update { it.copy(query = query, offset = 0u, page = null, summary = null, selectedIds = emptySet(), duplicateCandidates = emptyList()) }
        refresh()
    }

    fun nextPage() {
        val current = state.value
        if (current.writing || current.loading || current.page == null || current.offset + PAGE_SIZE >= current.page.total) return
        mutableState.update { it.copy(offset = it.offset + PAGE_SIZE) }
        refresh()
    }

    fun previousPage() {
        if (state.value.writing || state.value.loading) return
        mutableState.update { it.copy(offset = if (it.offset < PAGE_SIZE) 0u else it.offset - PAGE_SIZE) }
        refresh()
    }

    fun refresh() = refreshInternal(preserveError = false)

    private fun refreshInternal(preserveError: Boolean) {
        val requestVersion = ++version
        queryJob?.cancel()
        val snapshot = state.value
        mutableState.update { it.copy(loading = true, queryFailure = null,
            error = it.commandFailure ?: if (preserveError) it.error else null) }
        queryJob = viewModelScope.launch {
            try {
                val core = session.awaitCore()
                val filter = snapshot.query.toFilter()
                val accounts = core.accounts().use { it.overview().accounts }
                val categories = core.categories().use { it.list(null, true) }
                val (mainCurrency, defaultAccountId) = core.settings().use {
                    it.mainCurrency() to it.defaultAccount()
                }
                val today = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
                val people = core.plans().use { it.people(false, today) }
                val preferences = uiPreferences?.ready()
                val symbols = if (uiPreferences != null) core.currency().use { it.customCurrencies().associate { row -> row.code to row.symbol } } else emptyMap()
                val unified = preferences?.unifiedDisplayCurrency?.takeIf { preferences.unifiedDisplayEnabled }
                // These are temporary UniFFI service handles, not the application-owned core.
                core.ledger().use { ledger ->
                    var offset = snapshot.offset
                    var page = ledger.list(filter, PageRequest(offset, PAGE_SIZE))
                    // A deletion may remove the last page. Re-query, never filter a single page locally.
                    if (offset > 0u && offset >= page.total) {
                        offset = if (page.total == 0u) 0u else ((page.total - 1u) / PAGE_SIZE) * PAGE_SIZE
                        page = ledger.list(filter, PageRequest(offset, PAGE_SIZE))
                    }
                    val summary = ledger.summary(filter)
                    val detail = snapshot.detailId?.let { ledger.get(it) }
                    val displayed = if (unified != null) core.currency().use { api ->
                        (page.items + listOfNotNull(detail)).distinctBy { it.id }.associate { row ->
                            row.id to CoreMoneyPresentation.convert(api, row.amount, row.currency, unified)
                        }
                    } else emptyMap()
                    currentCoroutineContext().ensureActive()
                    if (requestVersion == version) {
                        referencesReady = true
                        mutableState.update {
                            it.copy(page = page, summary = summary, accounts = accounts, categories = categories,
                                people = people, mainCurrency = mainCurrency, defaultAccountId = defaultAccountId,
                                offset = offset, detail = detail, loading = false, customSymbols = symbols, displayValues = displayed,
                                queryFailure = null, error = it.commandFailure)
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (requestVersion == version) mutableState.update {
                    val message = queryError(failure)
                    it.copy(loading = false, queryFailure = message, error = it.commandFailure ?: message)
                }
            }
        }
    }

    fun openDetail(id: String) {
        if (state.value.writing) return
        ++editorIdentity
        invalidateSource()
        mutableState.update { it.copy(detailId = id, detail = null, form = null, error = null) }
        refresh()
    }

    fun newTransaction(kind: TransactionKind = TransactionKind.EXPENSE) {
        if (state.value.writing) return
        ++editorIdentity
        ++version
        queryJob?.cancel()
        invalidateSource()
        val current = state.value
        draftDefaultsReady = referencesReady
        val selectedAccount = current.query.accountId ?: current.defaultAccountId
        val entryCurrency = uiPreferences?.state?.value?.takeIf { it.defaultEntryEnabled }?.defaultEntryCurrency
        mutableState.update {
            it.copy(detailId = null, detail = null, error = null, form = CoreTransactionForm(
                kind = kind, accountId = selectedAccount,
                currency = entryCurrency ?: current.accounts.find { account -> account.id == selectedAccount }?.mainCurrency ?: current.mainCurrency,
            ))
        }
        persistDraft()
        // Re-query references against detailId=null; an old non-cancellable detail must not refill it.
        refresh()
        if (uiPreferences != null) loadSource { base ->
            val prefs = uiPreferences.ready()
            val core = session.awaitCore()
            val defaults = core.settings().use { it.mainCurrency() to it.defaultAccount() }
            val selected = current.query.accountId ?: defaults.second
            val accounts = core.accounts().use { it.overview().accounts }
            base.copy(accountId = selected, currency = prefs.defaultEntryCurrency?.takeIf { prefs.defaultEntryEnabled }
                ?: accounts.find { it.id == selected }?.mainCurrency ?: defaults.first)
        }
    }

    fun editTransaction() {
        if (state.value.writing) return
        ++editorIdentity
        invalidateSource()
        draftDefaultsReady = true
        state.value.detail?.let { transaction -> mutableState.update { it.copy(form = CoreTransactionForm.from(transaction), error = null) }; persistDraft() }
    }

    fun updateForm(form: CoreTransactionForm) {
        if (!state.value.writing) {
            invalidateSource()
            draftDefaultsReady = true // Explicit user edits outrank all pending defaults.
            mutableState.update { it.copy(form = form, error = null, commandFailure = null, queryFailure = null, duplicateCandidates = emptyList()) }; persistDraft()
            requestMerchantSuggestions(form.merchant)
        }
    }

    /** Returns true when the inner detail/editor consumed Back. */
    fun back(): Boolean {
        val current = state.value
        if (current.writing) return true
        if (current.form != null) {
            ++editorIdentity
            invalidateSource()
            val refs = current.importedDraftRefs
            mutableState.update { it.copy(form = null, error = null, importedDraftRefs = emptySet(), duplicateCandidates = emptyList()) }
            persistDraft()
            viewModelScope.launch { attachmentStore?.discardDraft(refs) }
            return true
        }
        if (current.detailId != null) {
            mutableState.update { it.copy(detailId = null, detail = null, error = null) }
            refresh()
            return true
        }
        return false
    }

    fun save() {
        if (state.value.loadingSource || state.value.importingAttachment) return
        val form = state.value.form ?: return
        write {
            val core = session.awaitCore()
            val draft = form.toDraft()
            attachmentStore?.retainForCore(form.attachments)
            val id = core.ledger().use { ledger ->
                if (form.editingId == null) ledger.add(draft)
                else form.editingId.also { ledger.edit(it, draft) }
            }
            mutableState.update { it.copy(form = null, detailId = id, detail = null, notice = "Transaction saved", importedDraftRefs = emptySet(), duplicateCandidates = emptyList()) }
            persistDraft()
        }
    }

    fun delete() {
        val transaction = state.value.detail ?: return
        if (transaction.deleted) return
        write {
            val token = session.awaitCore().ledger().use { it.delete(listOf(transaction.id)) }
            mutableState.update { it.copy(detailId = null, detail = null, form = null, undoToken = token, notice = "交易已删除，可撤销") }
        }
    }

    fun undo() {
        val token = state.value.undoToken ?: return
        write {
            session.awaitCore().ledger().use { it.undo(token) }
            mutableState.update { it.copy(undoToken = null, notice = "删除已撤销") }
        }
    }

    /** Link the displayed existing transaction, never add a second ledger entry. */
    fun linkExistingTransaction(personId: String, kind: DebtKind?) {
        val transaction = state.value.detail ?: return
        if (transaction.deleted || transaction.debtId != null || state.value.form != null) return
        write {
            session.awaitCore().plans().use { it.linkTransaction(transaction.id, personId, kind) }
            mutableState.update { it.copy(detail = null, notice = "已有交易已关联到人员／借贷") }
        }
    }

    private fun invalidateSource() {
        ++auxiliaryVersion
        auxiliaryJob?.cancel()
        ++sourceVersion
        sourceJob?.cancel()
        mutableState.update { it.copy(loadingSource = false, checkingDuplicates = false) }
    }

    fun startSubscriptionPayment(id: String) {
        if (state.value.writing || state.value.form?.editingId != null) return
        newTransaction()
        loadSource { base ->
            val date = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue.toUByte(), it.dayOfMonth.toUByte()) }
            val row = session.awaitCore().plans().use { it.subscriptions(date, true).find { row -> row.id == id } }
                ?: throw IllegalArgumentException("订阅已不存在，请返回重新选择")
            base.withSubscription(row)
        }
    }

    fun applyTemplate(id: String) {
        if (state.value.writing || state.value.form?.editingId != null || state.value.detailId != null) return
        val queryAccountId = state.value.query.accountId
        val needsDefaults = state.value.form == null || !draftDefaultsReady
        if (state.value.form == null) newTransaction()
        loadSource { base ->
            val core = session.awaitCore()
            // First navigation can precede the reference query. Resolve new-draft defaults
            // through Core, not the UiState placeholder CNY/null. Existing drafts are untouched.
            val readyBase = if (needsDefaults) {
                val (currency, account) = core.settings().use { it.mainCurrency() to it.defaultAccount() }
                val selected = queryAccountId ?: account
                val accounts = core.accounts().use { it.overview().accounts }
                val prefs = uiPreferences?.ready()
                base.copy(accountId = selected, currency = prefs?.defaultEntryCurrency?.takeIf { prefs.defaultEntryEnabled }
                    ?: accounts.find { it.id == selected }?.mainCurrency ?: currency)
            } else base
            readyBase.withTemplate(core.plans().use { it.templatePrefill(id) })
        }
    }

    /** A delayed native callback must never replace a newer source or a user-edited draft. */
    private fun loadSource(prefill: suspend (CoreTransactionForm) -> CoreTransactionForm) {
        val base = state.value.form ?: return
        invalidateSource()
        val mine = sourceVersion
        mutableState.update { it.copy(loadingSource = true, error = null) }
        sourceJob = viewModelScope.launch {
            try {
                val form = prefill(base)
                if (mine == sourceVersion) {
                    draftDefaultsReady = true
                    mutableState.update { it.copy(form = form, loadingSource = false) }
                    persistDraft()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (mine == sourceVersion) mutableState.update { it.copy(loadingSource = false, error = queryError(failure)) }
            }
        }
    }

    private fun persistDraft() { savedState?.set("transaction_draft", state.value.form?.let(::saveTransactionForm)) }

    fun toggleSelected(id: String) {
        if (state.value.writing) return
        mutableState.update { it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id) }
    }
    fun selectCurrentPage() { if (!state.value.writing) mutableState.update { it.copy(selectedIds = it.selectedIds + it.page?.items.orEmpty().map(Transaction::id)) } }
    fun clearSelection() { if (!state.value.writing) mutableState.update { it.copy(selectedIds = emptySet()) } }
    fun selectAllMatching() {
        if (state.value.writing) return
        val filter = state.value.query.toFilter()
        write {
            val ids = collectTransactionIds(filter)
            mutableState.update { it.copy(selectedIds = ids.toSet()) }
        }
    }
    private suspend fun collectTransactionIds(filter: TransactionFilter): List<String> {
        val revision = changes.revisions.value
        val ids = mutableListOf<String>()
        session.awaitCore().ledger().use { api ->
            var offset = 0u
            var total: UInt? = null
            do {
                currentCoroutineContext().ensureActive()
                check(changes.revisions.value == revision) { "Ledger changed; select again" }
                val page = api.list(filter, PageRequest(offset, 200u))
                check(total == null || total == page.total) { "Ledger changed; select again" }
                total = page.total
                check(page.items.isNotEmpty() || offset >= page.total) { "Incomplete Core page" }
                ids += page.items.map(Transaction::id)
                offset += page.items.size.toUInt()
            } while (offset < requireNotNull(total))
        }
        check(ids.distinct().size == ids.size) { "Ledger changed; select again" }
        return ids
    }
    fun deleteSelected() {
        val ids = state.value.selectedIds.toList(); if (ids.isEmpty()) return
        write { val token = session.awaitCore().ledger().use { it.delete(ids) }
            mutableState.update { it.copy(selectedIds = emptySet(), undoToken = token, detailId = null, detail = null) } }
    }
    fun purgeSelected(allRecycleBin: Boolean = false) {
        val ids = state.value.selectedIds.toList()
        if (!allRecycleBin && ids.isEmpty()) return
        write {
            val targets = if (allRecycleBin) collectTransactionIds(CoreTransactionQuery(deleted = DeletedFilter.ONLY).toFilter()) else ids
            if (targets.isNotEmpty()) session.awaitCore().ledger().use { it.purge(targets) }
            mutableState.update { it.copy(selectedIds = emptySet(), detailId = null, detail = null, notice = "Permanently deleted") }
        }
    }
    fun bulkUpdate(form: CoreTransactionBulkForm) {
        val ids = state.value.selectedIds.toList(); if (ids.isEmpty()) return
        write { session.awaitCore().ledger().use { it.bulkUpdate(ids, form.toPatch()) }; mutableState.update { it.copy(selectedIds = emptySet()) } }
    }
    fun checkDuplicates() {
        if (state.value.writing || state.value.checkingDuplicates) return
        val form = state.value.form ?: state.value.detail?.let(CoreTransactionForm::from) ?: return
        val mine = ++auxiliaryVersion
        auxiliaryJob?.cancel()
        mutableState.update { it.copy(checkingDuplicates = true) }
        auxiliaryJob = viewModelScope.launch {
            try {
                val candidates = session.awaitCore().ledger().use { api -> api.findDuplicates(form.toDraft()).filter { it != form.editingId }.map { api.get(it) } }
                currentCoroutineContext().ensureActive()
                if (mine == auxiliaryVersion) mutableState.update { it.copy(duplicateCandidates = candidates, checkingDuplicates = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (mine == auxiliaryVersion) mutableState.update { it.copy(error = queryError(failure), checkingDuplicates = false) } }
        }
    }
    fun selectDuplicateCandidates() { if (!state.value.writing) mutableState.update { it.copy(selectedIds = it.duplicateCandidates.map(Transaction::id).toSet()) } }
    private fun requestMerchantSuggestions(prefix: String) {
        if (prefix.isBlank()) { mutableState.update { it.copy(merchants = emptyList()) }; return }
        val mine = ++auxiliaryVersion; auxiliaryJob?.cancel()
        auxiliaryJob = viewModelScope.launch {
            try { val rows = session.awaitCore().ledger().use { it.merchants(prefix, 20u) }
                if (mine == auxiliaryVersion && state.value.form?.merchant == prefix) mutableState.update { it.copy(merchants = rows) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Suggestions must not erase the draft or command error. */ }
        }
    }
    fun createSubscription(cycle: Cycle, endDate: LocalDate?) {
        val id = state.value.detail?.id ?: return
        write { val subscription = session.awaitCore().plans().use { it.subscriptionFromTransaction(id, cycle, endDate) }
            mutableState.update { it.copy(createdSubscriptionId = subscription, notice = "Subscription created") } }
    }
    fun importAttachment(uri: Uri) {
        val store = attachmentStore ?: return
        if (state.value.writing || state.value.importingAttachment || state.value.form == null) return
        val identity = editorIdentity
        mutableState.update { it.copy(importingAttachment = true) }
        viewModelScope.launch {
            var ref: String? = null
            try {
                val imported = store.importFrom(uri)
                ref = imported
                if (editorIdentity == identity && state.value.form != null) {
                    mutableState.update { it.copy(form = it.form?.let { form -> form.copy(attachments = form.attachments + imported) }, importedDraftRefs = it.importedDraftRefs + imported) }
                    persistDraft()
                } else store.discardDraft(listOf(imported))
            } catch (cancelled: CancellationException) { ref?.let { store.discardDraft(listOf(it)) }; throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(error = queryError(failure)) } }
            finally { mutableState.update { it.copy(importingAttachment = false) } }
        }
    }
    fun removeAttachment(ref: String) {
        if (state.value.writing) return
        val owned = ref in state.value.importedDraftRefs
        mutableState.update { it.copy(form = it.form?.copy(attachments = it.form.attachments - ref), importedDraftRefs = it.importedDraftRefs - ref) }
        persistDraft()
        if (owned) viewModelScope.launch { attachmentStore?.discardDraft(listOf(ref)) }
    }
    fun exportCsv() {
        val store = attachmentStore ?: return
        if (state.value.exporting) return
        val snapshot = state.value; val revision = changes.revisions.value
        mutableState.update { it.copy(exporting = true, error = null) }
        exportJob = viewModelScope.launch {
            var file: File? = null
            var completed = false
            try {
                val target = store.createShareFile("csv")
                file = target
                val count = withContext(Dispatchers.IO) { target.outputStream().use { output -> session.awaitCore().ledger().use { api ->
                    CoreTransactionCsv.export(api, snapshot.query.toFilter(), output, snapshot.accounts, snapshot.categories) { changes.revisions.value == revision }
                } } }
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(exportFile = target, exportCount = count) }
                completed = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(error = queryError(failure)) } }
            finally { if (!completed) file?.delete(); mutableState.update { it.copy(exporting = false) } }
        }
    }
    fun cancelWrite() { writeJob?.cancel() }
    fun cancelExport() { exportJob?.cancel() }
    fun clearError() { mutableState.update { it.copy(error = null, commandFailure = null, queryFailure = null) } }
    private fun write(block: suspend () -> Unit) {
        if (state.value.writing) return
        mutableState.update { it.copy(writing = true, error = null, commandFailure = null, notice = null) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { block(); currentCoroutineContext().ensureActive() }
            catch (cancelled: CancellationException) { mutableState.update { it.copy(notice = "Request cancelled; reload to verify any committed change") }; throw cancelled }
            catch (failure: Exception) { mutableState.update {
                val message = queryError(failure)
                it.copy(error = message, commandFailure = message)
            } }
        }
        writeJob = job
        job.invokeOnCompletion { mutableState.update { it.copy(writing = false) }; refreshInternal(preserveError = true) }
        job.start()
    }

}

internal fun queryError(error: Exception): String = when (error) {
    is NumberFormatException -> "Invalid amount: enter a decimal number"
    is java.time.DateTimeException -> "Invalid date: use YYYY-MM-DD or YYYY-MM-DDTHH:MM"
    is CoreException.Invalid -> "Core rejected the input (${error.reason})"
    is CoreException.NotFound -> "Record no longer exists; refresh"
    is CoreException.Conflict -> "Operation conflicts with the current ledger (${error.reason})"
    is CoreException.Storage -> "Local storage operation failed; reload before retrying"
    is CoreException.Network -> "Network request failed"
    is CoreException.Platform -> "Platform operation failed"
    else -> "Operation failed; review the input and reload before retrying"
}
