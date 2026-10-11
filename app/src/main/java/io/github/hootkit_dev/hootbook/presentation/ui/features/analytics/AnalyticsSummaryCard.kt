package io.github.hootkit_dev.hootbook.presentation.ui.features.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hootkit.core.Category
import dev.hootkit.core.LedgerSummary
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategoryIcon
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Receipt1
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import java.math.BigDecimal

@Composable
fun AnalyticsSummaryCard(
    modifier: Modifier = Modifier,
    summary: LedgerSummary,
    transactionCount: UInt,
    incomeOnly: Boolean = false,
    displayCurrency: String? = null,
    topCategory: Category? = null,
    showIncome: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit = {},
) {
    val original =
        displayCurrency?.let { currency -> summary.perCurrency.find { it.currency == currency } }
    val totalAmount =
        if (displayCurrency != null) {
            if (incomeOnly) original?.income else original?.expense
        } else if (incomeOnly) summary.income else summary.expense
    val incomeAmount =
        (if (displayCurrency != null) original?.income else summary.income).takeIf { showIncome }
    val currency = displayCurrency ?: summary.currency
    val alpha by
        animateFloatAsState(
            targetValue = if (isLoading) 0.5f else 1f,
            animationSpec = tween(300),
            label = "summary_alpha",
        )

    Box(modifier = modifier.fillMaxWidth().alpha(alpha).clickable(onClick = onClick)) {
        // Card Content layer
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(Dimensions.Radius.md),
                    )
                    .padding(Dimensions.Padding.content)
        ) {
            // Top Row - Total Spent and Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(
                                if (incomeAmount != null) R.string.analytics_spent
                                else R.string.total_uppercase
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyFormatter.format(totalAmount, currency),
                        style =
                            MaterialTheme.typography.displaySmall.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    incomeAmount?.let {
                        Text(
                            text =
                                stringResource(
                                    R.string.analytics_income_beside,
                                    CurrencyFormatter.formatCurrency(it, currency),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                io.github.hootkit_dev.hootbook.presentation.ui.theme.moneyColors
                                    .income,
                        )
                    }
                }
                // Transaction Count Box
                Box(
                    modifier =
                        Modifier.background(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(Spacing.sm),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .align(Alignment.Bottom)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Iconax.Receipt1,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.transactions_format,
                                    transactionCount.toLong(),
                                ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            topCategory?.let { category ->
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(
                    Modifier.fillMaxWidth(),
                    thickness = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                )
                Spacer(Modifier.height(24.dp))
                val color =
                    runCatching { Color(android.graphics.Color.parseColor(category.color)) }
                        .getOrNull() ?: MaterialTheme.colorScheme.primary
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(
                        Modifier.background(color.copy(0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryIcon(category, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            category.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (!summary.complete)
                Text(
                    stringResource(
                        R.string.core_complete_missing,
                        summary.missingCurrencies.joinToString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AnalyticsSummaryCardPreview() {
    HootbookTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AnalyticsSummaryCard(
                summary =
                    LedgerSummary(
                        "CNY",
                        BigDecimal.ZERO,
                        BigDecimal("9730"),
                        BigDecimal("-9730"),
                        true,
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                    ),
                transactionCount = 5u,
            )
        }
    }
}
