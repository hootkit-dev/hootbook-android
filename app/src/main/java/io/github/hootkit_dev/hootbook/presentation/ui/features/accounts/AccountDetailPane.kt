package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.hootkit_dev.hootbook.R

/** The accounts list shows the chosen account beside it from this window width. */
const val ACCOUNT_LIST_DETAIL_MIN_WIDTH_DP = 720

/**
 * The detail half of an account list on wide windows: [account]'s detail screen, or a hint to
 * pick one. Each account gets its own view model, created with the route arguments the
 * full-screen account detail would have.
 */
@Composable
fun AccountDetailPane(
    accountId: String?,
    onTransaction: (String) -> Unit,
    onBrokerage: (Long, String) -> Unit,
    animatedContentScope: AnimatedVisibilityScope?,
    onClose: () -> Unit,
) {
    if (accountId == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.account_pane_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        return
    }
    key(accountId) {
        val owner = checkNotNull(LocalViewModelStoreOwner.current)
        val defaults = owner as HasDefaultViewModelProviderFactory
        val viewModel: AccountDetailViewModel = viewModel(
            viewModelStoreOwner = owner,
            key = "account_pane_${accountId}",
            factory = HiltViewModelFactory(LocalContext.current, defaults.defaultViewModelProviderFactory),
            extras = MutableCreationExtras(defaults.defaultViewModelCreationExtras).apply {
                set(DEFAULT_ARGS_KEY, bundleOf("accountId" to accountId))
            }
        )
        SharedTransitionLayout {
            AccountDetailScreen(
                accountId = accountId,
                onTransaction = onTransaction,
                onBrokerage = onBrokerage,
                accountDetailViewModel = viewModel,
                animatedContentScope = animatedContentScope,
                onNavigateBack = onClose,
            )
        }
    }
}
