package io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding

import io.github.hootkit_dev.hootbook.presentation.ui.features.profile.EditProfileState

/**
 * [number] is the step's place in the progress bar. [SYNC] (turning on sync from the welcome
 * step) takes the account step's place: a ledger from the cloud needs no first account.
 */
enum class OnboardingStep(val number: Int) {
    WELCOME(1), ACCOUNT(2), SYNC(2), PROFILE(3), NOTIFICATIONS(4);

    companion object {
        const val COUNT = 4
    }
}

data class OnBoardingUiState(
    val institutions: List<dev.hootkit.core.Institution> = emptyList(),
    val institutionKey: String? = null,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val profileState: EditProfileState = EditProfileState(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val onboardingFinished: Boolean = false,
    val hasSavedAccount: Boolean = false,
    val manualAccountName: String = "",
    val manualAccountBalance: String = "",
    val manualAccountLast4: String = "",
    val selectedCurrency: String = "CNY",
    val showCurrencyBottomSheet: Boolean = false,
    /** Sync brought accounts, so the profile step follows sync and the account step is skipped */
    val accountsFromSync: Boolean = false,
    /** What this device holds once sync is on: accounts and transactions */
    val syncedAccounts: Int = 0,
    val syncedTransactions: Int = 0
) {
    val canSaveAccount: Boolean
        get() = manualAccountName.isNotBlank() &&
            manualAccountBalance.toBigDecimalOrNull() != null &&
            manualAccountLast4.length == 4

    /** Where back goes: the profile step returns to whichever step led to it. */
    val previousStep: OnboardingStep
        get() = when (step) {
            OnboardingStep.WELCOME, OnboardingStep.ACCOUNT, OnboardingStep.SYNC -> OnboardingStep.WELCOME
            OnboardingStep.PROFILE -> if (accountsFromSync) OnboardingStep.SYNC else OnboardingStep.ACCOUNT
            OnboardingStep.NOTIFICATIONS -> OnboardingStep.PROFILE
        }

    /**
     * The step after sync was turned on, with [accounts] on the device: accounts from the cloud
     * skip the account step; with none (the first device), the account step follows.
     */
    fun afterSync(accounts: Int): OnBoardingUiState =
        if (accounts > 0) copy(step = OnboardingStep.PROFILE, accountsFromSync = true)
        else copy(step = OnboardingStep.ACCOUNT, accountsFromSync = false)
}
