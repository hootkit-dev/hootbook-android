package io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding

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
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.backup.*
import io.github.hootkit_dev.hootbook.presentation.data.backup.*
import io.github.hootkit_dev.hootbook.presentation.ui.features.profile.persistProfileImage
import java.io.File

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val preferences: CoreUiPreferencesStore,
    private val assets: CorePlatformAssets,
    private val platform: BackupPlatformApplier,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnBoardingUiState())
    val uiState = _uiState.asStateFlow()
    private var summaryQuery: Job? = null
    private var summaryGeneration = 0L
    private var currencyDirty = false
    private var accountReceipt: Pair<String, String>? = null
    private val workflow = File(context.noBackupFilesDir, "hootkit-core/backup-ui").let { root ->
        val files = CoreBackupFiles(root)
        BackupWorkflow(SessionBackupAccess(session), files, BackupOperationJournal(files), root) { platform.apply(it, onboardingFinished = false) }
    }
    init {
        loadPreferences()
        viewModelScope.launch { session.observe(changes, setOf(Table.SETTINGS)) { core ->
            coreQuery { core.settings().use { it.mainCurrency() } }
        }.collect { result -> result.fold(onSuccess = { currency ->
            if (!currencyDirty) _uiState.update { it.copy(selectedCurrency = currency) }
        }, onFailure = { _uiState.update { it.copy(errorMessage = context.getString(R.string.onboarding_operation_failed)) } }) } }
    }
    private fun loadPreferences() = runOperation {
        val p = preferences.ready()
        val institutions = session.withCore { core -> core.catalog().use { it.all() } }
        val currency = session.withCore { core -> core.settings().use { it.mainCurrency() } }
        _uiState.update { it.copy(institutions = institutions, selectedCurrency = currency, profileState = it.profileState.copy(
            editedUserName = p.userName, editedProfileImageUri = assets.resolve(p.avatar)?.let(Uri::fromFile),
            editedProfileBackgroundColor = Color(p.profileBackground), editedBannerImageUri = assets.resolve(p.banner)?.let(Uri::fromFile))) }
    }
    fun nextStep() {
        if (_uiState.value.isLoading) return
        _uiState.update { state ->
            when (state.step) {
                OnboardingStep.WELCOME -> state.copy(step = OnboardingStep.ACCOUNT, accountsFromSync = false)
                OnboardingStep.ACCOUNT -> if (state.hasSavedAccount) state.copy(step = OnboardingStep.PROFILE) else state
                OnboardingStep.PROFILE -> state.copy(step = OnboardingStep.NOTIFICATIONS)
                // Sync moves on with continueAfterSync
                OnboardingStep.SYNC, OnboardingStep.NOTIFICATIONS -> state
            }
        }
    }

    fun previousStep() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(step = it.previousStep) }
    }

    /** From the welcome step: turn on sync instead of adding an account. */
    fun openSync() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(step = OnboardingStep.SYNC) }
    }

    /** Counts what this device holds now that sync is on, for the sync step's summary. */
    fun refreshSyncSummary() {
        val mine = ++summaryGeneration
        summaryQuery?.cancel()
        summaryQuery = viewModelScope.launch {
            session.observe(changes, setOf(Table.ACCOUNTS, Table.TRANSACTIONS)) { core -> coreQuery {
                val accounts = core.accounts().use { it.overview().accounts.size }
                val transactions = core.ledger().use { it.list(allTransactions(), PageRequest(0u, 1u)).total.toInt() }
                accounts to transactions
            } }.collect { result ->
                if (mine != summaryGeneration) return@collect
                result.fold(onSuccess = { (accounts, transactions) -> _uiState.update { it.copy(syncedAccounts = accounts, syncedTransactions = transactions) } },
                    onFailure = { _uiState.update { it.copy(errorMessage = context.getString(R.string.onboarding_operation_failed)) } })
            }
        }
    }

    /**
     * Sync is on. With accounts from the cloud the chosen main currency is saved (preferences do
     * not sync) and the account step is skipped; see [OnBoardingUiState.afterSync].
     */
    fun continueAfterSync() = runOperation {
        val accounts = session.withCore { core -> core.accounts().use { it.overview().accounts.size } }
        if (accounts > 0) session.withCore { core -> core.settings().use { it.setMainCurrency(_uiState.value.selectedCurrency) } }
        _uiState.update { it.afterSync(accounts) }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun onNameChange(name: String) { _uiState.update { it.copy(profileState = it.profileState.copy(editedUserName = name, hasChanges = true)) } }
    fun onProfileImageChange(uri: Uri?) { _uiState.update { it.copy(profileState = it.profileState.copy(editedProfileImageUri = uri, hasChanges = true)) } }
    fun onBackgroundColorChange(color: Color) { _uiState.update { it.copy(profileState = it.profileState.copy(editedProfileBackgroundColor = color, hasChanges = true)) } }
    fun toggleCurrencyBottomSheet(show: Boolean) { _uiState.update { it.copy(showCurrencyBottomSheet = show) } }
    fun updateSelectedCurrency(currency: String) { currencyDirty = true; _uiState.update { it.copy(selectedCurrency = currency) } }
    fun updateManualAccountName(name: String) { _uiState.update { it.copy(manualAccountName = name, institutionKey = null) } }
    fun selectInstitution(institution: Institution, name: String) { _uiState.update { it.copy(manualAccountName = name, institutionKey = institution.key) } }
    fun updateManualAccountBalance(balance: String) {
        if (balance.isEmpty() || balance.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(manualAccountBalance = balance) }
        }
    }
    fun updateManualAccountLast4(last4: String) {
        if (last4.length <= 4) _uiState.update { it.copy(manualAccountLast4 = last4) }
    }


    private fun allTransactions() = TransactionFilter(null, null, emptyList(), emptyList(), emptyList(), null, null,
        DeletedFilter.EXCLUDE, SortOrder.NEWEST_FIRST)
    private fun runOperation(block: suspend () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _uiState.update { it.copy(errorMessage = context.getString(R.string.onboarding_operation_failed)) } }
            finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }
    fun saveManualAccount() {
        val draft = uiState.value
        if (draft.hasSavedAccount) { nextStep(); return }
        if (!draft.canSaveAccount) return
        runOperation {
            val id = accountReceipt?.first ?: session.withCore { core -> core.accounts().use { it.create(NewAccount(
                name = draft.manualAccountName.trim(), last4 = draft.manualAccountLast4, kind = AccountKind.BANK,
                mainCurrency = draft.selectedCurrency, openingBalance = draft.manualAccountBalance.toBigDecimal(),
                openingAt = null, creditLimit = null, statementDay = null, dueDay = null, institutionKey = draft.institutionKey,
                icon = null, color = null, extraCurrencies = emptyList())) } }
            // Preserve the receipt immediately: a later settings failure must not create a second account.
            accountReceipt = id to (accountReceipt?.second ?: draft.selectedCurrency)
            session.withCore { core -> core.settings().use {
                it.setMainCurrency(requireNotNull(accountReceipt).second); it.setDefaultAccount(id)
            } }
            _uiState.update { it.copy(hasSavedAccount = true, step = OnboardingStep.PROFILE, accountsFromSync = false) }
        }
    }
    fun saveProfile() {
        val draft = uiState.value.profileState
        runOperation {
            val avatar = persistProfileImage(context, assets, draft.editedProfileImageUri)
            val banner = persistProfileImage(context, assets, draft.editedBannerImageUri)
            preferences.update { it.copy(userName = draft.editedUserName.trim(), avatar = avatar, banner = banner,
                showBanner = banner != null, profileBackground = draft.editedProfileBackgroundColor.toArgb()) }
            _uiState.update { it.copy(step = OnboardingStep.NOTIFICATIONS) }
        }
    }
    fun importBackup(uri: Uri) = runOperation {
        io.github.hootkit_dev.hootbook.core.CoreUriBoundary.requireExternalDocument(context, uri)
        val source = workflow.files.stage(context.contentResolver, uri)
        workflow.inspect(source, true, null)
        workflow.restore(source, true, ImportMode.MERGE, null, false)
        completeOnboarding()
    }
    fun finishOnboarding() = runOperation { completeOnboarding() }
    private suspend fun completeOnboarding() {
        preferences.update { it.copy(onboardingFinished = true) }
        _uiState.update { it.copy(onboardingFinished = true) }
    }
}
