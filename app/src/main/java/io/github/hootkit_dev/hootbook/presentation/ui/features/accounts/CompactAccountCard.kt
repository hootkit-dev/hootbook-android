package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.AccountSurfaceElevation
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountRowContent

/** Compact account list treatment; detailed actions and supplementary content stay available. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CompactAccountCard(
    account: Account,
    isMain: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    // Everything else about an account (balance, details, history, delete) is on its page
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = AccountSurfaceElevation)
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountRowContent(account, note = if (isMain) stringResource(R.string.default_account_note) else null)
        }
        content()
    }
}
