package io.github.hootkit_dev.hootbook.presentation.ui.components

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import io.github.hootkit_dev.hootbook.presentation.format.toCoreDateTime
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.ui.components.AddEditPersonSheet

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import java.math.BigDecimal

/**
 * Loan-style Ledger summary card (32dp radius) used on the home dashboard.
 * Uses a flat surfaceContainerLow fill so it stays theme-aware in both light
 * and dark mode.
 */
@OptIn(ExperimentalHazeApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LoanBalanceCard(
    summary: DebtSummary,
    people: List<Person> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    currency: String = "CNY",
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() },
    animatedContentScope: AnimatedContentScope? = null,
    footer: @Composable ColumnScope.() -> Unit = {}
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
        LoanBalanceContent(summary = summary,
                people = people, currency = currency, animatedContentScope = animatedContentScope)
        footer()
    }
}

/**
 * The inner, card-less Ledger balance content. Shared by the home widget and the
 * Ledger overview card so both stay visually identical.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LoanBalanceContent(
    summary: DebtSummary,
    people: List<Person> = emptyList(),
    currency: String,
    modifier: Modifier = Modifier,
    animatedContentScope: AnimatedContentScope? = null
) {
    val netColor = when {
        summary.net > BigDecimal.ZERO -> MaterialTheme.colorScheme.tertiary
        summary.net < BigDecimal.ZERO -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }

    val statusText = when {
        summary.net > BigDecimal.ZERO -> stringResource(R.string.lend_borrow_you_get)
        summary.net < BigDecimal.ZERO -> stringResource(R.string.lend_borrow_you_owe)
        else -> stringResource(R.string.lend_borrow_settled)
    }

    val balanceStyle = MaterialTheme.typography.displayMedium.copy(
        fontSize = Dimensions.TextSize.balance,
        fontWeight = FontWeight.Bold
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LoanStatusPill(text = statusText, color = netColor)
        }

        if (!summary.complete) Text(stringResource(R.string.core_debts_incomplete), color = MaterialTheme.colorScheme.error)

        val (whole, decimal) = remember(summary.net, currency) {
            splitCurrencyParts(summary.net.abs(), currency)
        }
        val amountIsZero = summary.net.abs() == BigDecimal.ZERO
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = whole,
                style = balanceStyle,
                color = if (amountIsZero) MaterialTheme.colorScheme.onSurface.copy(alpha = Dimensions.Alpha.faint)
                else MaterialTheme.colorScheme.onSurface
            )
            if (decimal.isNotEmpty()) {
                val decimalStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = Dimensions.TextSize.display,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = decimal,
                    style = decimalStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Dimensions.Alpha.faint) ,
                    modifier = Modifier.padding(bottom = 4.dp)

                )
            }
        }
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
                animatedContentScope = animatedContentScope,
                sharedElementKey = LoanSharedElementKeys.BORROWED,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LoanSummaryItem(
    title: String,
    amount: BigDecimal,
    persons: List<Person>,
    attachmentStore: CoreAttachmentStore? = null,
    currency: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    animatedContentScope: AnimatedContentScope? = null,
    sharedElementKey: String? = null
) {
    val sharedModifier = if (animatedContentScope != null && sharedElementKey != null) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = sharedElementKey),
            animatedVisibilityScope = animatedContentScope,
            boundsTransform = { _, _ ->
                tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .then(sharedModifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(Dimensions.Radius.lg),
        color = color.copy(alpha = 0.2f),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Surface(
                    color = color.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = CurrencyFormatter.formatCurrency(amount, currency),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                color = color,
                modifier = Modifier.align(Alignment.Start)
            )

            PersonIconsStack(
                persons = persons,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun PersonIconsStack(
    persons: List<Person>,
    attachmentStore: CoreAttachmentStore? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    maxIcons: Int = 4
) {
    if (persons.isEmpty()) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val store = attachmentStore ?: remember(context) { CoreAttachmentStore(context.applicationContext) }
    val displayCount = if (persons.size > maxIcons) maxIcons else persons.size
    val remainingCount = persons.size - maxIcons

    val totalCircles = if (remainingCount > 0) displayCount + 1 else displayCount
    val outerIconSize = iconSize + 4.dp

    val totalWidth = if (totalCircles > 1) {
        (iconSize * (totalCircles - 1).toFloat() * 0.55f) + outerIconSize
    } else {
        outerIconSize
    }

    Box(modifier = modifier.width(totalWidth)) {
        persons.take(displayCount).forEachIndexed { index, person ->
            val overlapOffset = (iconSize * index.toFloat() * 0.55f)

            Box(
                modifier = Modifier
                    .offset(x = overlapOffset)
                    .zIndex(index.toFloat())
                    .size(outerIconSize)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
//                    .padding(2.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        try {
                            Color(person.color.toColorInt())
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (person.avatar != null) {
                    AsyncImage(
                        model = person.avatar?.let { store.resolve(it) },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = person.name.take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (remainingCount > 0) {
            val overlapOffset = (iconSize * displayCount.toFloat() * 0.55f)
            Box(
                modifier = Modifier
                    .offset(x = overlapOffset)
                    .zIndex(displayCount.toFloat())
                    .size(outerIconSize)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$remainingCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LoanStatusPill(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/**
 * Splits a formatted currency string into the whole and decimal portions so the
 * balance headline can de-emphasize the cents. Handles locale grouping and
 * symbol placement (e.g. "1.234,56 €" vs "₹1,234.56") by splitting on the last
 * grouping/decimal separator.
 */
private fun splitCurrencyParts(amount: BigDecimal, currency: String): Pair<String, String> {
    val full = CurrencyFormatter.formatCurrency(amount, currency)
    val lastDot = full.lastIndexOf('.')
    val lastComma = full.lastIndexOf(',')
    val decimalIndex = maxOf(lastDot, lastComma)

    // Only split if the separator is near the end, suggesting it's a decimal separator
    return if (decimalIndex > 0 && decimalIndex >= full.length - 3) {
        full.substring(0, decimalIndex) to full.substring(decimalIndex)
    } else {
        full to ""
    }
}

/**
 * Shared element keys used for the loan/ledger summary tiles so the home widget
 * and the Ledger screen's summary header can animate between each other.
 */
object LoanSharedElementKeys {
    const val LENT = "loan_tile_lent"
    const val BORROWED = "loan_tile_borrowed"
}

@Preview(showBackground = true)
@Composable
private fun LoanBalanceCardPreview() {
    HootbookTheme {
        SharedTransitionLayout {
            LoanBalanceCard(summary = DebtSummary("CNY", BigDecimal("135"), BigDecimal("5"), BigDecimal("130"), 2u, 1u, 1u, 0u, true),
                people = emptyList(), currency = "CNY", onClick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoanBalanceCardDarkPreview() {
    HootbookTheme(darkTheme = true) {
        SharedTransitionLayout {
            LoanBalanceCard(summary = DebtSummary("CNY", BigDecimal("135"), BigDecimal("5"), BigDecimal("130"), 2u, 1u, 1u, 0u, true),
                people = emptyList(), currency = "CNY", onClick = {})
        }
    }
}
