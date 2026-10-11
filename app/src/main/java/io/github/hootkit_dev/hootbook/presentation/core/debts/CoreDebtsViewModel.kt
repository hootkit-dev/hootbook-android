package io.github.hootkit_dev.hootbook.presentation.core.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.presentation.core.accounts.coreFailureText
import javax.inject.Inject
import android.net.Uri
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal val DEBT_TABLES = setOf(Table.PEOPLE, Table.DEBTS, Table.TRANSACTIONS, Table.ACCOUNTS,
    Table.ACCOUNT_CURRENCIES, Table.EXCHANGE_RATES, Table.SETTINGS)

data class CoreDebtsUiState(
    val people: List<Person> = emptyList(), val summary: DebtSummary? = null,
    val accounts: List<Account> = emptyList(), val mainCurrency: String = "CNY",
    val includeArchived: Boolean = false, val selectedId: String? = null, val person: Person? = null,
    val debts: List<DebtEntry> = emptyList(), val personForm: CorePersonForm? = null,
    val debtForm: CoreDebtForm? = null, val settlement: CoreSettlementForm? = null,
    val loading: Boolean = true, val saving: Boolean = false, val navigationBlocked: Boolean = false,
    val error: String? = null, val commandRevision: Long = 0,
    val assetLoading:Boolean=false,val platformError:Int?=null,
)

