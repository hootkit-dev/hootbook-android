@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync.SyncUiState
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItem
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListItemPosition
import io.github.hootkit_dev.hootbook.presentation.ui.components.toShape
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync.SyncErrorKindRow
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync.SyncSetupSection
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.sync.SyncViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

/**
 * Turning on sync from the welcome step, in place of adding an account: sign-in, the
 * passphrase and, if this device already has data, the first-sync choice, as on the sync page.
 * A fresh device then holds the cloud's ledger (docs/sync.md, "First sync"), so it needs
 * neither a backup nor a first account; the main currency is asked here because preferences
 * do not sync.
 */
@Composable
internal fun SyncStep(
    syncState: SyncUiState,
    wrongPassphrase: Boolean,
    syncViewModel: SyncViewModel,
    accounts: Int,
    transactions: Int,
    selectedCurrency: String,
    onSelectCurrency: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.onboarding_sync_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.sync_intro),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SyncSetupSection(syncState, wrongPassphrase, syncViewModel) {
            SyncReady(syncState, accounts, transactions, selectedCurrency, onSelectCurrency)
        }
    }
}

/** Sync is on: what came down, any problem, and the main currency when accounts came. */
@Composable
private fun SyncReady(
    state: SyncUiState,
    accounts: Int,
    transactions: Int,
    selectedCurrency: String,
    onSelectCurrency: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.onboarding_sync_ready), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (accounts > 0) {
                        stringResource(
                            R.string.onboarding_sync_summary,
                            pluralStringResource(R.plurals.onboarding_sync_accounts, accounts, accounts),
                            pluralStringResource(R.plurals.onboarding_sync_transactions, transactions, transactions)
                        )
                    } else stringResource(R.string.onboarding_sync_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    state.status?.lastError?.let { SyncErrorKindRow(it) }
    if (accounts > 0) {
        ListItem(
            headline = { Text(stringResource(R.string.onboarding_sync_currency)) },
            supporting = { Text(stringResource(R.string.onboarding_sync_currency_body)) },
            trailing = {
                Text(
                    text = selectedCurrency,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            onClick = onSelectCurrency,
            shape = ListItemPosition.Single.toShape(),
            padding = PaddingValues(0.dp)
        )
    }
}
