package io.github.hootkit_dev.hootbook.presentation.ui.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import io.github.hootkit_dev.hootbook.core.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.format.toCoreDateTime
import io.github.hootkit_dev.hootbook.presentation.ui.features.lendborrow.planToday
import java.time.LocalDate

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val preferences: CoreUiPreferencesStore,
    val assets: CorePlatformAssets,
    val attachmentStore: CoreAttachmentStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileScreenState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch { preferences.state.collect { p -> mutable.update { it.copy(
            userName = p.userName,
            profileImageUri = assets.resolve(p.avatar)?.let(Uri::fromFile),
            profileBackgroundColor = Color(p.profileBackground),
            bannerImageUri = assets.resolve(p.banner)?.let(Uri::fromFile)) } } }
        command { preferences.ready() }
        viewModelScope.launch { session.observe(changes, setOf(Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES,
            Table.BALANCE_ANCHORS, Table.TRANSACTIONS, Table.EXCHANGE_RATES, Table.SETTINGS,
            Table.SUBSCRIPTIONS, Table.PEOPLE, Table.DEBTS)) { core -> coreQuery {
                val month = LocalDate.now().withDayOfMonth(1)
                val filter = TransactionFilter(null, null, emptyList(), emptyList(), emptyList(), null, null,
                    DeletedFilter.EXCLUDE, SortOrder.NEWEST_FIRST)
                val customCurrencies = core.currency().use { it.customCurrencies() }
                val overview = core.accounts().use { it.overview() }
                val summary = core.ledger().use { it.summary(filter.copy(from = month.atStartOfDay().toCoreDateTime(),
                    to = month.plusMonths(1).atStartOfDay().toCoreDateTime())) }
                val count = core.ledger().use { it.list(filter, PageRequest(0u, 1u)).total }
                core.plans().use { plans -> ProfileScreenState(overview = overview, monthly = summary, customCurrencies = customCurrencies,
                    totalTransactions = count, subscriptions = plans.subscriptionSummary(planToday()),
                    contacts = plans.people(false, planToday()), isLoading = false) }
            } }.collect { result -> result.fold(onSuccess = { snapshot -> mutable.update { it.copy(
                overview = snapshot.overview, monthly = snapshot.monthly, subscriptions = snapshot.subscriptions, customCurrencies = snapshot.customCurrencies,
                totalTransactions = snapshot.totalTransactions, contacts = snapshot.contacts, isLoading = false, error = null) } },
                onFailure = { failure -> mutable.update { it.copy(isLoading = false, error = coreFailureText(failure)) } }) } }
    }
    fun toggleEditSheet() {
        if (state.value.saving) return
        mutable.update { it.copy(isEditSheetOpen = !it.isEditSheetOpen, editState = EditProfileState(
            it.userName, it.profileImageUri, it.profileBackgroundColor, it.bannerImageUri)) }
    }
    fun dismissEditSheet() { if (!state.value.saving) mutable.update { it.copy(isEditSheetOpen = false) } }
    fun showAddPersonSheet(show: Boolean) { if (!state.value.saving) mutable.update { it.copy(isAddPersonSheetOpen = show) } }
    fun updateEditUserName(value: String) = edit { it.copy(editedUserName = value, hasChanges = true) }
    fun updateEditProfileImage(value: Uri?) = edit { it.copy(editedProfileImageUri = value, hasChanges = true) }
    fun updateEditProfileBackgroundColor(value: Color) = edit { it.copy(editedProfileBackgroundColor = value, hasChanges = true) }
    fun updateEditBannerImage(value: Uri?) = edit { it.copy(editedBannerImageUri = value, hasChanges = true) }
    private fun edit(transform: (EditProfileState) -> EditProfileState) {
        if (!state.value.saving) mutable.update { it.copy(editState = transform(it.editState)) }
    }
    fun saveProfileChanges() {
        val draft = state.value.editState
        command {
            val avatar = persistProfileImage(context, assets, draft.editedProfileImageUri)
            val banner = persistProfileImage(context, assets, draft.editedBannerImageUri)
            preferences.update { it.copy(userName = draft.editedUserName.trim(), avatar = avatar,
                banner = banner, showBanner = banner != null, profileBackground = draft.editedProfileBackgroundColor.toArgb()) }
            mutable.update { it.copy(isEditSheetOpen = false) }
        }
    }
    fun addPerson(name: String, phone: String?, notes: String?, color: String, avatar: String?, relation: PersonRelation?) = command {
        attachmentStore.retainForCore(listOfNotNull(avatar))
        session.withCore { core -> core.plans().use { it.createPerson(PersonDraft(name, phone, notes, color, avatar, relation)) } }
        mutable.update { it.copy(isAddPersonSheetOpen = false) }
    }
    private fun command(block: suspend () -> Unit) {
        if (state.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = coreFailureText(failure)) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
