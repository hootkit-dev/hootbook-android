package io.github.hootkit_dev.hootbook

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import io.github.hootkit_dev.hootbook.presentation.navigation.AiAssistant
import io.github.hootkit_dev.hootbook.presentation.navigation.AppLock
import io.github.hootkit_dev.hootbook.presentation.navigation.Home
import io.github.hootkit_dev.hootbook.presentation.navigation.HootbookNavHost
import io.github.hootkit_dev.hootbook.presentation.navigation.OnBoarding
import io.github.hootkit_dev.hootbook.presentation.navigation.Settings
import io.github.hootkit_dev.hootbook.presentation.navigation.AddTransaction
import io.github.hootkit_dev.hootbook.presentation.navigation.TransactionDetail
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.GitHubUpdateHost
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.applock.AppLockViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.appearance.ThemeViewModel

@Composable
fun HootbookApp(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    appLockViewModel: AppLockViewModel = hiltViewModel(),
    editTransactionId: Long? = null,
    onEditComplete: () -> Unit = {},
    addTransactionTab: Int? = null,
    addTransactionType: String? = null,
    addTemplateId: Long? = null,
    onAddComplete: () -> Unit = {},
    openAiAssistant: Boolean = false,
    onAiAssistantOpened: () -> Unit = {}
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val darkTheme = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appLockViewModel.refreshLockState()
            }
            if (event == Lifecycle.Event.ON_STOP) {
                appLockViewModel.onLeftApp()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!themeUiState.isLoaded) return

    val startDestination = remember {
        if (themeUiState.isOnboardingFinished) Home else OnBoarding
    }

    LaunchedEffect(appLockUiState.isLocked, appLockUiState.isLockEnabled) {
        if (appLockUiState.isLocked && appLockUiState.isLockEnabled) {
            // On top of whatever was open, so unlocking returns there
            if (navController.currentDestination?.route != AppLock::class.qualifiedName) {
                navController.navigate(AppLock) { launchSingleTop = true }
            }
        }
    }
    
    // Screens opened from outside (notifications, shortcuts) wait until the app is unlocked
    val lockedNow = appLockUiState.isLocked && appLockUiState.isLockEnabled
    LaunchedEffect(editTransactionId, lockedNow, appLockUiState.isLoaded) {
        if (!appLockUiState.isLoaded || lockedNow) return@LaunchedEffect
        editTransactionId?.let { transactionId ->
            navController.navigate(TransactionDetail(transactionId))
        }
    }

    LaunchedEffect(addTransactionTab, addTransactionType, addTemplateId, lockedNow, appLockUiState.isLoaded) {
        if (!appLockUiState.isLoaded || lockedNow) return@LaunchedEffect
        addTransactionTab?.let { tab ->
            navController.navigate(AddTransaction(initialTab = tab, type = addTransactionType, templateId = addTemplateId))
            onAddComplete()
        }
    }

    // Shared files wait in AiShareInbox until the lock state is known and the app is unlocked
    val locked = appLockUiState.isLocked && appLockUiState.isLockEnabled
    LaunchedEffect(openAiAssistant, locked, appLockUiState.isLoaded) {
        if (openAiAssistant && appLockUiState.isLoaded && !locked) {
            navController.navigate(AiAssistant) { launchSingleTop = true }
            onAiAssistantOpened()
        }
    }

    HootbookTheme(
        darkTheme = darkTheme,
        themeStyle = themeUiState.themeStyle,
        // The visible ThemeStyle choice is authoritative; old backups can contain a
        // contradictory legacy dynamic-color flag. HootbookTheme defaults to enabled.
        isAmoledMode = themeUiState.isAmoledMode,
        accentColor = themeUiState.accentColor,
        appFont = themeUiState.appFont,
        blurEffects = themeUiState.blurEffects
    ) {
        Box {
            HootbookNavHost(
                navController = navController,
                startDestination = startDestination,
                onEditComplete = onEditComplete
            )
            GitHubUpdateHost()
        }
    }
}
