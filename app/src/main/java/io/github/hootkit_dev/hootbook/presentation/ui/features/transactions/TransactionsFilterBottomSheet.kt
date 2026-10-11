@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.presentation.common.TransactionTypeFilter
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.AccountFilterChip
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.components.TypeFilterIcon
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CoreMoneyPresentation
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Category2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ReceiptEdit
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import io.github.hootkit_dev.hootbook.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransactionsFilterBottomSheet(
    viewModel: TransactionsViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val state by viewModel.uiState.collectAsState()
    val categoryFilter = state.filter.categoryIds
    val accountsFilter = state.filter.accountIds
    val currenciesFilter = listOfNotNull(state.filter.currency)
    val typeFilter = state.filter.kinds.mapNotNull { it.typeFilter() }
    val categoriesMap = state.categories
    val accountsMap = state.accounts
    val availableCurrencies = state.currencies
    val categoryList = categoriesMap.values.toList()

    HootbookModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.9f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 100.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Spacer(modifier = Modifier.height(Spacing.xl))
                    LazyRow(contentPadding = PaddingValues(horizontal = Dimensions.Padding.content), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(dev.hootkit.core.DeletedFilter.entries) { deleted ->
                            FilterChip(selected = state.filter.deleted == deleted,
                                onClick = { viewModel.setDeletedFilter(deleted) },
                                label = { Text(stringResource(when (deleted) {
                                    dev.hootkit.core.DeletedFilter.EXCLUDE -> R.string.core_full_label_49
                                    dev.hootkit.core.DeletedFilter.ONLY -> R.string.core_full_label_50
                                    dev.hootkit.core.DeletedFilter.INCLUDE -> R.string.core_full_label_51
                                })) })
                        }
                    }
                    //Transaction Types
                    LazyRow(
                        contentPadding = PaddingValues(
                            horizontal = Dimensions.Padding.content,
                            vertical = Spacing.sm
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(TransactionTypeFilter.entries) { type ->
                            FilterChip(
                                selected = if (type == TransactionTypeFilter.ALL) typeFilter.isEmpty() else typeFilter.contains(type),
                                onClick = { viewModel.setTransactionTypeFilter(type) },
                                leadingIcon = if (if (type == TransactionTypeFilter.ALL) typeFilter.isEmpty() else typeFilter.contains(type)) {
                                    { TypeFilterIcon(type) }
                                } else null,
                                label = { Text(stringResource(type.labelRes)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }
                    }
                    // Categories
                    if (categoryList.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.categories),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                horizontal = Dimensions.Padding.content,
                                vertical = Spacing.sm
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            item {
                                FilterCategoryItem(
                                    name = stringResource(R.string.all),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    iconVector = Iconax.Category2,
                                    isSelected = categoryFilter.isEmpty(),
                                    onClick = {
                                        viewModel.clearCategoryFilter()
                                    }
                                )
                            }
                            items(categoryList, key = { it.id }) { category ->
                                val color = try {
                                    Color(category.color.toColorInt())
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                FilterCategoryItem(
                                    name = category.displayName,
                                    color = color.copy(alpha = 0.2f),
                                    iconResId = 0,
                                    iconName = category.icon,
                                    iconVector = Iconax.Category2,
                                    isSelected = categoryFilter.contains(category.id),
                                    onClick = {
                                        viewModel.setCategoryFilter(category.id)
                                        // Optional: Clear subcategories that don't belong to any selected category anymore
                                        //  keep the subcategories selected until explicitly cleared
                                    }
                                )
                            }
                        }
                    }

                    // Accounts
                    if (accountsMap.isNotEmpty()) {
                        val context = LocalContext.current
                        val sortedFilterAccounts = remember(accountsMap) {
                            accountsMap.values.sortedWith(compareBy({ !it.isDefault }, { it.name }))
                        }
                        Text(
                            text = stringResource(R.string.accounts),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(
                            contentPadding = PaddingValues(
                                horizontal = Dimensions.Padding.content,
                                vertical = Spacing.sm
                            ),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            items(
                                sortedFilterAccounts,
                                key = { it.id }) { account ->
                                val accountKey = account.id
                                Box(modifier = Modifier.width(220.dp)) {
                                    AccountFilterChip(
                                        account = account,
                                        isSelected = accountsFilter.contains(accountKey),
                                        onClick = { viewModel.toggleAccountFilter(accountKey) }
                                    )
                            }
                        }
                    }

                    // Currencies
                    if (availableCurrencies.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.currencies),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(
                            contentPadding = PaddingValues(
                                horizontal = Dimensions.Padding.content,
                                vertical = Spacing.sm
                            ),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            items(availableCurrencies) { currencyCode ->
                                    val currency = remember(currencyCode) { runCatching { java.util.Currency.getInstance(currencyCode) }.getOrNull() }
                                    io.github.hootkit_dev.hootbook.presentation.ui.components.CurrencyCard(
                                        code = currencyCode, symbol = currency?.symbol ?: currencyCode,
                                        name = currency?.displayName ?: currencyCode,
                                        isSelected = currenciesFilter.contains(currencyCode),
                                        onCurrencyCardClick = { viewModel.toggleCurrencyFilter(currencyCode) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent,
                            )
                        )
                    )
                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.filters),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()

                )
            }

            // Action Buttons at Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Apply button
                    Button(
                        onClick = { onDismiss() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(
                            text = stringResource(R.string.apply),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Reset button
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = { viewModel.resetFilters() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraExtraLarge
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = "Reset to default",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun FilterCategoryItem(
    name: String,
    color: Color,
    iconResId: Int = 0,
    iconName: String? = null,
    iconVector: ImageVector? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    isSmall: Boolean = false
) {
    val context = LocalContext.current
    val size = if (isSmall) 54.dp else 66.dp
    val iconSize = if (isSmall) 44.dp else 58.dp

    val resolvedResId = remember(iconName, iconResId) {
        if (!iconName.isNullOrEmpty()) {
            val res = IconResolutionUtils.nameToResId(context, iconName)
            if (res != 0) res else IconResolutionUtils.getSafeResId(context, iconResId, 0)
        } else {
            IconResolutionUtils.getSafeResId(context, iconResId, 0)
        }
    }


    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(size + 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (resolvedResId != 0) {
                Icon(
                    painter = painterResource(id = resolvedResId),
                    contentDescription = name,
                    modifier = Modifier.size(iconSize).padding(4.dp),
                    tint =  Color.Unspecified
                )
            } else {
                Icon(
                    imageVector = iconVector ?: Iconax.Category2,
                    contentDescription = name,
                    modifier = Modifier.size(iconSize).padding(4.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Selection indicator badge
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(percent = 50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.padding(2.dp).fillMaxSize()
                    )
                }
            }
        }

        Text(
            text = name,
            style = if (isSmall) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                repeatDelayMillis = 1000,
            )
        )
    }
}
