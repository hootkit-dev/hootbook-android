@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.hootkit_dev.hootbook.presentation.core.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.LegacyCount
import dev.hootkit.core.LegacyImportSummary
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationPhase
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationPreview
import io.github.hootkit_dev.hootbook.core.migration.LegacyMigrationUiState
import io.github.hootkit_dev.hootbook.core.migration.LegacyServiceSettings
import io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar

@Composable
internal fun LegacyUpgradeScreen(
    onContinue: () -> Unit,
    viewModel: LegacyMigrationViewModel = hiltViewModel(),
) {
    CoreSecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val commandFailed by viewModel.commandFailed.collectAsStateWithLifecycle()
    var choosingDestination by rememberSaveable { mutableStateOf(false) }
    var pickerFailed by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        choosingDestination = false
        if (uri != null) viewModel.exportRecovery(uri)
    }
    LegacyUpgradeContent(
        state = state.copy(busy = state.busy || choosingDestination),
        commandFailed = commandFailed,
        pickerFailed = pickerFailed,
        onPrepare = viewModel::prepare,
        onMigrate = viewModel::migrate,
        onResumePlatform = viewModel::resumePlatform,
        onReload = viewModel::reload,
        onContinue = onContinue,
        onExport = {
            if (!state.busy && !choosingDestination) {
                pickerFailed = false
                choosingDestination = true
                try { save.launch("Hootbook-legacy-recovery.zip") }
                catch (_: Exception) { choosingDestination = false; pickerFailed = true }
            }
        },
    )
}

