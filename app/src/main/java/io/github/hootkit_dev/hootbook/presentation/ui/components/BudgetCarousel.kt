package io.github.hootkit_dev.hootbook.presentation.ui.components

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.ui.features.budgets.budgetProgressFraction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope

/**
 * Carousel component for displaying multiple budgets.
 * Shows single budget as full-width card, multiple budgets as scrollable carousel.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.BudgetCarousel(
    budgets: List<Budget>,
    onBudgetClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
    onHistoryClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val isTransitioning = animatedVisibilityScope?.transition?.let {
        it.currentState != it.targetState
    } ?: false


    if (budgets.isEmpty()) return

    if (budgets.size == 1) {
        // Single budget - show full-width card
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            BudgetCard(
                budgetWithSpending = budgets.first(),
                onClick = { onBudgetClick(budgets.first().id) },
                onHistoryClick = onHistoryClick,
                modifier = Modifier.fillMaxWidth(),
                animatedVisibilityScope = animatedVisibilityScope,
                sharedElementKey = "budget_card_${budgets.first().id}"
            )
        }
    } else {
        // Multiple budgets - show as carousel with snapping
        val pagerState = rememberPagerState(pageCount = { budgets.size })

        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            userScrollEnabled = !isTransitioning
        ) { page ->
            val budgetWithSpending = budgets[page]
            BudgetCardCompact(
                budgetWithSpending = budgetWithSpending,
                onClick = { onBudgetClick(budgetWithSpending.id) },
                onHistoryClick = onHistoryClick,
                modifier = Modifier.fillMaxWidth(),
                animatedVisibilityScope = animatedVisibilityScope,
                sharedElementKey = "budget_card_${budgetWithSpending.id}"
            )
        }
    }
}
