@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.PreferenceSwitch
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils

/**
 * "Broker auto-sync": one row per registered [BrokerageProviderInfo] (IBKR Flex today), with its
 * connections under it. A provider registered in `BrokerageModule` appears here by itself; its
 * credentials form is picked by [BrokerConnectForm].
 */
@Composable
fun BrokerAutoSyncSection(
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    onOpenConnection: (BrokerageConnection) -> Unit = {},
    viewModel: InvestmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connections = state.connections
    val busy = state.busy
    val error = state.error
    val connecting = state.connectProvider
    val settingCredentials = state.credentialsTarget
    val disconnecting = state.connections.find { it.id == state.disconnectTarget }

    BrokerAutoSyncContent(
        providers = state.providers,
        institutions = state.institutions.associateBy { it.key },
        connections = connections,
        busy = busy,
        showTitle = showTitle,
        dailyAutoSyncEnabled = state.dailyAutoSyncEnabled,
        // A failed connection shows in its form; other failures (disconnect) show here
        error = error.takeIf { connecting == null && settingCredentials == null },
        onConnect = viewModel::showConnect,
        onSetCredentials = { viewModel.showSetCredentials(it.id) },
        onRefresh = { viewModel.refresh(it.id) },
        onDisconnect = { viewModel.showDisconnect(it.id) },
        onSetDailyAutoSync = viewModel::setDailyAutoSync,
        onOpenConnection = { connection ->
            when {
                !connection.hasCredentials -> viewModel.showSetCredentials(connection.id)
                connection.syncedAt == null -> viewModel.refresh(connection.id)
                else -> onOpenConnection(connection)
            }
        },
        modifier = modifier,
    )

    connecting?.let { providerId ->
        BrokerConnectForm(
            providerId = providerId,
            busy = busy,
            error = error,
            onDismiss = {
                if (busy) viewModel.cancelOperation() else viewModel.showConnect(null)
                viewModel.clearError()
            },
            onConnect = { label, fields -> viewModel.connect(providerId, label, fields) },
        )
    }
    settingCredentials?.let { connection ->
        BrokerConnectForm(
            providerId = connection.providerId,
            busy = busy,
            error = error,
            onDismiss = {
                if (busy) viewModel.cancelOperation() else viewModel.showSetCredentials(null)
                viewModel.clearError()
            },
            onConnect = { _, fields -> viewModel.setCredentials(connection.id, fields) },
        )
    }
    disconnecting?.let { connection ->
        AlertDialog(
            onDismissRequest = { viewModel.showDisconnect(null) },
            title = { Text(stringResource(R.string.investments_disconnect)) },
            text = { Text(stringResource(R.string.investments_disconnect_hint)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.investments_disconnect),
                    onClick = { viewModel.disconnect(connection.id) },
                    destructive = true,
                )
            },
            dismissButton = {
                DialogDismissButton(
                    text = stringResource(R.string.investments_cancel),
                    onClick = { viewModel.showDisconnect(null) },
                )
            },
            containerColor = HootbookDialogDefaults.containerColor,
        )
    }
}

