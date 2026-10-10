package io.github.hootkit_dev.hootbook.presentation.ui.features.subscriptions

import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import java.math.BigDecimal

data class SubscriptionsUiState(
    val activeSubscriptions: List<SubscriptionEntity> = emptyList(),
    val totalMonthlyAmount: BigDecimal = BigDecimal.ZERO,
    val totalYearlyAmount: BigDecimal = BigDecimal.ZERO,
    val targetCurrency: String = "CNY",
    val isLoading: Boolean = true,
    val lastHiddenSubscription: SubscriptionEntity? = null,
    val selectedSubscription: SubscriptionEntity? = null,
    val convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val conversionFailureCount: Int = 0
)
