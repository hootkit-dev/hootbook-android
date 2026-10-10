@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.cloudbackup

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.hootkit_dev.hootbook.data.sync.SyncManager
import io.github.hootkit_dev.hootbook.presentation.ui.components.TooltipIconButton
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync.SyncViewModel
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.backup.BackupConfiguration
import io.github.hootkit_dev.hootbook.data.cloud.CloudFileInfo
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderType
import io.github.hootkit_dev.hootbook.data.cloud.SyncStatus
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookCheckbox
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.DeleteCloudSnapshotDialog
import io.github.hootkit_dev.hootbook.presentation.ui.components.GenericTypeSwitcher
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.SectionHeader
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.dataprivacy.DataPrivacyViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DirectboxReceive
import io.github.hootkit_dev.hootbook.presentation.ui.icons.DirectboxSend
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Folder2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ImportArrow01
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.blue_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.blue_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.green_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.green_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.orange_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.orange_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.yellow_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.yellow_light
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeApi::class)
@Composable
fun BackupSyncScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccounts: () -> Unit = {},
    // Backup & sync → Firebase sync (the sync page)
    onNavigateToFirebaseSync: () -> Unit = {},
    viewModel: BackupSyncViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel(),
    dataPrivacyViewModel: DataPrivacyViewModel = hiltViewModel(),
    blurEffects: Boolean
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val firebaseSync by syncViewModel.state.collectAsStateWithLifecycle()
    val dataPrivacyUiState by dataPrivacyViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    var webDavUrl by remember(uiState.webDavConfig.url) { mutableStateOf(uiState.webDavConfig.url) }
    var webDavUser by remember(uiState.webDavConfig.username) { mutableStateOf(uiState.webDavConfig.username) }
    var webDavPass by remember(uiState.webDavConfig.passwordOrToken) { mutableStateOf(uiState.webDavConfig.passwordOrToken) }

    var showRetentionDialog by remember { mutableStateOf(false) }
    var showE2eDialog by remember { mutableStateOf(false) }
    var e2ePassphraseInput by remember { mutableStateOf("") }
    var restorePassphraseInput by remember { mutableStateOf("") }
    var snapshotToDelete by remember { mutableStateOf<CloudFileInfo?>(null) }
    // Restoring replaces everything here, so it asks first
    var snapshotToRestore by remember { mutableStateOf<CloudFileInfo?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    // File launchers for Data Management
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri -> 
            uri?.let { dataPrivacyViewModel.saveBackupToFile(it) }
            if (uri == null) dataPrivacyViewModel.clearExportedFile()
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { dataPrivacyViewModel.importBackup(it) } }
    )

    val cashewImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { dataPrivacyViewModel.importCashew(it) } }
    )

    // Handle export completion
    LaunchedEffect(dataPrivacyUiState.exportedBackupFile) {
        dataPrivacyUiState.exportedBackupFile?.let { file ->
            exportLauncher.launch(file.name)
        }
    }

    // Handle data management messages
    LaunchedEffect(dataPrivacyUiState.importExportMessage) {
        dataPrivacyUiState.importExportMessage?.let { message ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (dataPrivacyUiState.hasNewAccountsCreated) context.getString(R.string.review) else null,
                    withDismissAction = true
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onNavigateToAccounts()
                }
                dataPrivacyViewModel.clearImportExportMessage()
            }
        }
    }

    // Setup Google Sign-In Client for Google Drive AppData
    val gDriveSignInClient = remember(context) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()
        GoogleSignIn.getClient(context, options)
    }

    val gDriveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Read the result whatever the result code: a failed sign-in (DEVELOPER_ERROR 10,
        // 12500…) also returns RESULT_CANCELED, with the error in the intent
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.handleGoogleSignInResult(account)
        } catch (e: ApiException) {
            Log.e("BackupSyncScreen", "Google Sign-In failed code=${e.statusCode}", e)
            // Only the user closing the picker stays quiet
            if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.signin_failed_code_format, e.statusCode))
                }
            }
        } catch (e: Exception) {
            Log.e("BackupSyncScreen", "Google Sign-In failed", e)
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.auth_failed_format, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    val recoverableAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User granted permission, try sign-in again
            val account = GoogleSignIn.getLastSignedInAccount(context)
            viewModel.handleGoogleSignInResult(account)
        }
        viewModel.clearRecoverableAuthIntent()
    }

    LaunchedEffect(uiState.recoverableAuthIntent) {
        uiState.recoverableAuthIntent?.let { intent ->
            recoverableAuthLauncher.launch(intent)
        }
    }

    // Handle sync status snackbars
    LaunchedEffect(uiState.syncStatus) {
        when (val status = uiState.syncStatus) {
            is SyncStatus.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar(status.message)
                    viewModel.dismissSyncStatus()
                }
            }
            is SyncStatus.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.error_format, status.message))
                    viewModel.dismissSyncStatus()
                }
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.backup_sync_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent { onNavigateBack() } }
            )
        },
        snackbarHost = {
            Column(
                modifier = Modifier
                    .padding(horizontal = Dimensions.Padding.content)
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Active Operation Progress Bar
                AnimatedVisibility(
                    visible = uiState.syncStatus is SyncStatus.BackingUp ||
                            uiState.syncStatus is SyncStatus.Restoring,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    when (val status = uiState.syncStatus) {
                        is SyncStatus.BackingUp -> OperationProgressCard(status.message, status.progress)
                        is SyncStatus.Restoring -> OperationProgressCard(status.message, status.progress)
                        else -> {}
                    }
                }

                SnackbarHost(hostState = snackbarHostState) {
                    Snackbar(
                        snackbarData = it,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = Dimensions.Padding.content),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {

                // Seamless sync: Firebase (docs/sync.md), on its own page
                SectionHeader(
                    title = stringResource(R.string.seamless_sync_section),
                    modifier = Modifier.padding(start = Spacing.md, top = Spacing.md)
                )
                FirebaseSyncEntry(state = firebaseSync, onClick = onNavigateToFirebaseSync)

                // Full backup: manual export and restore, to a file, Google Drive or WebDAV
                SectionHeader(
                    title = stringResource(R.string.full_backup_section),
                    modifier = Modifier.padding(start = Spacing.md, top = Spacing.md)
                )
                Text(
                    text = stringResource(R.string.full_backup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    ProviderOptionItem(
                        title = CloudProviderType.LOCAL_ONLY.getLocalizedDisplayName(),
                        subtitle = stringResource(R.string.store_backups_local_desc),
                        providerType = CloudProviderType.LOCAL_ONLY,
                        isSelected = uiState.activeProviderType == CloudProviderType.LOCAL_ONLY,
                        position = ListItemPosition.Top,
                        onClick = { viewModel.setActiveProviderType(CloudProviderType.LOCAL_ONLY) }
                    )
                    ProviderOptionItem(
                        title = CloudProviderType.GOOGLE_DRIVE.getLocalizedDisplayName(),
                        subtitle = stringResource(R.string.google_drive_provider_desc),
                        providerType = CloudProviderType.GOOGLE_DRIVE,
                        isSelected = uiState.activeProviderType == CloudProviderType.GOOGLE_DRIVE,
                        position = ListItemPosition.Middle,
                        onClick = { viewModel.setActiveProviderType(CloudProviderType.GOOGLE_DRIVE) }
                    )
                    ProviderOptionItem(
                        title = CloudProviderType.WEBDAV.getLocalizedDisplayName(),
                        subtitle = stringResource(R.string.webdav_provider_desc),
                        providerType = CloudProviderType.WEBDAV,
                        isSelected = uiState.activeProviderType == CloudProviderType.WEBDAV,
                        position = ListItemPosition.Bottom,
                        onClick = { viewModel.setActiveProviderType(CloudProviderType.WEBDAV) }
                    )
                }

                            // WebDAV Configuration Card
                            AnimatedVisibility(visible = uiState.activeProviderType == CloudProviderType.WEBDAV) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Spacing.md),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.webdav_credentials_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        TextField(
                                            value = webDavUrl,
                                            onValueChange = { webDavUrl = it },
                                            label = { Text(stringResource(R.string.server_url_hint)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(
                                                    0.5f
                                                ),
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(
                                                    0.5f
                                                ),
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                focusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(
                                                    0.5f
                                                ),
                                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(
                                                    0.5f
                                                ),
                                            ),
                                        )
                                        TextField(
                                            value = webDavUser,
                                            onValueChange = { webDavUser = it },
                                            label = { Text(stringResource(R.string.username)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(
                                                    0.5f
                                                ),
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(
                                                    0.5f
                                                ),
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                focusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(
                                                    0.5f
                                                ),
                                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(
                                                    0.5f
                                                ),
                                            ),
                                        )
                                        TextField(
                                            value = webDavPass,
                                            onValueChange = { webDavPass = it },
                                            label = { Text(stringResource(R.string.password_or_token)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(
                                                    0.5f
                                                ),
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(
                                                    0.5f
                                                ),
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                focusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(
                                                    0.5f
                                                ),
                                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(
                                                    0.5f
                                                ),
                                            ),
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                shapes = ButtonDefaults.shapes(),
                                                onClick = {
                                                    viewModel.testConnection(
                                                        CloudProviderType.WEBDAV
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(stringResource(R.string.test_connection))
                                            }
                                            Button(
                                                shapes = ButtonDefaults.shapes(),
                                                onClick = {
                                                    viewModel.updateWebDavConfig(
                                                        webDavUrl,
                                                        webDavUser,
                                                        webDavPass,
                                                        true
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(stringResource(R.string.save_configuration))
                                            }
                                        }
                                    }
                                }
                            }

                            // Google Drive Configuration Card
                            AnimatedVisibility(visible = uiState.activeProviderType == CloudProviderType.GOOGLE_DRIVE) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.google_drive_configuration),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(Spacing.md,),
                                        )

                                        if (uiState.isGoogleDriveSignedIn) {
                                            ListItem(
                                                headline = { Text(uiState.googleDriveConfig.accountEmail) },
                                                supporting = { Text(stringResource(R.string.connected_to_google_drive)) },
                                                leading = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .background(green_light, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Email,
                                                            contentDescription = null,
                                                            tint = green_dark
                                                        )
                                                    }
                                                },
                                                padding = PaddingValues(0.dp)
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                                            ) {
                                                Button(
                                                    shapes = ButtonDefaults.shapes(),
                                                    onClick = {
                                                        gDriveSignInClient.signOut()
                                                        viewModel.onGoogleDriveSignOut()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text(stringResource(R.string.sign_out))
                                                }
                                                Button(
                                                    shapes = ButtonDefaults.shapes(),
                                                    onClick = {
                                                        viewModel.testConnection(
                                                            CloudProviderType.GOOGLE_DRIVE
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    ),
                                                ) {
                                                    Text(stringResource(R.string.test_connection))
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = stringResource(R.string.google_drive_signin_desc),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                            )
                                            Spacer(modifier = Modifier.size(Spacing.sm))
                                            Button(
                                                shapes = ButtonDefaults.shapes(),
                                                onClick = {
                                                    gDriveSignInLauncher.launch(gDriveSignInClient.signInIntent)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = Spacing.md)
                                                    .padding(bottom = Spacing.md)
                                            ) {
                                                Icon(
                                                    Icons.Default.Email,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.size(8.dp))
                                                Text(stringResource(R.string.sign_in_with_google))
                                            }
                                        }
                                    }
                                }
                            }


                val cloud = uiState.activeProviderType != CloudProviderType.LOCAL_ONLY
                if (cloud) {
                    // End-to-end encryption of cloud backups, and how many are kept
                    SectionHeader(
                        title = stringResource(R.string.backup_security_section),
                        modifier = Modifier.padding(start = Spacing.md)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                                    PreferenceSwitch(
                                        title = stringResource(R.string.e2e_encryption),
                                        subtitle = if (uiState.isE2eEnabled) stringResource(R.string.e2e_enabled_desc) else stringResource(
                                            R.string.e2e_disabled_desc
                                        ),
                                        checked = uiState.isE2eEnabled,
                                        onCheckedChange = { enabled ->
                                            if (enabled) {
                                                e2ePassphraseInput = uiState.e2ePassphrase
                                                showE2eDialog = true
                                            } else {
                                                viewModel.setE2eEncryption(false, "")
                                            }
                                        },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(green_light, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Security,
                                                    contentDescription = null,
                                                    tint = green_dark
                                                )
                                            }
                                        },
                                        padding = PaddingValues(0.dp),
                                        isFirst = true
                                    )

                                    ListItem(
                                        headline = { Text(stringResource(R.string.backup_retention_limit)) },
                                        supporting = {
                                            Text(
                                                stringResource(
                                                    R.string.keep_snapshots_format,
                                                    uiState.retentionLimit
                                                )
                                            )
                                        },
                                        leading = {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(orange_light, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Storage,
                                                    contentDescription = null,
                                                    tint = orange_dark
                                                )
                                            }
                                        },
                                        trailing = {
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = { showRetentionDialog = true },
                                        shape = ListItemPosition.Bottom.toShape(),
                                        padding = PaddingValues(0.dp)
                                    )
                    }
                }

                // Manual actions only: nothing runs on a schedule
                val place = uiState.activeProviderType.getLocalizedDisplayName()
                val cloudReady = when (uiState.activeProviderType) {
                    CloudProviderType.LOCAL_ONLY -> true
                    CloudProviderType.GOOGLE_DRIVE -> uiState.isGoogleDriveSignedIn
                    CloudProviderType.WEBDAV -> uiState.webDavConfig.isConfigured
                }
                val idle = uiState.syncStatus is SyncStatus.Idle
                SectionHeader(
                    title = stringResource(R.string.manual_actions),
                    modifier = Modifier.padding(start = Spacing.md)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    ActionItem(
                        title = stringResource(R.string.backup_export_to, place),
                        subtitle = stringResource(if (cloud) R.string.backup_export_cloud_body else R.string.backup_export_local_body),
                        icon = Iconax.DirectboxSend,
                        container = yellow_light,
                        tint = yellow_dark,
                        position = ListItemPosition.Top,
                        onClick = if (!cloudReady || !idle) null else ({
                            if (cloud) viewModel.performManualBackup() else showExportDialog = true
                        })
                    )
                    ActionItem(
                        title = stringResource(R.string.backup_restore_from, place),
                        subtitle = stringResource(if (cloud) R.string.backup_restore_cloud_body else R.string.backup_restore_local_body),
                        icon = Iconax.DirectboxReceive,
                        container = green_light,
                        tint = green_dark,
                        position = ListItemPosition.Middle,
                        onClick = if (!cloudReady || !idle) null else ({
                            if (cloud) viewModel.loadRemoteSnapshots() else importLauncher.launch("*/*")
                        })
                    )
                    ActionItem(
                        title = stringResource(R.string.import_cashew_backup),
                        subtitle = stringResource(R.string.import_cashew_backup_sub),
                        icon = Iconax.ImportArrow01,
                        container = blue_light,
                        tint = blue_dark,
                        position = ListItemPosition.Bottom,
                        onClick = { cashewImportLauncher.launch("*/*") }
                    )
                }

                if (cloud && cloudReady) {
                    // The backups in the chosen place, to restore or delete
                    SectionHeader(
                        title = stringResource(R.string.cloud_snapshots),
                        modifier = Modifier.padding(start = Spacing.md),
                        action = {
                            TooltipIconButton(
                                icon = Icons.Rounded.Refresh,
                                label = stringResource(R.string.backup_refresh_list),
                                onClick = { viewModel.loadRemoteSnapshots() }
                            )
                        }
                    )
                                if (uiState.isLoadingSnapshots) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.lg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator()
                                    }
                                } else if (uiState.remoteSnapshots.isEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = stringResource(R.string.no_cloud_snapshots),
                                            modifier = Modifier.padding(Spacing.md),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(1.5.dp)
                                    ) {
                                        uiState.remoteSnapshots.forEachIndexed { index, snapshot ->
                                            val pos = when {
                                                uiState.remoteSnapshots.size == 1 -> ListItemPosition.Single
                                                index == 0 -> ListItemPosition.Top
                                                index == uiState.remoteSnapshots.size - 1 -> ListItemPosition.Bottom
                                                else -> ListItemPosition.Middle
                                            }
                                            SnapshotListItem(
                                                snapshot = snapshot,
                                                position = pos,
                                                onRestore = { snapshotToRestore = snapshot },
                                                onDelete = { snapshotToDelete = snapshot }
                                            )
                                        }
                                    }
                                }
                }

                Spacer(modifier = Modifier.size(Spacing.xl))
            }
        }
    }

    // Retention Selection Dialog
    if (showRetentionDialog) {
        val limits = listOf(5, 10, 20, 50)
        val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text(stringResource(R.string.retention_limit)) },
            text = {
                Column {
                    limits.forEach { limit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .selectable(
                                    selected = uiState.retentionLimit == limit,
                                    onClick = {
                                        viewModel.setRetentionLimit(limit)
                                        showRetentionDialog = false
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.retentionLimit == limit, onClick = null)
                            Text(text = stringResource(R.string.keep_backups_format, limit), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                DialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showRetentionDialog = false }
                )
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    }

    // E2E Passphrase Dialog
    if (showE2eDialog) {
        val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        AlertDialog(
            onDismissRequest = { showE2eDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.e2e_encryption)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.enter_passphrase_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextField(
                        value = e2ePassphraseInput,
                        onValueChange = { e2ePassphraseInput = it },
                        label = { Text(stringResource(R.string.passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.enable_and_save),
                    onClick = {
                        if (e2ePassphraseInput.isNotBlank()) {
                            viewModel.setE2eEncryption(true, e2ePassphraseInput)
                            showE2eDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                DialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showE2eDialog = false }
                )
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    }

    // Restore E2E Passphrase Dialog
    if (uiState.showRestorePassphraseDialog) {
        val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestorePassphraseDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.encrypted_backup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.restore_passphrase_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextField(
                        value = restorePassphraseInput,
                        onValueChange = { restorePassphraseInput = it },
                        label = { Text(stringResource(R.string.passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.restore),
                    onClick = {
                        if (restorePassphraseInput.isNotBlank()) {
                            viewModel.retryRestoreWithPassphrase(restorePassphraseInput)
                        }
                    }
                )
            },
            dismissButton = {
                DialogDismissButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        viewModel.dismissRestorePassphraseDialog()
                        restorePassphraseInput = ""
                    }
                )
            },
            containerColor = HootbookDialogDefaults.containerColor
        )
    }

    snapshotToRestore?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { snapshotToRestore = null },
            title = { Text(stringResource(R.string.cloud_restore_confirm_title)) },
            text = { Text(stringResource(R.string.cloud_restore_confirm_text, snapshot.name)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.cloud_restore_confirm),
                    onClick = {
                        viewModel.restoreSnapshot(snapshot)
                        snapshotToRestore = null
                    }
                )
            },
            dismissButton = {
                DialogDismissButton(text = stringResource(R.string.cancel), onClick = { snapshotToRestore = null })
            }
        )
    }

    // Delete Cloud Snapshot Confirmation Dialog
    if (snapshotToDelete != null) {
        val snapshot = snapshotToDelete!!
        DeleteCloudSnapshotDialog(
            snapshotName = snapshot.name,
            onDismiss = { snapshotToDelete = null },
            onDelete = {
                viewModel.deleteSnapshot(snapshot)
                snapshotToDelete = null
            },
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    // Export Options Dialog for Data Management
    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { config ->
                dataPrivacyViewModel.exportBackup(config)
                showExportDialog = false
            },
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

}

@Composable
fun OperationProgressCard(message: String, progress: Int? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (progress != null) {
                LinearWavyProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ProviderOptionItem(
    title: String,
    subtitle: String,
    providerType: CloudProviderType,
    isSelected: Boolean,
    position: ListItemPosition,
    onClick: () -> Unit
) {
    ListItem(
        headline = { Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
        supporting = { Text(subtitle) },
        leading = {
            when (providerType) {
                CloudProviderType.WEBDAV -> Icon(
                    painter = painterResource(R.drawable.ic_brand_nextcloud),
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                CloudProviderType.GOOGLE_DRIVE -> Icon(
                    painter = painterResource(R.drawable.ic_brand_google_drive),
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                CloudProviderType.LOCAL_ONLY -> Icon(
                    imageVector = Iconax.Folder2,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        selectedListColor = MaterialTheme.colorScheme.primaryContainer,
        selected = isSelected,
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}

@Composable
fun SnapshotListItem(
    snapshot: CloudFileInfo,
    position: ListItemPosition,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(snapshot.lastModified) {
        io.github.hootkit_dev.hootbook.utils.DateFormats.fullDate(
            java.time.Instant.ofEpochMilli(snapshot.lastModified).atZone(java.time.ZoneId.systemDefault())
        ) + " " + io.github.hootkit_dev.hootbook.utils.DateFormats.time(
            java.time.Instant.ofEpochMilli(snapshot.lastModified).atZone(java.time.ZoneId.systemDefault())
        )
    }
    val sizeMb = stringResource(R.string.size_mb_format, snapshot.size / (1024f * 1024f))

    ListItem(
        headline = { Text(snapshot.name) },
        supporting = { Text(stringResource(R.string.snapshot_subtitle_format, timeStr, sizeMb)) },
        trailing = {
            Row {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onRestore) {
                    Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore_snapshot), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDelete) {
                    Icon(Iconax.Bag, contentDescription = stringResource(R.string.delete_snapshot), tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        onClick = {},
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (BackupConfiguration) -> Unit,
    blurEffects: Boolean,
    hazeState: HazeState = remember { HazeState() }
) {
    var includeTransactional by remember { mutableStateOf(true) }
    var includeProfile by remember { mutableStateOf(true) }
    var includeBudgets by remember { mutableStateOf(true) }
    var includePreferences by remember { mutableStateOf(true) }
    var includeBrokerage by remember { mutableStateOf(false) }
    var includeAiKey by remember { mutableStateOf(false) }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_data)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.select_data_to_backup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExportCheckbox(stringResource(R.string.transactional_data), includeTransactional) { includeTransactional = it }
                ExportCheckbox(stringResource(R.string.profile_data), includeProfile) { includeProfile = it }
                ExportCheckbox(stringResource(R.string.budgets), includeBudgets) { includeBudgets = it }
                ExportCheckbox(stringResource(R.string.app_preferences), includePreferences) { includePreferences = it }
                ExportCheckbox(stringResource(R.string.backup_brokerage_credentials), includeBrokerage) { includeBrokerage = it }
                if (includeBrokerage) {
                    Text(
                        stringResource(R.string.backup_brokerage_credentials_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ExportCheckbox(stringResource(R.string.backup_ai_key), includeAiKey) { includeAiKey = it }
                if (includeAiKey) {
                    Text(
                        stringResource(R.string.backup_ai_key_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                text = stringResource(R.string.export),
                onClick = {
                    onConfirm(
                        BackupConfiguration(
                            includeTransactionalData = includeTransactional,
                            includeProfileData = includeProfile,
                            includeBudgets = includeBudgets,
                            includeAppPreferences = includePreferences,
                            includeBrokerageCredentials = includeBrokerage,
                            includeAiKey = includeAiKey
                        )
                    )
                }
            )
        },
        dismissButton = {
            DialogDismissButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        },
        containerColor = HootbookDialogDefaults.containerColor
    )
}

@Composable
fun ExportCheckbox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HootbookCheckbox(checked = checked, onCheckedChange = null)
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CloudProviderType.getLocalizedDisplayName(): String {
    return when (this) {
        CloudProviderType.LOCAL_ONLY -> stringResource(R.string.provider_local_file)
        CloudProviderType.WEBDAV -> stringResource(R.string.provider_webdav)
        CloudProviderType.GOOGLE_DRIVE -> stringResource(R.string.provider_google_drive)
    }
}

/** The Firebase sync row: whether it is on, and as whom; opens the sync page. */
@Composable
private fun FirebaseSyncEntry(state: SyncManager.State, onClick: () -> Unit) {
    val email = state.email.orEmpty()
    val status = when (state.stage) {
        SyncManager.Stage.UNAVAILABLE -> stringResource(R.string.sync_unavailable_title)
        SyncManager.Stage.SIGNED_OUT -> stringResource(R.string.sync_entry_signed_out)
        SyncManager.Stage.PASSPHRASE, SyncManager.Stage.CHOICE -> stringResource(R.string.sync_entry_setup, email)
        SyncManager.Stage.ACTIVE ->
            stringResource(if (state.paused) R.string.sync_entry_paused else R.string.sync_entry_on, email)
    }
    ListItem(
        headline = { Text(stringResource(R.string.sync_firebase_title)) },
        supporting = { Text(status) },
        leading = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        },
        trailing = {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        onClick = onClick,
        shape = ListItemPosition.Single.toShape(),
        padding = PaddingValues(0.dp)
    )
}

/** One manual action; without [onClick] (no provider set up, or busy) it shows but does nothing. */
@Composable
private fun ActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    tint: Color,
    position: ListItemPosition,
    onClick: (() -> Unit)?
) {
    val enabled = onClick != null
    ListItem(
        headline = {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        supporting = { Text(subtitle) },
        leading = {
            Box(
                modifier = Modifier.size(48.dp).background(container, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint)
            }
        },
        trailing = {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        onClick = onClick,
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )
}