@HiltViewModel
class CoreDebtsViewModel private constructor(
    private val session: CoreSession?, changes: ChangeBus,
    private val testPlans: (suspend () -> PlansInterface)?,
    private val testAccounts: (suspend () -> List<Account>)?,
    private val testCurrency: (suspend () -> String)?,
    val attachmentStore:CoreAttachmentStore?,
) : ViewModel() {
    @Inject constructor(session: CoreSession, changes: ChangeBus, attachments:CoreAttachmentStore) : this(session, changes, null, null, null,attachments)
    internal constructor(changes: ChangeBus, plans: suspend () -> PlansInterface,
        accounts: suspend () -> List<Account> = { emptyList() }, currency: suspend () -> String = { "CNY" }, attachments:CoreAttachmentStore?=null) : this(null, changes, plans, accounts, currency,attachments)

    private suspend fun <T> withPlans(block: suspend (PlansInterface) -> T): T {
        if (testPlans == null) return session!!.awaitCore().plans().use { block(it) }
        val api = testPlans.invoke()
        // Test seams exercise the same generated Disposable.use finally when present.
        return if (api is Disposable) api.use { block(api) } else block(api)
    }
    private val mutable = MutableStateFlow(CoreDebtsUiState())
    val state = mutable.asStateFlow()
    private var queryJob: Job? = null
    private var version = 0L
    private var initialized = false
    private var assetRevision=0L

    init { viewModelScope.launch { changes.observe(DEBT_TABLES).collect { refresh() } } }
    fun initialize(personId: String?) {
        if (initialized) return
        initialized = true
        if (personId != null) select(personId)
    }
    fun refresh() {
        val request = ++version
        queryJob?.cancel()
        val before = mutable.value
        mutable.update { it.copy(loading = true) }
        queryJob = viewModelScope.launch {
            try {
                val accounts = testAccounts?.invoke() ?: session!!.awaitCore().accounts().use { it.overview().accounts }
                val currency = testCurrency?.invoke() ?: session!!.awaitCore().settings().use { it.mainCurrency() }
                val today = java.time.LocalDate.now().toDebtDate()
                val result = withPlans { api ->
                    val people = api.people(before.includeArchived, today)
                    val summary = api.debtSummary(today)
                    // Query selected person independently: archived people may not be in current list.
                    val debts = before.selectedId?.let { api.debts(it) }.orEmpty()
                    val person = before.selectedId?.let { id ->
                        try { api.person(id, today) } catch (missing: CoreException.NotFound) {
                            if (missing.entity == Entity.PERSON && debts.isNotEmpty()) null else throw missing
                        }
                    }
                    CoreDebtsUiState(people = people, summary = summary, person = person, debts = debts)
                }
                if (request == version) mutable.update { it.copy(loading = false, people = result.people, summary = result.summary,
                    accounts = accounts, mainCurrency = currency, person = result.person, debts = result.debts) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) {
                if (request == version) mutable.update {
                    // Deleted on another device: do not keep presenting a stale person detail.
                    if (failure is CoreException.NotFound && failure.entity == Entity.PERSON)
                        it.copy(loading = false, selectedId = null, person = null, debts = emptyList(), error = coreFailureText(failure))
                    else it.copy(loading = false, error = coreFailureText(failure))
                }
            }
        }
    }
    private fun blocked(): Boolean {
        if (!mutable.value.saving&&!mutable.value.assetLoading) return false
        mutable.update { it.copy(navigationBlocked = true) }
        return true
    }
    fun select(id: String?) {
        if (blocked()) return
        mutable.update { it.copy(selectedId = id, person = null, debts = emptyList(), personForm = null, debtForm = null, settlement = null) }
        refresh()
    }
    fun includeArchived(value: Boolean) { if (!blocked()) { mutable.update { it.copy(includeArchived = value) }; refresh() } }
    fun editPerson(person: Person? = null) {
        if (!blocked()) {++assetRevision;mutable.update { it.copy(personForm = person?.let(CorePersonForm::from) ?: CorePersonForm(), debtForm = null, settlement = null) }}
    }
    fun personChanged(form: CorePersonForm) { if (!mutable.value.saving) mutable.update { it.copy(personForm = form) } }
    fun editDebt(entry: DebtEntry? = null) {
        if (blocked()) return
        val personId = entry?.personId ?: mutable.value.selectedId ?: return
        mutable.update { it.copy(debtForm = entry?.let(CoreDebtForm::from) ?: CoreDebtForm(personId = personId, currency = it.mainCurrency), personForm = null, settlement = null) }
    }
    fun debtChanged(form: CoreDebtForm) { if (!mutable.value.saving) mutable.update { it.copy(debtForm = form) } }
    fun settle(title: String) {
        if (blocked()) return
        val personId = mutable.value.selectedId ?: return
        mutable.update { it.copy(settlement = CoreSettlementForm(personId, currency = it.mainCurrency, title = title), personForm = null, debtForm = null) }
    }
    fun settlementChanged(form: CoreSettlementForm) { if (!mutable.value.saving) mutable.update { it.copy(settlement = form) } }
    fun dismissEditor() {
        if(blocked())return
        ++assetRevision
        val abandoned=listOfNotNull(mutable.value.personForm?.avatar)+mutable.value.debtForm?.attachments.orEmpty()
        mutable.update { it.copy(personForm = null, debtForm = null, settlement = null) }
        viewModelScope.launch{attachmentStore?.discardDraft(abandoned)}
    }
    fun selectedContact(name:String?,phone:String?) {if(!mutable.value.saving)mutable.update{it.copy(personForm=it.personForm?.let{form->form.copy(name=name?:form.name,phone=phone?:form.phone)})}}
    fun platformFailure(){mutable.update{it.copy(platformError=R.string.core_detail_contact_failed)}}
    fun avatar(uri:Uri?){if(uri==null||mutable.value.personForm==null)return;importAsset(uri,true)}
    fun attachment(uri:Uri?){if(uri==null||mutable.value.debtForm==null)return;importAsset(uri,false)}
    private fun importAsset(uri:Uri,avatar:Boolean){
        val store=attachmentStore?:return;if(mutable.value.saving||mutable.value.assetLoading)return
        val mine=++assetRevision;mutable.update{it.copy(assetLoading=true,platformError=null)}
        viewModelScope.launch{try{val ref=store.importFrom(uri)
            if(mine==assetRevision)mutable.update{it.copy(personForm=if(avatar)it.personForm?.copy(avatar=ref)else it.personForm,
                debtForm=if(!avatar)it.debtForm?.let{form->form.copy(attachments=form.attachments+ref)}else it.debtForm)}
            else store.discardDraft(listOf(ref))
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(mine==assetRevision)platformFailure()}
        finally{if(mine==assetRevision)mutable.update{it.copy(assetLoading=false)}}}
    }
    fun removeAvatar(){if(blocked())return;val ref=mutable.value.personForm?.avatar;mutable.update{it.copy(personForm=it.personForm?.copy(avatar=null))};if(ref!=null)viewModelScope.launch{attachmentStore?.discardDraft(listOf(ref))}}
    fun removeAttachment(ref:String){if(blocked())return;mutable.update{it.copy(debtForm=it.debtForm?.let{form->form.copy(attachments=form.attachments-ref)})};viewModelScope.launch{attachmentStore?.discardDraft(listOf(ref))}}
    /** Consume editor/details before allowing host navigation. Saving always consumes back. */
    fun back(): Boolean {
        if (blocked()) return true
        if (mutable.value.personForm != null || mutable.value.debtForm != null || mutable.value.settlement != null) { dismissEditor(); return true }
        if (mutable.value.selectedId != null) { select(null); return true }
        return false
    }
    fun transactionTarget(entry: DebtEntry): String? = if (blocked()) null else entry.transactionId
    fun clearError() { mutable.update { it.copy(error = null,platformError=null) } }
    fun savePerson() {
        val form = mutable.value.personForm ?: return
        command { api ->
            attachmentStore?.retainForCore(listOfNotNull(form.avatar))
            if (form.id == null) {
                val id = api.createPerson(form.toDraft()); mutable.update { it.copy(selectedId = id) }
            } else api.updatePerson(form.id, form.toDraft())
            mutable.update { it.copy(personForm = null) }
        }
    }
    fun archive(person: Person) = command { it.setPersonArchived(person.id, !person.archived) }
    fun deletePerson(person: Person) = command {
        it.deletePerson(person.id)
        mutable.update { state -> state.copy(selectedId = null, person = null, debts = emptyList()) }
    }
    fun saveDebt() {
        val form = mutable.value.debtForm ?: return
        command {
            attachmentStore?.retainForCore(form.attachments)
            if (form.id == null) it.addDebt(form.toDraft()) else it.updateDebt(form.id, form.toDraft())
            mutable.update { state -> state.copy(selectedId = form.personId, debtForm = null) }
        }
    }
    fun saveSettlement() {
        val form = mutable.value.settlement ?: return
        command { it.settle(form.personId, form.toDraft()); mutable.update { state -> state.copy(settlement = null) } }
    }
    // Each item uses Core's own delete command. A failure stops the sequence; refresh in command()
    // exposes remaining rows, so the UI never implies that these separate writes are atomic.
    fun deletePeople(ids: Set<String>) {
        val existing = mutable.value.people.map { it.id }.toSet().intersect(ids)
        if (existing.isNotEmpty()) command { api -> existing.forEach { api.deletePerson(it) } }
    }
    fun deleteDebts(ids: Set<String>) {
        val existing = mutable.value.debts.map { it.id }.toSet().intersect(ids)
        if (existing.isNotEmpty()) command { api -> existing.forEach { api.deleteDebt(it) } }
    }
    fun deleteDebt(entry: DebtEntry) = command { it.deleteDebt(entry.id) }
    fun unlinkDebt(entry: DebtEntry) = command { it.unlinkDebt(entry.id) }
    fun linkTransaction(transactionId: String, personId: String, kind: DebtKind?) = command {
        it.linkTransaction(transactionId, personId, kind)
        mutable.update { state -> state.copy(selectedId = personId) }
    }
    private fun command(block: suspend (PlansInterface) -> Unit) {
        if (mutable.value.saving||mutable.value.assetLoading) return
        // Invalidate in-flight pre-write snapshot. Queries never own/cancel a write job.
        ++version; queryJob?.cancel()
        mutable.update { it.copy(saving = true, error = null, navigationBlocked = false) }
        viewModelScope.launch {
            try {
                withPlans(block)
                mutable.update { it.copy(commandRevision = it.commandRevision + 1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally {
                mutable.update { it.copy(saving = false, navigationBlocked = false) }
                refresh()
            }
        }
    }
}