/** Stateless inputs allow the review, recovery and completion gates to be exercised without JNI. */
@Composable
internal fun LegacyUpgradeContent(
    state: LegacyMigrationUiState,
    onPrepare: () -> Unit,
    onMigrate: (Boolean) -> Unit,
    onResumePlatform: () -> Unit,
    onReload: () -> Unit,
    onContinue: () -> Unit,
    onExport: () -> Unit,
    commandFailed: Boolean = false,
    pickerFailed: Boolean = false,
) {
    var accepted by remember(state.preview, state.phase) { mutableStateOf(false) }
    var confirmExport by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val enabled = !state.busy && !leaving
    val reportMissing = when (state.phase) {
        LegacyMigrationPhase.COMPLETE -> state.result == null || state.preview == null
        LegacyMigrationPhase.REVIEW -> state.preview == null
        LegacyMigrationPhase.PLATFORM_PENDING -> state.result == null
        else -> false
    }
    val requiresReload = commandFailed || reportMissing
    BackHandler(enabled = state.busy || leaving) { /* Do not navigate away during a write. */ }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.legacy_upgrade_title),
                scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior(),
                scrollBehaviorLarge = scroll,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            LazyColumn(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxSize()
                    .testTag("legacy-upgrade-content"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.busy) item {
                    Text(stringResource(R.string.legacy_upgrade_working), style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    UpgradeCard(stringResource(if (reportMissing) R.string.legacy_upgrade_attention else phaseTitle(state.phase))) {
                        Text(stringResource(if (reportMissing) R.string.legacy_upgrade_report_missing else phaseDescription(state.phase)))
                    }
                }
                if (requiresReload || state.error != null || pickerFailed) item {
                    UpgradeCard(stringResource(R.string.legacy_upgrade_attention), warning = true) {
                        Text(when {
                            reportMissing -> stringResource(R.string.legacy_upgrade_report_missing)
                            commandFailed -> stringResource(R.string.legacy_upgrade_command_failed)
                            pickerFailed -> stringResource(R.string.legacy_upgrade_picker_failed)
                            else -> state.error ?: stringResource(R.string.legacy_upgrade_failed)
                        })
                        if (requiresReload) OutlinedButton(
                            onClick = onReload, enabled = enabled,
                            modifier = Modifier.testTag("legacy-upgrade-reload"),
                        ) { Text(stringResource(R.string.legacy_upgrade_reload)) }
                    }
                }
                val preview = state.preview
                val report = when (state.phase) {
                    LegacyMigrationPhase.COMPLETE, LegacyMigrationPhase.PLATFORM_PENDING -> state.result
                    else -> preview?.summary
                }
                if (report != null) {
                    item {
                        CoreCounts(
                            title = stringResource(if (state.phase == LegacyMigrationPhase.COMPLETE ||
                                state.phase == LegacyMigrationPhase.PLATFORM_PENDING)
                                R.string.legacy_upgrade_imported else R.string.legacy_upgrade_will_import),
                            counts = report.imported,
                            tag = "imported",
                        )
                    }
                    item {
                        CoreCounts(
                            title = stringResource(R.string.legacy_upgrade_core_omissions),
                            counts = report.dropped,
                            tag = "dropped",
                        )
                    }
                    if (report.dropped.any { it.count > 0u && isFinancialOmission(it.item) }) item {
                        UpgradeCard(stringResource(R.string.legacy_upgrade_financial_warning_title), warning = true) {
                            Text(stringResource(R.string.legacy_upgrade_financial_warning))
                        }
                    }
                    item { CurrencyChanges(report) }
                }
                if (preview != null) {
                    item { PlatformOmissions(preview) }
                    item { ServicePreview(preview.services) }
                }
                if (state.phase == LegacyMigrationPhase.REVIEW || state.phase == LegacyMigrationPhase.COMPLETE) item {
                    UpgradeCard(stringResource(R.string.legacy_upgrade_differences_title)) {
                        Text(stringResource(R.string.legacy_upgrade_sync_reset))
                        Text(stringResource(R.string.legacy_upgrade_field_differences))
                    }
                }
                if (state.phase == LegacyMigrationPhase.REVIEW && preview != null) item {
                    UpgradeCard(stringResource(R.string.legacy_upgrade_review_title)) {
                        Text(stringResource(R.string.legacy_upgrade_preview_note))
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = accepted, enabled = enabled && !requiresReload,
                                role = Role.Checkbox, onValueChange = { accepted = it },
                            ).padding(vertical = 8.dp).testTag("legacy-upgrade-accept-losses"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = accepted, onCheckedChange = null, enabled = enabled && !requiresReload)
                            Text(stringResource(R.string.legacy_upgrade_accept_losses), modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = { if (enabled && accepted && !requiresReload) onMigrate(true) },
                            enabled = enabled && accepted && !requiresReload,
                            modifier = Modifier.fillMaxWidth().testTag("legacy-upgrade-migrate"),
                        ) { Text(stringResource(R.string.legacy_upgrade_migrate)) }
                    }
                }
                if (!requiresReload) item {
                    when (state.phase) {
                        LegacyMigrationPhase.IDLE, LegacyMigrationPhase.FAILED -> Button(
                            onClick = onPrepare, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag("legacy-upgrade-prepare"),
                        ) { Text(stringResource(R.string.legacy_upgrade_prepare)) }
                        LegacyMigrationPhase.PLATFORM_PENDING -> Button(
                            onClick = onResumePlatform, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag("legacy-upgrade-resume-platform"),
                        ) { Text(stringResource(R.string.legacy_upgrade_finish_platform)) }
                        LegacyMigrationPhase.COMPLETE -> Button(
                            onClick = {
                                if (enabled && state.result != null && state.preview != null) {
                                    leaving = true
                                    onContinue()
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag("legacy-upgrade-continue"),
                        ) { Text(stringResource(R.string.legacy_upgrade_continue)) }
                        LegacyMigrationPhase.REVIEW, LegacyMigrationPhase.UNCERTAIN -> Unit
                    }
                }
                item {
                    UpgradeCard(stringResource(R.string.legacy_upgrade_originals_title)) {
                        Text(stringResource(R.string.legacy_upgrade_originals_retained))
                        Text(stringResource(R.string.legacy_upgrade_recovery_note), style = MaterialTheme.typography.bodyMedium)
                        if (state.exported) Text(
                            stringResource(R.string.legacy_upgrade_saved),
                            modifier = Modifier.testTag("legacy-upgrade-exported"),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(
                            onClick = { confirmExport = true }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag("legacy-upgrade-export"),
                        ) { Text(stringResource(R.string.legacy_upgrade_export)) }
                    }
                }
            }
        }
    }
    if (confirmExport) AlertDialog(
        onDismissRequest = { if (enabled) confirmExport = false },
        title = { Text(stringResource(R.string.legacy_upgrade_export_warning_title)) },
        text = { Text(stringResource(R.string.legacy_upgrade_export_warning)) },
        confirmButton = {
            TextButton(onClick = { confirmExport = false; onExport() }, enabled = enabled) {
                Text(stringResource(R.string.legacy_upgrade_choose_destination))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmExport = false }, enabled = enabled) {
                Text(stringResource(R.string.legacy_upgrade_cancel))
            }
        },
    )
}

