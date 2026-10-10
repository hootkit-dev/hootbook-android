package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions

@Composable
fun TypeFilterIcon(typeFilter: TransactionTypeFilter) {
    when (typeFilter) {
        TransactionTypeFilter.INCOME -> Icon(
            Icons.AutoMirrored.Rounded.TrendingUp,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        TransactionTypeFilter.EXPENSE -> Icon(
            Icons.AutoMirrored.Rounded.TrendingDown,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        TransactionTypeFilter.CREDIT -> Icon(
            Icons.Rounded.CreditCard,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        TransactionTypeFilter.TRANSFER -> Icon(
            Icons.Rounded.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        TransactionTypeFilter.INVESTMENT -> Icon(
            Icons.AutoMirrored.Rounded.ShowChart,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        TransactionTypeFilter.LENT -> Icon(
            Icons.AutoMirrored.Rounded.TrendingDown,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        TransactionTypeFilter.BORROWED -> Icon(
            Icons.AutoMirrored.Rounded.TrendingUp,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.Icon.small),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> {}
    }
}
