package io.github.hootkit_dev.hootbook.presentation.ui.features.subscriptions

import dev.hootkit.core.*
import java.math.BigDecimal

data class SubscriptionsUiState(
    val activeSubscriptions: List<Subscription> = emptyList(),
    val summary: SubscriptionSummary? = null,
    val categories: Map<String, Category> = emptyMap(),
    val subcategories: Map<String, Subcategory> = emptyMap(),
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val lastHiddenSubscription: Subscription? = null,
    val selectedSubscription: Subscription? = null,
    val convertedMonthlyAmounts: Map<String, BigDecimal> = emptyMap(),
    val convertedAmounts: Map<String, BigDecimal> = emptyMap(),
    val error: String? = null,
)
