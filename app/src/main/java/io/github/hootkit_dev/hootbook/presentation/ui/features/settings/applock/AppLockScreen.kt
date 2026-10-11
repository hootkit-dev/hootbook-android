@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.applock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Padlock
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    modifier: Modifier = Modifier,
    onUnlocked: () -> Unit,
    appLockViewModel: AppLockViewModel = hiltViewModel(),
) {
    val uiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val context = io.github.hootkit_dev.hootbook.presentation.core.applock.coreFragmentActivity(LocalContext.current)

    // Prevent back navigation when app is locked
    BackHandler(enabled = true) {
        // Do nothing - prevent back navigation
        // User must authenticate to proceed
    }

    // Auto-trigger authentication when screen is shown
    LaunchedEffect(uiState.isLoaded, uiState.canUseBiometric) {
        if (uiState.canUseBiometric && context != null) {
            triggerAuthentication(context, appLockViewModel)
        }
    }

    // Navigate away only on explicit authentication success
    LaunchedEffect(uiState.isLoaded, uiState.isLocked) {
        if (uiState.isLoaded && !uiState.isLocked) {
            onUnlocked()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.app_lock),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorSmall,
                hazeState = hazeState,
                hasBackButton = false
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                .hazeSource(state = hazeState)
                .padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Lock icon
                Icon(
                    imageVector = Iconax.Padlock,
                    contentDescription = stringResource(R.string.app_locked_title),
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Title
                Text(
                    text = stringResource(R.string.app_locked_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Description
                Text(
                    text = stringResource(R.string.authenticate_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Show error if authentication failed
                if (uiState.authenticationError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.authenticationError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Spacing.md),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                // Show capability-specific message
                when (uiState.biometricCapability) {
                    BiometricCapability.Available -> {
                        // Unlock button
                        Button(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                if (context != null) {
                                    appLockViewModel.clearAuthError()
                                    triggerAuthentication(context, appLockViewModel)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.unlock))
                        }
                    }
                    else -> {
                        // Show error for unavailable biometric
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md)
                            ) {
                                Text(
                                    text = stringResource(R.string.biometric_unavailable_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = uiState.biometricCapability.getErrorMessage(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = stringResource(R.string.biometric_setup_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Privacy note
                Text(
                    text = stringResource(R.string.data_protected_format,
                        when (uiState.timeoutMinutes) {
                            0 -> stringResource(R.string.immediate_locking)
                            1 -> stringResource(R.string.minute_timeout_format, 1)
                            else -> stringResource(R.string.minute_timeout_format, uiState.timeoutMinutes)
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun triggerAuthentication(
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel
) {
    // Trigger authentication through the ViewModel
    appLockViewModel.triggerAuthentication(activity)
}
