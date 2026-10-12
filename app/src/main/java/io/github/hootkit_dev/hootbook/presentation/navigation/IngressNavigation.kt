package io.github.hootkit_dev.hootbook.presentation.navigation

import androidx.navigation.NavHostController
import io.github.hootkit_dev.hootbook.core.CoreIngressTarget
import io.github.hootkit_dev.hootbook.widget.QuickEntry

/** Intent parsing and attachment ownership stay in CoreIntentIngress; only routing lives here. */
internal fun CoreIngressTarget.destination(): Any = when (this) {
    CoreIngressTarget.Ai -> AiAssistant
    is CoreIngressTarget.Quick -> when (val quick = entry) {
        QuickEntry.Entry.Home -> Home
        QuickEntry.Entry.NewTransaction -> AddTransaction()
        QuickEntry.Entry.NewTransfer -> AddTransaction(type = "TRANSFER")
        QuickEntry.Entry.NewSubscription -> AddTransaction(initialTab = 1)
        is QuickEntry.Entry.Template -> AddTransaction(templateId = quick.id)
        is QuickEntry.Entry.Subscription -> Subscriptions(subscriptionId = quick.id)
    }
}

/** Returns false while onboarding owns the stack, leaving the persisted ingress queued. */
internal fun NavHostController.routeIngress(target: CoreIngressTarget, onboardingFinished: Boolean): Boolean {
    if (!onboardingFinished || currentBackStackEntry == null) return false
    val route = target.destination()
    if (route == Home) selectMainTab(BottomNavItem.Home)
    else navigate(route) { launchSingleTop = true }
    return true
}

/** String callbacks belong to the temporary Core settings/onboarding screens only. */
internal fun coreScreenDestination(route: String): Any = when (route) {
    "accounts" -> ManageAccounts
    "categories" -> Categories
    "ai" -> AiAssistant
    "investments" -> Investments()
    "sync" -> CloudSync
    "appearance" -> Appearance
    "profile" -> Profile
    "app-lock" -> AppLockSettings
    "notifications" -> NotificationSettings
    "about" -> About
    "backup" -> DataPrivacy
    else -> error("Unknown Core screen destination: $route")
}
