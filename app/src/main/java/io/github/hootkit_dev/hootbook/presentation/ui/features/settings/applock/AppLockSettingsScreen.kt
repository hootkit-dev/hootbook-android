package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.applock

import androidx.compose.runtime.Composable
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.dataprivacy.DataPrivacyScreen

/** The original security controls live in Data & privacy. */
@Composable
fun AppLockSettingsScreen(onNavigateBack: () -> Unit) {
    DataPrivacyScreen(onNavigateBack = onNavigateBack, securityOnly = true, blurEffects = false)
}
