package io.github.hootkit_dev.hootbook.presentation.ui.components

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import io.github.hootkit_dev.hootbook.presentation.format.toCoreDateTime
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddEditPersonSheet

import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Home dashboard widget for the Ledger feature. Shows only the lent/borrowed
 * summary tiles. Tapping a tile opens the Ledger screen with the corresponding
 * filter pre-selected; tapping the card opens it without a filter.
 */
@OptIn(ExperimentalHazeApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LendBorrowCard(
    summary: DebtSummary,
    people: List<Person> = emptyList(),
    onClick: () -> Unit,
    onLentClick: () -> Unit,
    onBorrowedClick: () -> Unit,
    modifier: Modifier = Modifier,
    currency: String = "CNY",
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
    animatedContentScope: AnimatedContentScope? = null
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    HootbookCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.Radius.xl))
            .then(
                if (blurEffects) Modifier.hazeEffect(
                    state = hazeState,
                    block = fun HazeEffectScope.() {
                        inputScale = HazeInputScale.Auto
                        style = HazeDefaults.style(
                            backgroundColor = Color.Transparent,
                            tint = HazeDefaults.tint(containerColor),
                            blurRadius = 20.dp,
                            noiseFactor = -1f,
                        )
                        blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                    }
                ) else Modifier
            ),
        onClick = onClick,
        shape = RoundedCornerShape(Dimensions.Radius.xl),
        colors = CardDefaults.cardColors(
            containerColor = if (blurEffects) containerColor.copy(alpha = Dimensions.Alpha.surface)
            else containerColor
        ),
        contentPadding = Dimensions.Padding.content
    ) {
        if (!summary.complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            LoanSummaryItem(
                title = stringResource(R.string.total_lent),
                amount = summary.receivable,
                persons = people.filter { it.totals.net.signum() > 0 },
                currency = currency,
                color = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.ArrowUpward,
                onClick = onLentClick,
                animatedContentScope = animatedContentScope,
                sharedElementKey = LoanSharedElementKeys.LENT,
                modifier = Modifier.weight(1f)
            )
            LoanSummaryItem(
                title = stringResource(R.string.total_borrowed),
                amount = summary.payable,
                persons = people.filter { it.totals.net.signum() < 0 },
                currency = currency,
                color = MaterialTheme.colorScheme.secondary,
                icon = Icons.Default.ArrowDownward,
                onClick = onBorrowedClick,
                animatedContentScope = animatedContentScope,
                sharedElementKey = LoanSharedElementKeys.BORROWED,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Home's one-line loans row: what others owe you and what you owe, each opening its list.
 * Home leaves it out while both are zero.
 */
@Composable
fun LendBorrowRow(
    summary: DebtSummary,
    people: List<Person> = emptyList(),
    currency: String,
    onClick: () -> Unit,
    onLentClick: () -> Unit,
    onBorrowedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().softShadow(shape)
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = stringResource(R.string.lend_borrow_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (summary.receivable.signum() != 0) {
                LoanAmountChip(stringResource(R.string.type_lent), summary.receivable, currency,
                    MaterialTheme.colorScheme.primary, Icons.Default.ArrowUpward, onLentClick)
            }
            if (summary.payable.signum() != 0) {
                LoanAmountChip(stringResource(R.string.type_borrowed), summary.payable, currency,
                    MaterialTheme.colorScheme.secondary, Icons.Default.ArrowDownward, onBorrowedClick)
            }
        }
    }
}

@Composable
private fun LoanAmountChip(
    label: String,
    amount: java.math.BigDecimal,
    currency: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.1f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
            Text(
                text = io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation.formatCurrency(amount, currency),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                maxLines = 1
            )
        }
    }
}
