package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.manager.NotificationScheduler
import io.github.hootkit_dev.hootbook.data.preferences.UserPreferencesRepository
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationScheduler: NotificationScheduler,
    subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    val upcomingNotificationsEnabled: StateFlow<Boolean> = userPreferencesRepository.upcomingNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Combine active subscriptions with disabled notification IDs
    val subscriptions: StateFlow<List<SubscriptionNotificationState>> = combine(
        subscriptionRepository.getActiveSubscriptions(),
        userPreferencesRepository.disabledSubscriptionNotificationIds
    ) { subscriptions, disabledIds ->
        subscriptions.map { subscription ->
            SubscriptionNotificationState(
                subscription = subscription,
                isNotificationEnabled = !disabledIds.contains(subscription.id.toString())
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setUpcomingNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUpcomingNotificationsEnabled(enabled)
            notificationScheduler.scheduleDailyReminder()
        }
    }

    fun toggleSubscriptionNotification(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.toggleSubscriptionNotification(id, enabled)
        }
    }
}

data class SubscriptionNotificationState(
    val subscription: SubscriptionEntity,
    val isNotificationEnabled: Boolean
)
