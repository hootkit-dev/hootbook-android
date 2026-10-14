package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import android.net.Uri
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
import io.github.hootkit_dev.hootbook.presentation.format.entryCycle
import io.github.hootkit_dev.hootbook.presentation.format.entryCycleKey
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal fun Transaction.draft() =
    TransactionDraft(
        kind,
        amount,
        currency,
        dateTime,
        accountId,
        toAccountId,
        toCurrency,
        toAmount,
        categoryId,
        subcategoryId,
        merchant,
        notes,
        subscriptionId,
        attachments,
        receivable,
        collected,
        tagIds,
    )

@HiltViewModel
class TransactionDetailViewModel
@Inject
constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    val attachmentStore: CoreAttachmentStore,
    preferences: CoreUiPreferencesStore,
) : ViewModel() {
    val preferences = preferences.state
    private val mutableState = MutableStateFlow(TransactionDetailUiState())
    val uiState = mutableState.asStateFlow()
    private var identity: String? = null
    private var generation = 0L
    private var query: Job? = null
    private val imported = mutableSetOf<String>()

    fun loadTransaction(id: String) {
        if (identity == id) return
        if (uiState.value.isSaving) return
        identity = id
        mutableState.value = TransactionDetailUiState()
        observeTransaction()
    }

    private fun observeTransaction() {
        val id = identity ?: return
        val mine = ++generation
        query?.cancel()
        query =
            viewModelScope.launch {
                session
                    .observe(
                        changes,
                        ledgerTables + setOf(Table.DEBTS, Table.PEOPLE, Table.SUBSCRIPTIONS),
                    ) { core ->
                        coreQuery {
                            val tx = core.ledger().use { it.get(id) }
                            val categories =
                                core.categories().use {
                                    it.list(null, true).associateBy(Category::id)
                                }
                            val tags =
                                core.tags().use {
                                    it.list(true).associateBy(Tag::id)
                                }
                            val accounts =
                                core.accounts().use {
                                    it.overview().accounts.associateBy(Account::id)
                                }
                            val today = java.time.LocalDate.now().coreDate()
                            val people =
                                core.plans().use { it.people(true, today).associateBy(Person::id) }
                            val debt =
                                tx.debtId?.let { debtId ->
                                    core.plans().use { plans ->
                                        people.keys.firstNotNullOfOrNull { person ->
                                            plans.debts(person).find { it.id == debtId }
                                        }
                                    }
                                }
                            val subscription =
                                tx.subscriptionId?.let { subId ->
                                    core.plans().use {
                                        it.subscriptions(today, true).find { it.id == subId }
                                    }
                                }
                            val currency =
                                preferences.value.unifiedDisplayCurrency?.takeIf {
                                    preferences.value.unifiedDisplayEnabled
                                } ?: core.settings().use { it.mainCurrency() }
                            val converted =
                                if (tx.currency != currency)
                                    core.currency().use {
                                        it.convert(tx.amount, tx.currency, currency)
                                    }
                                else null
                            TransactionDetailUiState(
                                transaction = tx,
                                categories = categories,
                                tags = tags,
                                accounts = accounts,
                                people = people,
                                linkedLendBorrow = debt,
                                subscription = subscription,
                                primaryCurrency = currency,
                                convertedAmount = converted,
                            )
                        }
                    }
                    .collect { result ->
                        val value =
                            result.getOrElse { failure ->
                                if (mine == generation)
                                    mutableState.update { it.copy(errorMessage = coreFailureText(failure)) }
                                return@collect
                            }
                        if (mine == generation)
                            mutableState.update { old ->
                                value.copy(
                                    editingSubscriptionId = old.editingSubscriptionId,
                                    editableTransaction = old.editableTransaction,
                                    isEditMode = old.isEditMode,
                                    isSaving = old.isSaving,
                                    saveSuccess = old.saveSuccess,
                                    duplicateSuccess = old.duplicateSuccess,
                                    deleteSuccess = old.deleteSuccess,
                                    errorMessage = old.errorMessage,
                                    showDeleteDialog = old.showDeleteDialog,
                                    showMarkAsLoanSheet = old.showMarkAsLoanSheet,
                                    showUnmarkLoanConfirm = old.showUnmarkLoanConfirm,
                                    markAsLoanSuccess = old.markAsLoanSuccess,
                                    markAsLoanError = old.markAsLoanError,
                                    recurring = old.recurring,
                                    cycle = old.cycle,
                                    isCustomCycle = old.isCustomCycle,
                                    customCycleCount = old.customCycleCount,
                                    customCycleUnit = old.customCycleUnit,
                                    customCycleEndDate = old.customCycleEndDate,
                                )
                            }
                    }
            }
    }

    fun enterEditMode() {
        if (uiState.value.isSaving) return
        mutableState.update {
            val cycle = it.subscription?.cycle ?: Cycle(1u, CycleUnit.MONTH)
            val end = it.subscription?.endDate
            it.copy(
                isEditMode = true,
                editableTransaction = it.transaction,
                editingSubscriptionId = it.transaction?.subscriptionId,
                recurring = it.transaction?.subscriptionId != null,
                cycle = cycle,
                isCustomCycle = entryCycleKey(cycle) == "Custom",
                customCycleCount = cycle.every.toInt(),
                customCycleUnit = cycle.unit.name.lowercase(),
                customCycleEndDate =
                    end?.let { d -> java.time.LocalDate.of(d.year, d.month.toInt(), d.day.toInt()) },
            )
        }
    }

    fun cancelEdit() {
        if (uiState.value.isSaving) return
        mutableState.update { it.copy(isEditMode = false, editableTransaction = null) }
        val refs = imported.toList()
        imported.clear()
        viewModelScope.launch { attachmentStore.discardDraft(refs) }
    }

    private fun edit(change: (Transaction) -> Transaction) {
        if (!uiState.value.isSaving)
            mutableState.update {
                it.copy(editableTransaction = it.editableTransaction?.let(change))
            }
    }

    fun updateAmount(value: String) {
        val amount = value.toBigDecimalOrNull() ?: return
        edit { it.copy(amount = amount) }
    }

    fun updateMerchantName(value: String) = edit { it.copy(merchant = value) }

    fun updateDescription(value: String) = edit { it.copy(notes = value) }

    fun updateCategory(id: String) = edit { it.copy(categoryId = id, subcategoryId = null) }

    fun updateSubcategory(id: String?) = edit { it.copy(subcategoryId = id) }

    fun updateDateTime(value: java.time.LocalDateTime) = edit {
        it.copy(dateTime = value.coreTime())
    }

    fun updateTransactionType(value: TransactionKind) = edit {
        it.copy(
            kind = value,
            toAccountId = null,
            toCurrency = null,
            toAmount = null,
            receivable = null,
            collected = null,
            categoryId =
                if (value in listOf(TransactionKind.EXPENSE, TransactionKind.INCOME)) it.categoryId
                else null,
            subcategoryId = null,
        )
    }

    fun toggleReceivable(enabled: Boolean) = edit {
        it.copy(receivable = if (enabled) it.amount else null, collected = null)
    }

    fun updateReceivableAmount(value: String) = edit {
        it.copy(receivable = value.toBigDecimalOrNull() ?: it.amount)
    }

    fun toggleCollected(enabled: Boolean) = edit {
        it.copy(collected = if (enabled) it.amount else null, receivable = null)
    }

    fun updateCollectedAmount(value: String) = edit {
        it.copy(collected = value.toBigDecimalOrNull() ?: it.amount)
    }

    fun toggleTag(tagId: String) = edit {
        val current = it.tagIds
        it.copy(tagIds = if (tagId in current) current - tagId else current + tagId)
    }

    fun createTag(name: String, color: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        write {
            val id = session.withCore { it.tags().use { tags -> tags.create(NewTag(trimmed, color)) } }
            mutableState.update { state ->
                state.copy(
                    editableTransaction =
                        state.editableTransaction?.let { tx ->
                            tx.copy(tagIds = if (id in tx.tagIds) tx.tagIds else tx.tagIds + id)
                        }
                )
            }
        }
    }

    fun updateTransactionAccount(value: Account?) = edit { it.copy(accountId = value?.id) }

    fun updateTransactionTargetAccount(value: Account?) = edit {
        it.copy(toAccountId = value?.id, toCurrency = value?.mainCurrency, toAmount = null)
    }

    fun updateTransactionCurrency(value: String) = edit { it.copy(currency = value) }

    fun updateTransactionTargetCurrency(value: String) = edit {
        it.copy(toCurrency = value, toAmount = null)
    }

    fun updateReceivedAmount(value: String) = edit {
        it.copy(toAmount = value.toBigDecimalOrNull())
    }

    fun currenciesOf(account: Account?) = account?.pockets.orEmpty().map { it.currency }

    private fun editSchedule(change: (TransactionDetailUiState) -> TransactionDetailUiState) {
        if (!uiState.value.isSaving) mutableState.update(change)
    }

    fun updateRecurringStatus(value: Boolean) = editSchedule { it.copy(recurring = value) }

    fun updateBillingCycle(value: String) = editSchedule {
        it.copy(
            isCustomCycle = value == "Custom",
            cycle = entryCycle(value) ?: it.cycle,
            customCycleEndDate = it.customCycleEndDate.takeIf { value == "Custom" },
        )
    }

    fun updateSubscriptionCustomCycleCount(value: Int) = editSchedule {
        it.copy(
            customCycleCount = value.coerceAtLeast(1),
            cycle = it.cycle.copy(every = value.coerceAtLeast(1).toUInt()),
        )
    }

    fun updateSubscriptionCustomCycleUnit(value: String) = editSchedule {
        it.copy(
            customCycleUnit = value,
            cycle = it.cycle.copy(unit = CycleUnit.valueOf(value.uppercase())),
        )
    }

    fun updateSubscriptionCustomCycleEndDate(value: java.time.LocalDate?) = editSchedule {
        it.copy(customCycleEndDate = value)
    }

    fun saveChanges() {
        val snapshot = uiState.value
        val tx = snapshot.editableTransaction ?: return
        write {
            attachmentStore.retainForCore(tx.attachments)
            session.withCore { core ->
                val current = core.ledger().use { it.get(tx.id) }
                val recurring = snapshot.recurring && tx.kind == TransactionKind.EXPENSE
                core.ledger().use {
                    it.edit(
                        tx.id,
                        tx.draft()
                            .copy(subscriptionId = current.subscriptionId.takeIf { recurring }),
                    )
                }
                core.plans().use { plans ->
                    val linked = current.subscriptionId ?: snapshot.editingSubscriptionId
                    if (recurring && linked == null) {
                        plans.subscriptionFromTransaction(
                            tx.id,
                            snapshot.cycle,
                            snapshot.customCycleEndDate?.coreDate(),
                        )
                    } else if (recurring && linked != null) {
                        val sub =
                            plans.subscriptions(java.time.LocalDate.now().coreDate(), true).first {
                                it.id == linked
                            }
                        plans.updateSubscription(
                            linked,
                            SubscriptionDraft(
                                tx.merchant,
                                Money(tx.amount, tx.currency),
                                snapshot.cycle,
                                snapshot.customCycleEndDate?.coreDate(),
                                sub.nextDue,
                                tx.accountId,
                                tx.categoryId,
                                tx.subcategoryId,
                                tx.notes,
                            ),
                        )
                        plans.setSubscriptionHidden(linked, false)
                    } else if (linked != null) {
                        plans.setSubscriptionHidden(linked, true)
                    }
                }
            }
            imported.clear()
            mutableState.update {
                it.copy(isEditMode = false, editableTransaction = null, saveSuccess = true)
            }
        }
    }

    fun duplicateTransaction() {
        val tx = uiState.value.transaction ?: return
        write {
            session.withCore {
                it.ledger().use { ledger -> ledger.add(tx.draft().copy(subscriptionId = null)) }
            }
            mutableState.update { it.copy(duplicateSuccess = true) }
        }
    }

    fun saveAsQuickTemplate() {
        val tx = uiState.value.transaction ?: return
        if (
            tx.kind !in
                listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.INVESTMENT)
        )
            return
        write {
            session.withCore {
                it.plans().use { plans ->
                    plans.createTemplate(
                        QuickTemplateDraft(
                            tx.merchant,
                            tx.kind,
                            tx.merchant,
                            tx.categoryId,
                            tx.subcategoryId,
                            tx.amount,
                            true,
                            tx.accountId,
                            tx.currency,
                            tx.notes,
                        )
                    )
                }
            }
        }
    }

    fun showDeleteDialog() {
        mutableState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        mutableState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteTransaction() {
        val tx = uiState.value.transaction ?: return
        write {
            session.withCore {
                it.ledger().use { ledger ->
                    if (tx.deleted) ledger.purge(listOf(tx.id)) else ledger.delete(listOf(tx.id))
                }
            }
            mutableState.update { it.copy(deleteSuccess = true, showDeleteDialog = false) }
        }
    }

    fun showMarkAsLoanSheet() {
        mutableState.update { it.copy(showMarkAsLoanSheet = true) }
    }

    fun hideMarkAsLoanSheet() {
        mutableState.update { it.copy(showMarkAsLoanSheet = false) }
    }

    fun showUnmarkLoanConfirm() {
        mutableState.update { it.copy(showUnmarkLoanConfirm = true) }
    }

    fun hideUnmarkLoanConfirm() {
        mutableState.update { it.copy(showUnmarkLoanConfirm = false) }
    }

    fun markAsLoan(personId: String, kind: DebtKind) {
        val tx = uiState.value.transaction ?: return
        if (
            tx.kind !in listOf(TransactionKind.LOAN_OUT, TransactionKind.LOAN_IN) ||
                tx.debtId != null ||
                tx.deleted
        )
            return
        write {
            session.withCore {
                it.plans().use { plans -> plans.linkTransaction(tx.id, personId, kind) }
            }
            mutableState.update { it.copy(showMarkAsLoanSheet = false, markAsLoanSuccess = true) }
        }
    }

    fun unmarkAsLoan() {
        if (
            uiState.value.transaction?.kind !in
                listOf(TransactionKind.LOAN_OUT, TransactionKind.LOAN_IN)
        )
            return
        val debt = uiState.value.linkedLendBorrow ?: return
        write {
            session.withCore { it.plans().use { plans -> plans.unlinkDebt(debt.id) } }
            mutableState.update { it.copy(showUnmarkLoanConfirm = false, markAsLoanSuccess = true) }
        }
    }

    fun addPerson(
        name: String,
        phone: String?,
        notes: String?,
        color: String,
        avatar: String?,
        relation: PersonRelation?,
        onSaved: () -> Unit = {},
    ) = write {
        attachmentStore.retainForCore(listOfNotNull(avatar))
        session.withCore {
            it.plans().use { plans ->
                plans.createPerson(PersonDraft(name, phone, notes, color, avatar, relation))
            }
        }
        onSaved()
    }

    fun clearSaveSuccess() {
        mutableState.update { it.copy(saveSuccess = false) }
    }

    fun clearDuplicateSuccess() {
        mutableState.update { it.copy(duplicateSuccess = false) }
    }

    fun clearMarkAsLoanResult() {
        mutableState.update { it.copy(markAsLoanSuccess = false, markAsLoanError = null) }
    }

    fun addAttachment(uri: Uri) {
        if (uiState.value.isSaving) return
        val id = identity
        write {
            val ref = attachmentStore.importFrom(uri)
            if (id == identity && uiState.value.isEditMode) {
                imported += ref
                mutableState.update {
                    it.copy(
                        editableTransaction =
                            it.editableTransaction?.copy(
                                attachments = it.editableTransaction.attachments + ref
                            )
                    )
                }
            } else attachmentStore.discardDraft(listOf(ref))
        }
    }

    fun removeAttachment(ref: String) {
        if (uiState.value.isSaving) return
        edit { it.copy(attachments = it.attachments - ref) }
        if (imported.remove(ref))
            viewModelScope.launch { attachmentStore.discardDraft(listOf(ref)) }
    }

    private fun write(command: suspend () -> Unit) {
        if (uiState.value.isSaving) return
        mutableState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                command()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(errorMessage = coreFailureText(failure)) }
            } finally {
                mutableState.update { it.copy(isSaving = false) }
                if (!uiState.value.deleteSuccess) observeTransaction()
            }
        }
    }
}
