package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import io.github.hootkit_dev.hootbook.presentation.format.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.coreDate
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.coreTime
import io.github.hootkit_dev.hootbook.presentation.ui.features.transactions.ledgerTables
import io.github.hootkit_dev.hootbook.utils.pickerDate
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private fun emptyDraft() =
    TransactionDraft(
        TransactionKind.EXPENSE,
        BigDecimal.ZERO,
        null,
        java.time.LocalDateTime.now().coreTime(),
        null,
        null,
        null,
        null,
        null,
        null,
        "",
        null,
        null,
        emptyList(),
    )

private fun emptySubscription() =
    SubscriptionDraft(
        "",
        Money(BigDecimal.ZERO, ""),
        Cycle(1u, CycleUnit.MONTH),
        null,
        java.time.LocalDate.now().coreDate(),
        null,
        null,
        null,
        null,
    )

data class AddUiState(
    val transaction: TransactionDraft = emptyDraft(),
    val subscription: SubscriptionDraft = emptySubscription(),
    val subscriptionId: String? = null,
    val accounts: Map<String, Account> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val people: Map<String, Person> = emptyMap(),
    val templates: List<QuickTemplate> = emptyList(),
    val suggestions: List<TemplateSuggestion> = emptyList(),
    val merchants: List<String> = emptyList(),
    val duplicates: List<Transaction> = emptyList(),
    val amount: String = "",
    val targetAmount: String = "",
    val subscriptionAmount: String = "",
    val personId: String? = null,
    val saveAsTemplate: Boolean = false,
    val customCycle: Boolean = false,
    val loading: Boolean = true,
    val loadingSource: Boolean = false,
    val writing: Boolean = false,
    val importing: Boolean = false,
    val error: String? = null,
    val amountPadRequested: Boolean = false,
    val saved: Boolean = false,
    // A completed ledger command must not be replayed if an optional follow-up fails.
    val createdTransactionId: String? = null,
    val loanLinked: Boolean = false,
)

