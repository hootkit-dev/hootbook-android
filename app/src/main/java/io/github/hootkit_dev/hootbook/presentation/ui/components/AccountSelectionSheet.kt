package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.AccountBalanceEntity
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

/**
 * Picker for an account: compact [AccountRow]s grouped by type like the Accounts screen,
 * with accounts of the same bank next to each other and the main account first. Hidden
 * accounts are left out unless one is the current selection.
 */
@Composable
fun AccountSelectionSheet(
    accounts: List<AccountBalanceEntity>,
    selectedAccount: AccountBalanceEntity?,
    title: String = stringResource(R.string.select_account),
    onAccountSelected: (AccountBalanceEntity?) -> Unit,
    isTransitioning: Boolean = false,
    showNoneOption: Boolean = true
) {
    val context = LocalContext.current
    val groups = remember(accounts, selectedAccount) {
        accounts.groupedForDisplay(context.mainAccountKey())
    }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 12.dp).fillMaxWidth()
        )

        if (groups.isEmpty() && !showNoneOption) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_accounts_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                userScrollEnabled = !isTransitioning
            ) {
                if (showNoneOption) {
                    item {
                        Surface(
                            onClick = { onAccountSelected(null) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selectedAccount == null) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.none_manual_entry),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = Spacing.md, vertical = Spacing.md)
                            )
                        }
                    }
                }
                groups.forEach { (category, members) ->
                    item(key = "header:$category") { AccountGroupHeader(category) }
                    items(members, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            selected = selectedAccount?.id == account.id,
                            onClick = { onAccountSelected(account) }
                        )
                    }
                }
            }
        }
    }
}
