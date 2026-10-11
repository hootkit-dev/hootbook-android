package io.github.hootkit_dev.hootbook.presentation.core.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.*
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.presentation.core.applock.CoreAppLockGate
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme

@Composable fun CoreAppRoot(bootstrap: CoreAppBootstrap, session: CoreSession, capabilities: CoreCapabilities,
    prefs: CoreUiPreferencesStore, lock: CoreAppLockController, ingress: CoreIntentIngress) {
    val boot by bootstrap.state.collectAsStateWithLifecycle(); val preference by prefs.state.collectAsStateWithLifecycle()
    HootbookTheme(darkTheme = preference.darkTheme ?: isSystemInDarkTheme(), themeStyle = preference.themeStyle,
        dynamicColor = preference.dynamicColor, isAmoledMode = preference.amoled, accentColor = preference.accent,
        appFont = preference.font, blurEffects = preference.blur) {
        when (boot) {
            CoreBootstrapState.PREFERENCES_FAILED -> CorePreferencesRecovery(prefs, bootstrap)
            CoreBootstrapState.CORE_FAILED -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Text(stringResource(R.string.core_host_open_failed)); Button(onClick = bootstrap::start) { Text(stringResource(R.string.core_shell_retry)) }
            }
            CoreBootstrapState.READY -> CoreAppLockGate(lock) { CoreNavHost(session, capabilities, prefs, ingress) }
            else -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
