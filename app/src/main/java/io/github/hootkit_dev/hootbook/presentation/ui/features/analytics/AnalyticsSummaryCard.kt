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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hootkit.core.Category
import dev.hootkit.core.LedgerSummary
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.expense_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_dark
import io.github.hootkit_dev.hootbook.presentation.ui.theme.income_light
import io.github.hootkit_dev.hootbook.presentation.ui.theme.isAppInDarkTheme
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
    val currency = displayCurrency ?: summary.currency
    val incomeAmount = original?.income ?: summary.income
    val expenseAmount = original?.expense ?: summary.expense
    val netAmount = if (original != null) original.income - original.expense else summary.net

    val alpha by
        animateFloatAsState(
            targetValue = if (isLoading) 0.5f else 1f,
            animationSpec = tween(300),
            label = "summary_alpha",
        )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(Spacing.lg),
                )
                .padding(Spacing.sm)
        ) {
            // Transaction count header
            Text(
                text = stringResource(R.string.core_analytics_count, transactionCount.toString()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.sm)
            )

            // 3-Column nested rounded tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Income Column
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(
                                topStart = Spacing.md,
                                bottomStart = Spacing.md,
                                topEnd = Spacing.xs,
                                bottomEnd = Spacing.xs
                            )
                        )
                        .padding(Spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    TotalColumn(
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = stringResource(R.string.income),
                                modifier = Modifier.size(18.dp),
                                tint = if (!isAppInDarkTheme) income_light else income_dark
                            )
                        },
                        label = stringResource(R.string.income),
                        amount = CurrencyFormatter.formatCurrency(incomeAmount, currency),
                        color = if (!isAppInDarkTheme) income_light else income_dark
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 0.5.dp),
                    color = Color.Transparent
                )

                // Expense Column
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(Spacing.xs)
                        )
                        .padding(Spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    TotalColumn(
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = stringResource(R.string.expenses),
                                modifier = Modifier.size(18.dp),
                                tint = if (!isAppInDarkTheme) expense_light else expense_dark
                            )
                        },
                        label = stringResource(R.string.expenses),
                        amount = CurrencyFormatter.formatCurrency(expenseAmount, currency),
                        color = if (!isAppInDarkTheme) expense_light else expense_dark
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 0.5.dp),
                    color = Color.Transparent
                )

                // Net Column
                val netColor = when {
                    netAmount > BigDecimal.ZERO -> if (!isAppInDarkTheme) income_light else income_dark
                    netAmount < BigDecimal.ZERO -> if (!isAppInDarkTheme) expense_light else expense_dark
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val netPrefix = if (netAmount > BigDecimal.ZERO) "+" else ""

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(
                                topStart = Spacing.xs,
                                bottomStart = Spacing.xs,
                                topEnd = Spacing.md,
                                bottomEnd = Spacing.md
                            )
                        )
                        .padding(Spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    TotalColumn(
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.SettingsEthernet,
                                contentDescription = stringResource(R.string.net),
                                modifier = Modifier.size(18.dp),
                                tint = netColor
                            )
                        },
                        label = stringResource(R.string.net),
                        amount = "$netPrefix${CurrencyFormatter.formatCurrency(netAmount, currency)}",
                        color = netColor
                    )
                }
            }

            if (!summary.complete) {
                Text(
                    stringResource(
                        R.string.core_complete_missing,
                        summary.missingCurrencies.joinToString(),
                    ),
                    modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TotalColumn(
    icon: @Composable (() -> Unit)?,
    label: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        )
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
