package io.github.hootkit_dev.hootbook.domain.usecase

import android.util.Log
import io.github.hootkit_dev.hootbook.data.database.entity.SubscriptionEntity
import io.github.hootkit_dev.hootbook.data.repository.SubscriptionRepository
import javax.inject.Inject

class UpdateSubscriptionUseCase
@Inject
constructor(private val subscriptionRepository: SubscriptionRepository) {
    suspend fun execute(subscription: SubscriptionEntity) {
        Log.d("UpdateSubscriptionUseCase", "Updating subscription entity: ${subscription.id}")
        subscriptionRepository.updateSubscription(subscription)
        Log.d("UpdateSubscriptionUseCase", "Subscription updated successfully.")
    }
}
