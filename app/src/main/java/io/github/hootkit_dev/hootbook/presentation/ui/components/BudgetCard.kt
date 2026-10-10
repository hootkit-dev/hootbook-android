package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ripple
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import io.github.hootkit_dev.hootbook.presentation.ui.theme.MotionDurations

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.github.hootkit_dev.hootbook.data.repository.BudgetWithSpending
import io.github.hootkit_dev.hootbook.presentation.ui.icons.History
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter
import java.math.BigDecimal

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedTransitionScope.BudgetCard(
    modifier: Modifier = Modifier,
    budgetWithSpending: BudgetWithSpending,
    onClick: () -> Unit = {},
    onHistoryClick: (Long) -> Unit = {},
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedElementKey: String? = null
) {
    val budget = budgetWithSpending.budget
    val isSavings = budget.budgetType == io.github.hootkit_dev.hootbook.data.database.entity.BudgetType.SAVINGS
    
    // Animate the progress
    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val targetProgress = budgetWithSpending.percentUsed
    val animatedProgressState by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 800),
        label = "progressAnimation"
    )
    
    LaunchedEffect(targetProgress) {
        animatedProgress = targetProgress
    }
    
    // Determine colors based on spending status
    val budgetColor = try {
        Color(budget.color.toColorInt())
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val progressColor = when {
        isSavings -> {
            if (budgetWithSpending.isOverBudget) budgetColor else budgetColor.copy(alpha = 0.8f)
        }
        budgetWithSpending.isOverBudget -> MaterialTheme.colorScheme.error
        budgetWithSpending.percentUsed > 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> budgetColor
    }
    
    val progressBackgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    
    val sharedModifier = if (animatedVisibilityScope != null && sharedElementKey != null) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = sharedElementKey),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ ->
                tween(durationMillis = MotionDurations.standard, easing = FastOutSlowInEasing)
            },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
        )
    } else {
        Modifier
    }

    BudgetAnimatedGradientMeshCard(
        budgetColor = budgetColor,
        modifier = modifier
            .then(sharedModifier)
            .clickable(
                onClick = onClick,
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Dot
                Icon(
                    imageVector = Icons.Rounded.Api,
                    contentDescription = null,
                    tint = progressColor,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))
                
                // Budget Name
                Text(
                    text = io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.budgetDisplayName(budget.name, budget.periodType),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // History Icon
                IconButton(
                    onClick = { onHistoryClick(budget.id) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    shapes =  IconButtonDefaults.shapes(),
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Iconax.History,
                        contentDescription = stringResource(R.string.history),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.sm))
            
            // Main Content Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(if (isSavings) R.string.daily_goal_remaining else R.string.daily_budget_left),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    
                    val dailyBudgetLeft = if (budgetWithSpending.isOverBudget || budgetWithSpending.daysRemaining <= 0) {
                         BigDecimal.ZERO 
                    } else {
                        budgetWithSpending.recommendedDailySpending
                    }
                    
                    Text(
                        text = CurrencyFormatter.formatCurrency(
                            dailyBudgetLeft,
                            budget.currency
                        ),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(if (isSavings) R.string.saved_goal else R.string.spend_limit),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                         Text(
                            text = CurrencyFormatter.formatCurrency(
                                budgetWithSpending.currentSpending,
                                budget.currency
                            ).replace(".00", ""), // Simplified display
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                             maxLines = 1,
                             overflow = TextOverflow.Ellipsis,
                             modifier = Modifier.basicMarquee()
                        )
                        
                        Text(
                            text = " / ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        
                        Text(
                            text = CurrencyFormatter.formatCurrency(
                                budget.amount,
                                budget.currency
                            ).replace(".00", ""), // Simplified display
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(progressBackgroundColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgressState.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(progressColor)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.days_remaining_format, budgetWithSpending.daysRemaining),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
internal fun BudgetAnimatedGradientMeshCard(
    budgetColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Static blob positions. The former eight infinite animations behind a 60dp blur kept every
    // budget card re-rendering an offscreen blur pass on each frame.
    val primaryBlob = budgetColor.copy(alpha = 0.12f)
    val secondaryBlob = budgetColor.copy(alpha = 0.08f)

    val cardBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        border = BorderStroke(1.dp, budgetColor.copy(0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .blur(60.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            ) {
                val animatedColor = primaryBlob
                val animatedSecondaryColor = secondaryBlob
                val offsetX1 = 0.3f
                val offsetY1 = 0.3f
                val offsetX2 = 0.7f
                val offsetY2 = 0.8f
                val offsetX3 = 0.75f
                val scale3 = 0.5f
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                // Blob 1
                drawCircle(
                    color = animatedColor,
                    center = Offset(x = canvasWidth * offsetX1, y = canvasHeight * offsetY1),
                    radius = canvasWidth * 0.5f
                )
                
                // Blob 2
                drawCircle(
                    color = animatedSecondaryColor,
                    center = Offset(x = canvasWidth * offsetX2, y = canvasHeight * offsetY2),
                    radius = canvasWidth * 0.5f
                )
                
                 // Blob 3
                drawCircle(
                    color = animatedColor.copy(alpha = animatedColor.alpha * 0.8f),
                    center = Offset(x = canvasWidth * offsetX3, y = canvasHeight * 0.2f),
                    radius = canvasWidth * scale3
                )
            }

            content()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.BudgetCardCompact(
    budgetWithSpending: BudgetWithSpending,
    onClick: () -> Unit,
    onHistoryClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedElementKey: String? = null
) {
   BudgetCard(
       budgetWithSpending = budgetWithSpending,
       onClick = onClick,
       onHistoryClick = onHistoryClick,
       modifier = modifier.width(300.dp), // Fixed width for carousel
       animatedVisibilityScope = animatedVisibilityScope,
       sharedElementKey = sharedElementKey
   )
}
