package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

/**
 * The currencies an account holds as chips, one selected, and optional chips to add one:
 * [suggested] adds that currency directly, [onAddOther] lets the user pick any.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountCurrencyChoice(
    currencies: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    suggested: String? = null,
    onAddSuggested: () -> Unit = {},
    onAddOther: (() -> Unit)? = null
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        currencies.forEach { currency ->
            FilterChip(selected = currency == selected, onClick = { onSelect(currency) }, label = { Text(currency) })
        }
        if (suggested != null) {
            AssistChip(
                onClick = onAddSuggested,
                label = { Text(stringResource(R.string.account_add_named_currency, suggested)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        if (onAddOther != null) {
            AssistChip(
                onClick = onAddOther,
                label = { Text(stringResource(R.string.account_add_currency)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

/** Asks before adding a currency to an account: it can't be removed afterwards. */
@Composable
fun AddCurrencyConfirmation(currency: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_add_currency_title, currency)) },
        text = { Text(stringResource(R.string.account_add_currency_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.account_add_currency_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