@Composable
internal fun BrokerAutoSyncContent(
    providers: List<BrokerageProviderInfo>,
    institutions: Map<String, Institution>,
    connections: List<BrokerageConnection>,
    busy: Boolean,
    error: CoreInvestmentFailure?,
    onConnect: (String) -> Unit,
    onDisconnect: (BrokerageConnection) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    dailyAutoSyncEnabled: Boolean = true,
    onSetCredentials: (BrokerageConnection) -> Unit = {},
    onRefresh: (BrokerageConnection) -> Unit = {},
    onSetDailyAutoSync: (Boolean) -> Unit = {},
    onOpenConnection: (BrokerageConnection) -> Unit = {},
) {
    val language = LocalConfiguration.current.locales[0].language
    val context = LocalContext.current
    // Each provider's row, then a row per connection of it
    val rows =
        providers.flatMap { provider ->
            listOf<Any>(provider) + connections.filter { it.providerId == provider.id }
        }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (showTitle) {
            Text(
                stringResource(R.string.broker_auto_sync_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.broker_auto_sync_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
            rows.forEachIndexed { index, row ->
                val shape = ListItemPosition.from(index, rows.size).toShape()
                when (row) {
                    is BrokerageProviderInfo -> {
                        val institution = institutions[row.institutionKey]
                        val ids =
                            connections
                                .filter { it.providerId == row.id }
                                .flatMap { c -> c.accounts.map { it.accountId } }
                        ListItem(
                            modifier = Modifier.testTag("broker_provider_${row.id}"),
                            headline = {
                                Text(
                                    (if (language == "zh") institution?.chineseName
                                    else institution?.englishName) ?: row.id,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            supporting = {
                                Text(
                                    if (ids.isEmpty())
                                        stringResource(R.string.broker_auto_sync_not_connected)
                                    else
                                        stringResource(
                                            R.string.broker_auto_sync_connected,
                                            ids.joinToString(),
                                        )
                                )
                            },
                            leading = {
                                ProviderIcon(
                                    institution?.iconName?.let {
                                        IconResolutionUtils.nameToResId(context, it).takeIf { id ->
                                            id != 0
                                        }
                                    }
                                )
                            },
                            trailing = {
                                TextButton(
                                    onClick = { onConnect(row.id) },
                                    enabled = !busy,
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.broker_auto_sync_connect))
                                }
                            },
                            shape = shape,
                            padding = PaddingValues(0.dp),
                        )
                    }
                    is BrokerageConnection ->
                        ListItem(
                            modifier = Modifier.testTag("broker_connection_${row.id}"),
                            onClick = { onOpenConnection(row) },
                            headline = { Text(row.label) },
                            supporting = {
                                val text =
                                    when {
                                        !row.hasCredentials ->
                                            stringResource(
                                                R.string.core_invest_missing_credentials_hint
                                            )
                                        row.syncedAt == null ->
                                            stringResource(
                                                R.string.core_invest_needs_first_refresh_hint
                                            )
                                        else ->
                                            row.accounts
                                                .joinToString { it.accountId }
                                                .ifBlank { row.providerId }
                                    }
                                Text(text)
                            },
                            leading = {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailing = {
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!row.hasCredentials) {
                                        TextButton(
                                            onClick = { onSetCredentials(row) },
                                            enabled = !busy,
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.core_invest_set_credentials
                                                )
                                            )
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { onRefresh(row) },
                                            enabled = !busy,
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Text(stringResource(R.string.core_invest_refresh))
                                        }
                                    }
                                    TextButton(
                                        onClick = { onDisconnect(row) },
                                        enabled = !busy,
                                        shapes = ButtonDefaults.shapes(),
                                    ) {
                                        Text(stringResource(R.string.investments_disconnect))
                                    }
                                }
                            },
                            shape = shape,
                            padding = PaddingValues(0.dp),
                        )
                }
            }
        }
        if (connections.isNotEmpty()) {
            PreferenceSwitch(
                title = stringResource(R.string.core_invest_daily_auto_sync_title),
                subtitle = stringResource(R.string.core_invest_daily_auto_sync_subtitle),
                checked = dailyAutoSyncEnabled,
                onCheckedChange = onSetDailyAutoSync,
                padding = PaddingValues(0.dp),
                isSingle = true,
            )
        }
        if (error != null) InvestmentError(error)
    }
}

@Composable
private fun ProviderIcon(iconResId: Int?) {
    if (iconResId != null) {
        Image(
            painterResource(iconResId),
            contentDescription = null,
            modifier =
                Modifier.size(40.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(4.dp),
        )
    } else {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null)
        }
    }
}

/**
 * The credentials form of a provider. A new provider adds its form here; one without a form says so
 * instead of guessing at fields.
 */
@Composable
internal fun BrokerConnectForm(
    providerId: String,
    busy: Boolean,
    error: CoreInvestmentFailure?,
    onDismiss: () -> Unit,
    onConnect: (label: String, fields: Map<String, String>) -> Unit,
) {
    val provider = BrokerProviderRegistry.find(providerId)
    if (provider != null) {
        provider.ConnectDialog(busy, error, onDismiss, onConnect)
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.broker_auto_sync_unsupported)) },
            confirmButton = {
                DialogConfirmButton(
                    text = stringResource(R.string.investments_cancel),
                    onClick = onDismiss,
                )
            },
            containerColor = HootbookDialogDefaults.containerColor,
        )
    }
}
