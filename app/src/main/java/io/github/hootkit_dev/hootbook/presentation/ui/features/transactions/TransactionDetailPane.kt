package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ReceiptItem

/** The Transactions tab shows list and detail side by side from this window width. */
const val LIST_DETAIL_MIN_WIDTH_DP = 720

/**
 * The detail half of the Transactions tab on wide windows: the full transaction detail for
 * [transactionId], or a hint to pick one. Closing (back, delete) empties the pane.
 */
@Composable
fun TransactionDetailPane(
    transactionId: String?,
    onClose: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    blurEffects: Boolean
) {
    // One view model for the pane, apart from the full-screen detail route's
    val viewModel: TransactionDetailViewModel = hiltViewModel(key = "transactions_detail_pane")
    AnimatedContent(
        targetState = transactionId,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "TransactionDetailPane"
    ) { id ->
        if (id == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Iconax.ReceiptItem,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.transaction_pane_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                SharedTransitionLayout {
                    TransactionDetailScreen(
                        transactionId = id,
                        onNavigateBack = onClose,
                        onNavigateToPersonDetail = onNavigateToPersonDetail,
                        transactionDetailViewModel = viewModel,
                        blurEffects = blurEffects
                    )
                }
            }
        }
    }
}