@HiltViewModel
class AddViewModel
@Inject
constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val savedState: SavedStateHandle,
    val attachmentStore: CoreAttachmentStore,
    private val preferences: CoreUiPreferencesStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(restoreAddDraft(savedState) ?: AddUiState())
    val uiState = mutableState.asStateFlow()
    private var initialized = savedState.contains("add_draft")
    private var sourceVersion = 0L
    private var sourceJob: Job? = null
    private var conversionJob: Job? = null
    private var merchantJob: Job? = null
    private var duplicateJob: Job? = null
    private var duplicateVersion = 0L
    private var queryJob: Job? = null
    private val imported = mutableSetOf<String>()

    init {
        refresh()
    }

    fun refresh() {
        queryJob?.cancel()
        queryJob =
            viewModelScope.launch {
                session
                    .observe(
                        changes,
                        ledgerTables +
                            setOf(
                                Table.SUBSCRIPTIONS,
                                Table.QUICK_TEMPLATES,
                                Table.PEOPLE,
                                Table.DEBTS,
                            ),
                    ) { core ->
                        coreQuery {
                            val accounts =
                                core.accounts().use {
                                    it.overview().accounts.associateBy(Account::id)
                                }
                            val categories =
                                core.categories().use {
                                    it.list(null, true).associateBy(Category::id)
                                }
                            val today = java.time.LocalDate.now().coreDate()
                            core.plans().use { plans ->
                                AddUiState(
                                    accounts = accounts,
                                    categories = categories,
                                    people = plans.people(false, today).associateBy(Person::id),
                                    templates = plans.templates(),
                                    suggestions = plans.templateSuggestions(today),
                                )
                            }
                        }
                    }
                    .collect { result ->
                        val rows =
                            result.getOrElse { failure ->
                                mutableState.update {
                                    it.copy(loading = false, error = failure.message)
                                }
                                return@collect
                            }
                        mutableState.update {
                            it.copy(
                                accounts = rows.accounts,
                                categories = rows.categories,
                                people = rows.people,
                                templates = rows.templates,
                                suggestions = rows.suggestions,
                                loading = false,
                            )
                        }
                    }
            }
    }

    fun initialize(initialTab: Int, subscriptionId: String?, kind: String?, templateId: String?) {
        if (initialized) return
        initialized = true
        source {
            val defaults =
                session.withCore {
                    it.settings().use { settings ->
                        settings.mainCurrency() to settings.defaultAccount()
                    }
                }
            val prefs = preferences.ready()
            val defaultAccount =
                session.withCore {
                    it.accounts().use { accounts ->
                        accounts.overview().accounts.find { a -> a.id == defaults.second }
                    }
                }
            val currency =
                prefs.defaultEntryCurrency?.takeIf { prefs.defaultEntryEnabled }
                    ?: defaultAccount?.mainCurrency
                    ?: defaults.first
            var state =
                uiState.value.copy(
                    transaction =
                        uiState.value.transaction.copy(
                            currency = currency,
                            accountId = defaults.second,
                            kind =
                                kind?.let { TransactionKind.valueOf(it) } ?: TransactionKind.EXPENSE,
                        ),
                    subscription =
                        uiState.value.subscription.copy(
                            amount = Money(BigDecimal.ZERO, currency),
                            accountId = defaults.second,
                        ),
                )
            if (templateId != null) {
                val prefill =
                    session.withCore {
                        it.plans().use { plans -> plans.templatePrefill(templateId) }
                    }
                state = prefill(state, prefill)
            }
            if (subscriptionId != null) {
                val row =
                    session.withCore {
                        it.plans().use { plans ->
                            plans.subscriptions(java.time.LocalDate.now().coreDate(), true).find {
                                it.id == subscriptionId
                            }
                        }
                    } ?: error("Subscription no longer exists")
                state =
                    if (initialTab == 1)
                        state.copy(
                            subscriptionId = row.id,
                            subscription =
                                SubscriptionDraft(
                                    row.name,
                                    row.amount,
                                    row.cycle,
                                    row.endDate,
                                    row.nextDue,
                                    row.accountId,
                                    row.categoryId,
                                    row.subcategoryId,
                                    row.notes,
                                ),
                            subscriptionAmount = row.amount.amount.toPlainString(),
                            customCycle = entryCycleKey(row.cycle) == "Custom",
                        )
                    else
                        state.copy(
                            transaction =
                                state.transaction.copy(
                                    kind = TransactionKind.EXPENSE,
                                    amount = row.amount.amount,
                                    currency = row.amount.currency,
                                    accountId = row.accountId,
                                    categoryId = row.categoryId,
                                    subcategoryId = row.subcategoryId,
                                    merchant = row.name,
                                    notes = row.notes,
                                    subscriptionId = row.id,
                                ),
                            amount = row.amount.amount.toPlainString(),
                        )
            }
            state
        }
    }

    private fun prefill(state: AddUiState, value: TemplatePrefill) =
        state.copy(
            transaction =
                state.transaction.copy(
                    kind = value.kind,
                    merchant = value.merchant,
                    categoryId = value.categoryId,
                    subcategoryId = value.subcategoryId,
                    notes = value.notes,
                    accountId = value.accountId ?: state.transaction.accountId,
                    currency = value.currency ?: state.transaction.currency,
                    amount = value.amount ?: state.transaction.amount,
                    toAccountId = null,
                    toCurrency = null,
                    toAmount = null,
                    subscriptionId = null,
                ),
            amount = value.amount?.toPlainString() ?: state.amount,
            amountPadRequested = value.amount == null && state.amount.isBlank(),
        )

    private fun source(load: suspend () -> AddUiState) {
        if (uiState.value.writing || uiState.value.createdTransactionId != null) return
        val mine = ++sourceVersion
        sourceJob?.cancel()
        mutableState.update { it.copy(loadingSource = true, error = null) }
        sourceJob =
            viewModelScope.launch {
                try {
                    val value = load()
                    ensureActive()
                    if (mine == sourceVersion) {
                        mutableState.update { old ->
                            value.copy(
                                accounts = old.accounts,
                                categories = old.categories,
                                people = old.people,
                                templates = old.templates,
                                suggestions = old.suggestions,
                                loading = old.loading,
                                loadingSource = false,
                            )
                        }
                        persist()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (mine == sourceVersion)
                        mutableState.update {
                            it.copy(loadingSource = false, error = failure.message)
                        }
                }
            }
    }

    private fun update(change: (AddUiState) -> AddUiState) {
        if (
            uiState.value.writing ||
                uiState.value.saved ||
                uiState.value.createdTransactionId != null
        )
            return
        ++sourceVersion
        sourceJob?.cancel()
        mutableState.update {
            change(it).copy(error = null, loadingSource = false, duplicates = emptyList())
        }
        persist()
    }

    private fun transaction(change: (TransactionDraft) -> TransactionDraft) = update {
        it.copy(transaction = change(it.transaction))
    }

    private fun subscription(change: (SubscriptionDraft) -> SubscriptionDraft) = update {
        it.copy(subscription = change(it.subscription))
    }

    private fun persist() = saveAddDraft(savedState, uiState.value)

    fun updateTransactionAmount(value: String) {
        update {
            it.copy(
                amount = value,
                transaction =
                    it.transaction.copy(amount = value.toBigDecimalOrNull() ?: BigDecimal.ZERO),
            )
        }
        refreshTransferAmount()
    }

    fun updateTargetAmount(value: String) = update {
        it.copy(
            targetAmount = value,
            transaction = it.transaction.copy(toAmount = value.toBigDecimalOrNull()),
        )
    }

    fun updateTransactionType(kind: TransactionKind) = transaction {
        it.copy(
            kind = kind,
            categoryId = null,
            subcategoryId = null,
            toAccountId = null,
            toCurrency = null,
            toAmount = null,
            subscriptionId = null,
        )
    }

    fun updateTransactionMerchant(value: String) {
        transaction { it.copy(merchant = value) }
        merchantJob?.cancel()
        if (value.isBlank()) {
            mutableState.update { it.copy(merchants = emptyList()) }
            return
        }
        merchantJob =
            viewModelScope.launch {
                delay(150)
                try {
                    val rows =
                        session.withCore {
                            it.ledger().use { ledger -> merchantCompletions(ledger, value) }
                        }
                    ensureActive()
                    if (uiState.value.transaction.merchant == value)
                        mutableState.update { it.copy(merchants = rows) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    /* Completion is optional; retain the user's draft. */
                }
            }
    }

    fun applyMerchantSuggestion(value: String) = updateTransactionMerchant(value)

    fun updateTransactionCategory(id: String) = transaction {
        it.copy(categoryId = id, subcategoryId = null)
    }

    fun updateTransactionSubcategory(id: String?) = transaction { it.copy(subcategoryId = id) }

    fun updateTransactionNotes(value: String) = transaction { it.copy(notes = value) }

    fun updateTransactionDate(millis: Long) = transaction {
        it.copy(
            dateTime =
                pickerDate(millis).atTime(it.dateTime.toJavaDateTime().toLocalTime()).coreTime()
        )
    }

    fun updateTransactionTime(hour: Int, minute: Int) = transaction {
        it.copy(dateTime = it.dateTime.copy(hour = hour.toUByte(), minute = minute.toUByte()))
    }

    fun updateTransactionAccount(account: Account?) {
        transaction {
            it.copy(accountId = account?.id, currency = account?.mainCurrency ?: it.currency)
        }
        refreshTransferAmount()
    }

    fun updateTransactionCurrency(currency: String) {
        transaction { it.copy(currency = currency) }
        refreshTransferAmount()
    }

    fun updateTransactionTargetAccount(account: Account?) {
        transaction {
            it.copy(toAccountId = account?.id, toCurrency = account?.mainCurrency, toAmount = null)
        }
        refreshTransferAmount()
    }

    fun updateTransactionTargetCurrency(currency: String) {
        transaction { it.copy(toCurrency = currency, toAmount = null) }
        refreshTransferAmount()
    }

    private fun refreshTransferAmount() {
        conversionJob?.cancel()
        val draft = uiState.value.transaction
        if (draft.kind != TransactionKind.TRANSFER) return
        mutableState.update {
            it.copy(targetAmount = "", transaction = it.transaction.copy(toAmount = null))
        }
        val from = draft.currency ?: return
        val to = draft.toCurrency ?: return
        if (from == to || uiState.value.amount.toBigDecimalOrNull() == null) {
            persist()
            return
        }
        val requested = uiState.value.transaction
        conversionJob =
            viewModelScope.launch {
                try {
                    val amount =
                        session.withCore {
                            it.currency().use { currency ->
                                currency.convert(requested.amount, from, to)
                            }
                        }
                    ensureActive()
                    if (
                        uiState.value.transaction == requested &&
                            !uiState.value.writing &&
                            !uiState.value.saved
                    ) {
                        mutableState.update {
                            it.copy(
                                targetAmount = amount?.toPlainString().orEmpty(),
                                transaction = it.transaction.copy(toAmount = amount),
                            )
                        }
                        persist()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    /* The editable received-amount field remains available. */
                }
            }
    }

    fun currenciesOf(account: Account?) = account?.pockets.orEmpty().map { it.currency }

    fun updateTransactionPersonId(id: String?) = update { it.copy(personId = id) }

    fun updateSaveAsQuickTemplate(value: Boolean) = update { it.copy(saveAsTemplate = value) }

    fun onAmountPadOpened() {
        mutableState.update { it.copy(amountPadRequested = false) }
        persist()
    }

    fun applyQuickTemplate(template: QuickTemplate) = applyQuickTemplateById(template.id)

    fun applyQuickTemplateById(id: String) = source {
        var state = uiState.value
        if (state.transaction.currency == null) {
            val defaults =
                session.withCore { core ->
                    val accountId = core.settings().use { it.defaultAccount() }
                    val account =
                        core.accounts().use {
                            it.overview().accounts.find { a -> a.id == accountId }
                        }
                    (account?.mainCurrency ?: core.settings().use { it.mainCurrency() }) to
                        accountId
                }
            val prefs = preferences.ready()
            state =
                state.copy(
                    transaction =
                        state.transaction.copy(
                            accountId = state.transaction.accountId ?: defaults.second,
                            currency =
                                prefs.defaultEntryCurrency?.takeIf { prefs.defaultEntryEnabled }
                                    ?: defaults.first,
                        )
                )
        }
        prefill(state, session.withCore { it.plans().use { plans -> plans.templatePrefill(id) } })
    }

    fun applySuggestedTemplate(row: TemplateSuggestion) = update {
        prefill(
            it,
            TemplatePrefill(
                row.kind,
                row.merchant,
                row.categoryId,
                row.subcategoryId,
                null,
                row.amount,
                row.accountId,
                row.currency,
            ),
        )
    }

    fun pinSuggestedTemplate(row: TemplateSuggestion) = command {
        session.withCore {
            it.plans().use { plans ->
                plans.createTemplate(
                    QuickTemplateDraft(
                        row.name,
                        row.kind,
                        row.merchant,
                        row.categoryId,
                        row.subcategoryId,
                        row.amount,
                        row.amount != null,
                        row.accountId,
                        row.currency,
                        null,
                    )
                )
            }
        }
    }

    fun dismissSuggestedTemplate(row: TemplateSuggestion) = command {
        session.withCore { it.plans().use { plans -> plans.dismissSuggestion(row.key) } }
    }

    fun addCurrencyToAccount(account: Account, currency: String, asTarget: Boolean) = command {
        session.withCore {
            it.accounts().use { api -> api.addCurrency(account.id, currency, null, null) }
        }
        mutableState.update {
            it.copy(
                transaction =
                    if (asTarget) it.transaction.copy(toCurrency = currency)
                    else it.transaction.copy(currency = currency)
            )
        }
        persist()
    }

    fun addPerson(
        name: String,
        phone: String?,
        notes: String?,
        color: String,
        avatar: String?,
        relation: PersonRelation?,
    ) = command {
        attachmentStore.retainForCore(listOfNotNull(avatar))
        val id =
            session.withCore {
                it.plans().use { plans ->
                    plans.createPerson(PersonDraft(name, phone, notes, color, avatar, relation))
                }
            }
        mutableState.update { it.copy(personId = id) }
        persist()
    }

    fun addTransactionAttachment(uri: Uri) {
        if (
            uiState.value.writing ||
                uiState.value.importing ||
                uiState.value.saved ||
                uiState.value.createdTransactionId != null
        )
            return
        mutableState.update { it.copy(importing = true) }
        viewModelScope.launch {
            try {
                val ref = attachmentStore.importFrom(uri)
                imported += ref
                mutableState.update {
                    it.copy(
                        transaction =
                            it.transaction.copy(attachments = it.transaction.attachments + ref)
                    )
                }
                persist()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = failure.message) }
            } finally {
                mutableState.update { it.copy(importing = false) }
            }
        }
    }

    fun removeTransactionAttachment(ref: String) {
        if (
            uiState.value.writing ||
                uiState.value.saved ||
                uiState.value.createdTransactionId != null
        )
            return
        transaction { it.copy(attachments = it.attachments - ref) }
        if (imported.remove(ref))
            viewModelScope.launch { attachmentStore.discardDraft(listOf(ref)) }
    }

    fun checkDuplicates() {
        val draft = uiState.value.transaction
        if (uiState.value.amount.toBigDecimalOrNull() == null) return
        val mine = ++duplicateVersion
        duplicateJob?.cancel()
        duplicateJob =
            viewModelScope.launch {
                try {
                    val rows =
                        session.withCore {
                            it.ledger().use { ledger ->
                                ledger.findDuplicates(draft).map { id -> ledger.get(id) }
                            }
                        }
                    ensureActive()
                    if (mine == duplicateVersion && draft == uiState.value.transaction)
                        mutableState.update { it.copy(duplicates = rows) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    /* Duplicate hints never prevent saving an intentional entry. */
                }
            }
    }

    fun saveTransaction(onSuccess: () -> Unit) {
        if (uiState.value.loadingSource || uiState.value.importing || uiState.value.saved) return
        val before = uiState.value
        command {
            val draft =
                before.transaction.copy(
                    amount = BigDecimal(before.amount),
                    toAmount =
                        before.targetAmount
                            .takeIf {
                                it.isNotBlank() &&
                                    before.transaction.kind == TransactionKind.TRANSFER
                            }
                            ?.let(::BigDecimal),
                )
            val loan = draft.kind in listOf(TransactionKind.LOAN_OUT, TransactionKind.LOAN_IN)
            if (loan) requireNotNull(before.personId) { "Select a person" }
            attachmentStore.retainForCore(draft.attachments)
            val id =
                before.createdTransactionId
                    ?: session
                        .withCore { it.ledger().use { ledger -> ledger.add(draft) } }
                        .also { id ->
                            mutableState.update { it.copy(createdTransactionId = id) }
                            persist()
                        }
            if (loan && !before.loanLinked) {
                session.withCore {
                    it.plans().use { plans ->
                        plans.linkTransaction(id, requireNotNull(before.personId), null)
                    }
                }
                mutableState.update { it.copy(loanLinked = true) }
                persist()
            }
            if (
                before.saveAsTemplate &&
                    draft.kind in
                        listOf(
                            TransactionKind.EXPENSE,
                            TransactionKind.INCOME,
                            TransactionKind.INVESTMENT,
                        )
            )
                session.withCore {
                    it.plans().use { plans ->
                        plans.createTemplate(
                            QuickTemplateDraft(
                                draft.merchant,
                                draft.kind,
                                draft.merchant,
                                draft.categoryId,
                                draft.subcategoryId,
                                draft.amount,
                                true,
                                draft.accountId,
                                draft.currency,
                                draft.notes,
                            )
                        )
                    }
                }
            imported.clear()
            mutableState.update { it.copy(saved = true) }
            persist()
            onSuccess()
        }
    }

    fun updateSubscriptionService(value: String) = subscription { it.copy(name = value) }

    fun updateSubscriptionAmount(value: String) = update {
        it.copy(
            subscriptionAmount = value,
            subscription =
                it.subscription.copy(
                    amount =
                        it.subscription.amount.copy(
                            amount = value.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        )
                ),
        )
    }

    fun updateSubscriptionBillingCycle(value: String) = update {
        it.copy(
            customCycle = value == "Custom",
            subscription =
                it.subscription.copy(
                    cycle = entryCycle(value) ?: it.subscription.cycle,
                    endDate = it.subscription.endDate.takeIf { value == "Custom" },
                ),
        )
    }

    fun updateSubscriptionCustomCycleCount(value: Int) = subscription {
        it.copy(cycle = it.cycle.copy(every = value.coerceAtLeast(1).toUInt()))
    }

    fun updateSubscriptionCustomCycleUnit(value: String) = subscription {
        it.copy(cycle = it.cycle.copy(unit = CycleUnit.valueOf(value.uppercase())))
    }

    fun updateSubscriptionCustomCycleEndDate(value: java.time.LocalDate?) = subscription {
        it.copy(endDate = value?.coreDate())
    }

    fun updateSubscriptionNextPaymentDate(millis: Long) = subscription {
        it.copy(nextDue = pickerDate(millis).coreDate())
    }

    fun updateSubscriptionCategory(id: String) = subscription {
        it.copy(categoryId = id, subcategoryId = null)
    }

    fun updateSubscriptionSubcategory(id: String?) = subscription { it.copy(subcategoryId = id) }

    fun updateSubscriptionNotes(value: String) = subscription { it.copy(notes = value) }

    fun updateSubscriptionAccount(account: Account?) = subscription {
        it.copy(
            accountId = account?.id,
            amount = it.amount.copy(currency = account?.mainCurrency ?: it.amount.currency),
        )
    }

    fun saveSubscription(onSuccess: () -> Unit) {
        if (uiState.value.loadingSource || uiState.value.saved) return
        val before = uiState.value
        command {
            val draft =
                before.subscription.copy(
                    amount =
                        before.subscription.amount.copy(
                            amount = BigDecimal(before.subscriptionAmount)
                        )
                )
            session.withCore {
                it.plans().use { plans ->
                    if (before.subscriptionId == null) plans.createSubscription(draft)
                    else plans.updateSubscription(before.subscriptionId, draft)
                }
            }
            mutableState.update { it.copy(saved = true) }
            persist()
            onSuccess()
        }
    }

    fun cancel(onClosed: () -> Unit) {
        if (uiState.value.writing || uiState.value.importing) return
        sourceJob?.cancel()
        viewModelScope.launch {
            attachmentStore.discardDraft(imported.toList())
            imported.clear()
            onClosed()
        }
    }

    private fun command(block: suspend () -> Unit) {
        if (uiState.value.writing || uiState.value.saved) return
        mutableState.update { it.copy(writing = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = failure.message) }
            } finally {
                mutableState.update { it.copy(writing = false) }
                refresh()
            }
        }
    }
}
