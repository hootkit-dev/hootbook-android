package io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding

import org.junit.Assert.*
import org.junit.Test

class OnboardingStateTest {
    @Test fun `new install offers welcome without a required account`() {
        val state = OnBoardingUiState()
        assertEquals(OnboardingStep.WELCOME, state.step)
        assertFalse(state.hasSavedAccount)
        assertFalse(state.onboardingFinished)
        assertFalse(state.isLoading)
    }
    @Test fun `manual entry rejects incomplete or malformed amounts`() {
        val state = OnBoardingUiState(manualAccountName = "Bank", manualAccountLast4 = "1234")
        assertFalse(state.canSaveAccount)
        assertFalse(state.copy(manualAccountBalance = ".").canSaveAccount)
        assertFalse(state.copy(manualAccountBalance = "12.50", manualAccountLast4 = "12").canSaveAccount)
        assertTrue(state.copy(manualAccountBalance = "0").canSaveAccount)
        assertTrue(state.copy(manualAccountBalance = "12.50").canSaveAccount)
    }
    @Test fun `onboarding has no tracking or scan step`() {
        assertEquals(listOf("WELCOME", "ACCOUNT", "SYNC", "PROFILE", "NOTIFICATIONS"), OnboardingStep.entries.map { it.name })
    }
    @Test fun `sync takes the account step's place in the progress`() {
        assertEquals(OnboardingStep.ACCOUNT.number, OnboardingStep.SYNC.number)
        assertEquals((1..OnboardingStep.COUNT).toList(), OnboardingStep.entries.map { it.number }.distinct())
    }
    @Test fun `accounts from sync skip the account step both ways`() {
        val synced = OnBoardingUiState(step = OnboardingStep.SYNC).afterSync(accounts = 3)
        assertEquals(OnboardingStep.PROFILE, synced.step)
        assertEquals(OnboardingStep.SYNC, synced.previousStep)
        assertEquals(OnboardingStep.WELCOME, synced.copy(step = OnboardingStep.SYNC).previousStep)
    }
    @Test fun `an empty cloud still asks for the first account`() {
        val synced = OnBoardingUiState(step = OnboardingStep.SYNC, accountsFromSync = true).afterSync(accounts = 0)
        assertEquals(OnboardingStep.ACCOUNT, synced.step)
        assertFalse(synced.accountsFromSync)
        assertEquals(OnboardingStep.ACCOUNT, synced.copy(step = OnboardingStep.PROFILE).previousStep)
    }
}
