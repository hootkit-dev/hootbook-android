package io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.backup.BackupImporter
import io.github.hootkit_dev.hootbook.data.backup.ImportResult
import io.github.hootkit_dev.hootbook.data.backup.ImportStrategy
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.AccountBalanceRepository
import io.github.hootkit_dev.hootbook.data.repository.TransactionRepository
import io.github.hootkit_dev.hootbook.presentation.common.icons.InstitutionCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class OnBoardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val backupImporter: BackupImporter
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnBoardingUiState())
    val uiState: StateFlow<OnBoardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            _uiState.update {
                it.copy(selectedCurrency = prefs.baseCurrency, profileState = it.profileState.copy(
                    editedUserName = prefs.userName,
                    editedProfileImageUri = prefs.profileImageUri?.let(Uri::parse),
                    editedProfileBackgroundColor = Color(prefs.profileBackgroundColor),
                    editedBannerImageUri = prefs.bannerImageUri?.let(Uri::parse)
                ))
            }
        }
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
        viewModelScope.launch {
            val accounts = accountBalanceRepository.getAccountCount().first()
            val transactions = transactionRepository.countVisibleTransactions()
            _uiState.update { it.copy(syncedAccounts = accounts, syncedTransactions = transactions) }
        }
    }

    /**
     * Sync is on. With accounts from the cloud the chosen main currency is saved (preferences do
     * not sync) and the account step is skipped; see [OnBoardingUiState.afterSync].
     */
    fun continueAfterSync() = runOperation {
        val accounts = accountBalanceRepository.getAccountCount().first()
        if (accounts > 0) userPreferencesRepository.updateBaseCurrency(_uiState.value.selectedCurrency)
        _uiState.update { it.afterSync(accounts) }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun onNameChange(name: String) { _uiState.update { it.copy(profileState = it.profileState.copy(editedUserName = name, hasChanges = true)) } }
    fun onProfileImageChange(uri: Uri?) { _uiState.update { it.copy(profileState = it.profileState.copy(editedProfileImageUri = uri, hasChanges = true)) } }
    fun onBackgroundColorChange(color: Color) { _uiState.update { it.copy(profileState = it.profileState.copy(editedProfileBackgroundColor = color, hasChanges = true)) } }
    fun toggleCurrencyBottomSheet(show: Boolean) { _uiState.update { it.copy(showCurrencyBottomSheet = show) } }
    fun updateSelectedCurrency(currency: String) { _uiState.update { it.copy(selectedCurrency = currency) } }
    fun updateManualAccountName(name: String) { _uiState.update { it.copy(manualAccountName = name) } }
    fun updateManualAccountBalance(balance: String) {
        if (balance.isEmpty() || balance.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(manualAccountBalance = balance) }
        }
    }
    fun updateManualAccountLast4(last4: String) {
        if (last4.length <= 4) _uiState.update { it.copy(manualAccountLast4 = last4) }
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = context.getString(R.string.onboarding_operation_failed)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveManualAccount() {
        val state = _uiState.value
        if (state.hasSavedAccount) { nextStep(); return }
        if (!state.canSaveAccount) return
        runOperation {
            val institution = InstitutionCatalog.find(state.manualAccountName.trim())
            accountBalanceRepository.insertBalance(AccountBalanceEntity(
                bankName = state.manualAccountName.trim(), accountLast4 = state.manualAccountLast4,
                balance = state.manualAccountBalance.toBigDecimal(), timestamp = LocalDateTime.now(),
                sourceType = "MANUAL", currency = state.selectedCurrency,
                iconResId = institution?.iconResId ?: R.drawable.type_finance_dollar_banknote,
                iconName = institution?.iconName ?: "type_finance_dollar_banknote",
                color = institution?.color ?: "#33B5E5"
            ))
            context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE).edit {
                putString("main_account", "${state.manualAccountName.trim()}_${state.manualAccountLast4}")
            }
            userPreferencesRepository.updateBaseCurrency(state.selectedCurrency)
            _uiState.update { it.copy(hasSavedAccount = true, step = OnboardingStep.PROFILE, accountsFromSync = false) }
        }
    }

    fun saveProfile() {
        val profile = _uiState.value.profileState
        runOperation {
            userPreferencesRepository.updateUserName(profile.editedUserName.trim())
            userPreferencesRepository.updateProfileImageUri(profile.editedProfileImageUri?.toString())
            userPreferencesRepository.updateProfileBackgroundColor(profile.editedProfileBackgroundColor.toArgb())
            userPreferencesRepository.updateBannerImageUri(profile.editedBannerImageUri?.toString())
            _uiState.update { it.copy(step = OnboardingStep.NOTIFICATIONS) }
        }
    }

    fun importBackup(uri: Uri) = runOperation {
        // MERGE keeps any existing records; both ZIP and legacy JSON use the established importer.
        // Defer the completion flag until all restore operations have succeeded.
        when (backupImporter.importBackup(uri, ImportStrategy.MERGE, restoreOnboardingCompletion = false)) {
            is ImportResult.Success -> completeOnboarding()
            is ImportResult.Error -> _uiState.update { it.copy(errorMessage = context.getString(R.string.onboarding_import_failed)) }
        }
    }

    fun finishOnboarding() = runOperation { completeOnboarding() }

    private suspend fun completeOnboarding() {
        userPreferencesRepository.markScanTutorialShown()
        _uiState.update { it.copy(onboardingFinished = true) }
    }
}
