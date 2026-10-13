package io.github.hootkit_dev.hootbook.presentation.navigation

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.core.CoreIngressTarget
import io.github.hootkit_dev.hootbook.widget.QuickEntry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class HootbookNavigationTest {
    private fun nav(onboarded: Boolean = true): NavHostController {
        val nav = NavHostController(ApplicationProvider.getApplicationContext<Application>())
        nav.setViewModelStore(ViewModelStore())
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        nav.setLifecycleOwner(owner)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = OnBoarding) {
            composable<OnBoarding> { }
            composable<Home> { }
            composable<Analytics> { }
            composable<Transactions> { }
            composable<AddTransaction> { }
            composable<TransactionDetail> { }
            composable<AccountDetail> { }
            composable<Subscriptions> { }
            composable<CurrencySettings> { }
            composable<Settings> { }
            composable<Profile> { }
            composable<Appearance> { }
            composable<AppLockSettings> { }
            composable<NotificationSettings> { }
            composable<About> { }
            composable<CloudSync> { }
            composable<Investments> { }
            composable<ManageAccounts> { }
            composable<AiAssistant> { }
        }
        if (onboarded) nav.navigate(Home) { popUpTo<OnBoarding> { inclusive = true } }
        nav.settle()
        return nav
    }

    private fun NavHostController.settle() {
        val navigator = navigatorProvider.getNavigator<ComposeNavigator>("composable")
        currentBackStack.value.forEach(navigator::onTransitionComplete)
    }

    private inline fun <reified T : Any> NavHostController.assertRoute(expected: T) {
        settle()
        assertEquals(expected, currentBackStackEntry!!.toRoute<T>())
    }

    @Test fun homeIsAnchorAfterOnboardingAndRepeatedHomeDoesNotDuplicate() {
        val nav = nav()
        nav.navigate(Profile)
        nav.settle()
        repeat(3) {
            nav.selectMainTab(BottomNavItem.Home)
            nav.assertRoute(Home)
        }
        assertFalse(nav.popBackStack())
    }

    @Test fun allSettingsChildrenReturnToHomeWithoutRestoringAChildStack() {
        val nav = nav()
        listOf(Profile, Appearance, AppLockSettings, NotificationSettings, About, CloudSync, Investments()).forEach { child ->
            nav.navigate(Settings)
            nav.navigate(child)
            nav.settle()
            nav.selectMainTab(BottomNavItem.Home)
            nav.assertRoute(Home)
        }
        nav.selectMainTab(BottomNavItem.Transactions)
        nav.assertRoute(Transactions())
        nav.selectMainTab(BottomNavItem.Analytics)
        nav.assertRoute(Analytics)
        nav.selectMainTab(BottomNavItem.Home)
        nav.assertRoute(Home)
        assertFalse(nav.popBackStack())
    }

    @Test fun childBackAndSavedTabsRemainUsable() {
        val nav = nav()
        nav.navigate(Settings)
        nav.navigate(Profile)
        assertTrue(nav.popBackStack())
        nav.assertRoute(Settings)
        repeat(2) {
            nav.selectMainTab(BottomNavItem.Transactions)
            nav.assertRoute(Transactions())
            nav.selectMainTab(BottomNavItem.Analytics)
            nav.assertRoute(Analytics)
            nav.selectMainTab(BottomNavItem.Home)
            nav.assertRoute(Home)
        }
    }

    @Test fun contextualTransactionsResetWhenSelectingTheTransactionsTab() {
        val nav = nav()
        nav.navigate(Transactions(accountId = "account/opaque", categoryId = "category/opaque"))
        nav.settle()
        nav.selectMainTab(BottomNavItem.Transactions)
        nav.assertRoute(Transactions())
        nav.selectMainTab(BottomNavItem.Home)
        nav.assertRoute(Home)
    }

    @Test fun opaqueStringIdsAndCurrencySurviveActualTypedNavigation() {
        val nav = nav()
        val id = "001 /?&x=1#片+😀%2F"
        val transactions = Transactions(accountId = id, categoryId = id, subscriptionId = id)
        nav.navigate(transactions)
        nav.assertRoute(transactions)
        val add = AddTransaction(subscriptionId = id, templateId = id)
        nav.navigate(add)
        nav.assertRoute(add)
        nav.navigate(TransactionDetail(id))
        nav.assertRoute(TransactionDetail(id))
        nav.navigate(AccountDetail(id, history = true))
        nav.assertRoute(AccountDetail(id, history = true))
        nav.navigate(Subscriptions(id))
        nav.assertRoute(Subscriptions(id))
        nav.navigate(CurrencySettings("X /?&currency=JPY#中文"))
        nav.assertRoute(CurrencySettings("X /?&currency=JPY#中文"))
    }

    @Test fun analyticsDateCurrencyAndKindsKeepTheirExactFilterContext() {
        val nav = nav()
        val filter = Transactions(categoryId = "cat", from = "2026-01-01", to = "2027-01-01",
            currency = "USD", kinds = "EXPENSE,INCOME")
        nav.navigate(filter)
        nav.assertRoute(filter)
    }

    @Test fun everyIngressUsesRestoredDestinationsAndRetainsItsSourceId() {
        val nav = nav()
        fun quick(entry: QuickEntry.Entry) = CoreIngressTarget.Quick(entry)
        assertTrue(nav.routeIngress(quick(QuickEntry.Entry.NewTransaction), true))
        nav.assertRoute(AddTransaction())
        assertTrue(nav.routeIngress(quick(QuickEntry.Entry.NewTransfer), true))
        nav.assertRoute(AddTransaction(type = "TRANSFER"))
        assertTrue(nav.routeIngress(quick(QuickEntry.Entry.NewSubscription), true))
        nav.assertRoute(AddTransaction(initialTab = 1))
        val id = "source/中文?&=#"
        assertTrue(nav.routeIngress(quick(QuickEntry.Entry.Template(id)), true))
        nav.assertRoute(AddTransaction(templateId = id))
        assertTrue(nav.routeIngress(quick(QuickEntry.Entry.Subscription(id)), true))
        nav.assertRoute(Subscriptions(id))
        assertTrue(nav.routeIngress(CoreIngressTarget.Ai, true))
        nav.assertRoute(AiAssistant)
        repeat(3) {
            assertTrue(nav.routeIngress(quick(QuickEntry.Entry.Home), true))
            nav.assertRoute(Home)
        }
        assertFalse(nav.popBackStack())
    }

    @Test fun ingressRemainsPendingUntilOnboardingCompletes() {
        val nav = nav(onboarded = false)
        assertFalse(nav.routeIngress(CoreIngressTarget.Ai, false))
        nav.assertRoute(OnBoarding)
        nav.navigate(Home) { popUpTo<OnBoarding> { inclusive = true } }
        nav.settle()
        assertTrue(nav.routeIngress(CoreIngressTarget.Ai, true))
        nav.assertRoute(AiAssistant)
    }

    @Test fun investmentsBrokerAccountPopsDirectlyToCaller() {
        val nav = nav()
        nav.navigate(ManageAccounts)
        nav.assertRoute(ManageAccounts)
        nav.navigate(Investments(connectionId = "1", accountId = "U15116568"))
        nav.assertRoute(Investments(connectionId = "1", accountId = "U15116568"))
        assertTrue(nav.popBackStack())
        nav.assertRoute(ManageAccounts)
    }
}