@Composable
private fun UpgradeCard(title: String, warning: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun CoreCounts(title: String, counts: List<LegacyCount>, tag: String) {
    UpgradeCard(title) {
        if (counts.isEmpty()) Text(stringResource(R.string.legacy_upgrade_none_reported))
        counts.forEach { count ->
            CountRow(legacyItemLabel(count.item), count.count.toString(), "$tag-${count.item}")
        }
    }
}

@Composable
private fun CountRow(label: String, count: String, tag: String) {
    Row(
        Modifier.fillMaxWidth().testTag("legacy-upgrade-$tag"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(count, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PlatformOmissions(preview: LegacyMigrationPreview) {
    UpgradeCard(stringResource(R.string.legacy_upgrade_platform_omissions)) {
        CountRow(stringResource(R.string.legacy_upgrade_templates), preview.omittedTemplates.toString(), "templates")
        CountRow(stringResource(R.string.legacy_upgrade_default_account), if (preview.omittedDefaultAccount) "1" else "0", "default-account")
        CountRow(stringResource(R.string.legacy_upgrade_missing_attachments), preview.missingAttachments.toString(), "missing-attachments")
        CountRow(stringResource(R.string.legacy_upgrade_missing_avatars), preview.missingAvatars.toString(), "missing-avatars")
        Text(stringResource(R.string.legacy_upgrade_omissions_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ServicePreview(services: LegacyServiceSettings.Preview) {
    UpgradeCard(stringResource(R.string.legacy_upgrade_service_title)) {
        Text(stringResource(when {
            services.aiAccessFailed -> R.string.legacy_upgrade_ai_unreadable
            services.aiRequiresSetup -> R.string.legacy_upgrade_ai_setup
            services.aiExported -> R.string.legacy_upgrade_ai_prepared
            services.aiPresent -> R.string.legacy_upgrade_ai_present
            else -> R.string.legacy_upgrade_ai_absent
        }))
        services.aiConfigured?.let { configured ->
            CountRow(
                stringResource(R.string.legacy_upgrade_ai_core_configured),
                stringResource(if (configured) R.string.legacy_upgrade_yes else R.string.legacy_upgrade_no),
                "ai-configured",
            )
        }
        services.aiHasKey?.let { hasKey ->
            CountRow(
                stringResource(R.string.legacy_upgrade_ai_core_key_present),
                stringResource(if (hasKey) R.string.legacy_upgrade_yes else R.string.legacy_upgrade_no),
                "ai-key-present",
            )
        }
        if (services.aiConfigured != null || services.aiHasKey != null) {
            Text(stringResource(R.string.legacy_upgrade_ai_core_note), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(when {
            services.brokerageAccessFailed -> R.string.legacy_upgrade_brokerage_unreadable
            services.brokeragePresent -> R.string.legacy_upgrade_brokerage_present
            else -> R.string.legacy_upgrade_brokerage_absent
        }))
        if (services.brokeragePresent && !services.brokerageAccessFailed) {
            CountRow(stringResource(R.string.legacy_upgrade_brokerage_connections), services.brokerageConnections.toString(), "service-connections")
            CountRow(stringResource(R.string.legacy_upgrade_brokerage_accounts), services.brokerageAccounts.toString(), "service-accounts")
            CountRow(stringResource(R.string.legacy_upgrade_brokerage_holdings), services.brokerageHoldings.toString(), "service-holdings")
        }
        Text(stringResource(R.string.legacy_upgrade_service_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CurrencyChanges(summary: LegacyImportSummary) {
    UpgradeCard(stringResource(R.string.legacy_upgrade_currency_title)) {
        Text(stringResource(R.string.legacy_upgrade_main_currency, summary.mainCurrency))
        summary.renamedCurrencies.forEach { (old, current) ->
            Text(stringResource(R.string.legacy_upgrade_renamed_currency, old, current))
        }
    }
}

private fun phaseTitle(phase: LegacyMigrationPhase): Int = when (phase) {
    LegacyMigrationPhase.IDLE -> R.string.legacy_upgrade_start_title
    LegacyMigrationPhase.REVIEW -> R.string.legacy_upgrade_review_title
    LegacyMigrationPhase.COMPLETE -> R.string.legacy_upgrade_complete_title
    LegacyMigrationPhase.UNCERTAIN -> R.string.legacy_upgrade_uncertain_title
    LegacyMigrationPhase.PLATFORM_PENDING -> R.string.legacy_upgrade_platform_title
    LegacyMigrationPhase.FAILED -> R.string.legacy_upgrade_failed_title
}

private fun phaseDescription(phase: LegacyMigrationPhase): Int = when (phase) {
    LegacyMigrationPhase.IDLE -> R.string.legacy_upgrade_description
    LegacyMigrationPhase.REVIEW -> R.string.legacy_upgrade_review_description
    LegacyMigrationPhase.COMPLETE -> R.string.legacy_upgrade_complete_description
    LegacyMigrationPhase.UNCERTAIN -> R.string.legacy_upgrade_uncertain_description
    LegacyMigrationPhase.PLATFORM_PENDING -> R.string.legacy_upgrade_platform_description
    LegacyMigrationPhase.FAILED -> R.string.legacy_upgrade_failed_description
}

internal fun isFinancialOmission(item: String): Boolean = item in setOf(
    "cards", "balance_updates", "unknown_transactions", "deleted_transactions", "deleted_debts",
    "invalid_records", "brokerage_connections", "loan_transactions_without_account", "budget_limits",
)

@Composable
private fun legacyItemLabel(item: String): String {
    val resource = when (item) {
        "accounts" -> R.string.legacy_upgrade_count_accounts
        "anchors" -> R.string.legacy_upgrade_count_anchors
        "categories" -> R.string.legacy_upgrade_count_categories
        "subcategories" -> R.string.legacy_upgrade_count_subcategories
        "transactions" -> R.string.legacy_upgrade_count_transactions
        "budgets" -> R.string.legacy_upgrade_count_budgets
        "budget_limits" -> R.string.legacy_upgrade_count_budget_limits
        "subscriptions" -> R.string.legacy_upgrade_count_subscriptions
        "people" -> R.string.legacy_upgrade_count_people
        "debts" -> R.string.legacy_upgrade_count_debts
        "custom_rates" -> R.string.legacy_upgrade_count_custom_rates
        "attachments" -> R.string.legacy_upgrade_count_attachments
        "cards" -> R.string.legacy_upgrade_count_cards
        "balance_updates" -> R.string.legacy_upgrade_count_balance_updates
        "unknown_transactions" -> R.string.legacy_upgrade_count_unknown_transactions
        "deleted_transactions" -> R.string.legacy_upgrade_count_deleted_transactions
        "deleted_debts" -> R.string.legacy_upgrade_count_deleted_debts
        "invalid_records" -> R.string.legacy_upgrade_count_invalid_records
        "brokerage_connections" -> R.string.legacy_upgrade_count_brokerage_connections
        "loan_transactions_without_account" -> R.string.legacy_upgrade_count_unlinked_loans
        "downloaded_rates" -> R.string.legacy_upgrade_count_downloaded_rates
        else -> null
    }
    return resource?.let { stringResource(it) } ?: item
}
