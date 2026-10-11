package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.dataprivacy

import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCheckbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.applock.AppLockViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Padlock
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.green_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.green_light
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalHazeApi::class
)
@Composable
fun DataPrivacyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccounts: () -> Unit = {},
    appLockViewModel: AppLockViewModel = hiltViewModel(),
    onNavigateToSync: () -> Unit = {},
    securityOnly: Boolean = false,
    blurEffects: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    
    var showTimeoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(if (securityOnly) R.string.app_lock else R.string.data_privacy_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent { onNavigateBack() } }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(snackbarData = it)
                }
            ) }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = Dimensions.Padding.content),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Security Section
                SectionHeader(
                    title = stringResource(R.string.security_section),
                    modifier = Modifier.padding(start = Spacing.md, top = Spacing.md))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    PreferenceSwitch(
                        title = stringResource(R.string.app_lock),
                        subtitle =
                        if (appLockUiState.canUseBiometric) {
                            stringResource(R.string.app_lock_biometric_sub)
                        } else {
                            appLockUiState.biometricCapability.getErrorMessage()
                        },
                        checked = appLockUiState.isLockEnabled,
                        onCheckedChange = { enabled ->
                            appLockViewModel.setAppLockEnabled(enabled, io.github.hootkit_dev.hootbook.presentation.core.applock.coreFragmentActivity(context))
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = green_light,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Iconax.Padlock,
                                    contentDescription = null,
                                    tint = green_dark
                                )
                            }
                        },
                        padding = PaddingValues(0.dp),
                        isSingle = !appLockUiState.isLockEnabled,
                        isFirst = true,
                    )

                    // Lock Timeout Setting
                    AnimatedVisibility(visible = appLockUiState.isLockEnabled) {
                        ListItem(
                            headline = { Text(stringResource(R.string.lock_timeout)) },
                            supporting = {
                                Text(
                                    when (appLockUiState.timeoutMinutes) {
                                        0 -> stringResource(R.string.lock_timeout_immediate)
                                        1 -> stringResource(R.string.lock_timeout_1min)
                                        else -> stringResource(R.string.lock_timeout_minutes, appLockUiState.timeoutMinutes)
                                    }
                                )
                            },
                            trailing = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { showTimeoutDialog = true },
                            shape = ListItemPosition.Bottom.toShape(),
                            padding = PaddingValues(0.dp),
                        )
                    }
                }

                appLockUiState.authenticationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!securityOnly) {
                    ListItem(headline = { Text(stringResource(R.string.sync_firebase_title)) },
                        trailing = { Icon(Icons.Default.ChevronRight, null) }, onClick = onNavigateToSync,
                        shape = ListItemPosition.Single.toShape(), padding = PaddingValues(0.dp))
                    BackupActions(blurEffects = blurEffects, hazeState = hazeState)
                }
                // Add bottom spacing
                Spacer(modifier = Modifier.size(Spacing.xl))
            }
        }
    }
    
    // Timeout Dialog
    if (showTimeoutDialog) {
        val options = listOf(0, 1, 5, 15, 30)
        val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text(stringResource(R.string.lock_timeout)) },
            text = {
                Column {
                    options.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .selectable(
                                    selected = appLockUiState.timeoutMinutes == minutes,
                                    onClick = {
                                        appLockViewModel.setTimeoutMinutes(minutes)
                                        showTimeoutDialog = false
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLockUiState.timeoutMinutes == minutes,
                                onClick = null 
                            )
                            Text(
                                text = when (minutes) {
                                    0 -> stringResource(R.string.immediately)
                                    1 -> stringResource(R.string.one_minute)
                                    else -> stringResource(R.string.minutes_format, minutes)
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                DialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showTimeoutDialog = false }
                )
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    }
}
