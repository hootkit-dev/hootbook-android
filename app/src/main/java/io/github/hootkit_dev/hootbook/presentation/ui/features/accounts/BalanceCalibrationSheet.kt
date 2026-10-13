@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountCurrencyChoice
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import java.math.BigDecimal

/**
 * Sets an account's balance (a card's amount owed) to what the bank shows. An account holding
 * several currencies is set one currency at a time, starting with its main one.
 */
@Composable
fun BalanceCalibrationSheet(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (balance: BigDecimal, currency: String) -> Unit
) {
    HootbookModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        val pockets = account.pockets
        var currency by remember(account.id) { mutableStateOf(account.mainCurrency) }
        val current = pockets.firstOrNull { it.currency == currency }?.balance ?: account.pockets.first { it.currency == account.mainCurrency }.balance
        val title = stringResource(if ((account.kind == AccountKind.CREDIT_CARD)) R.string.update_outstanding_title else R.string.balance_calibration)
        Column {
            if (pockets.size > 1) {
                AccountCurrencyChoice(
                    currencies = listOf(account.mainCurrency) + pockets.map { it.currency }.filter { it != account.mainCurrency },
                    selected = currency,
                    onSelect = { currency = it },
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }
            key(currency) {
                NumberPad(
                    initialValue = current.toPlainString(),
                    title = title,
                    bankName = account.name,
                    accountLast4 = account.last4.orEmpty(),
                    doneButtonLabel = title,
                    onDone = { value ->
                        value.toBigDecimalOrNull()?.let { onSave(it, currency) }
                    }
                )
            }
        }
    }
}
