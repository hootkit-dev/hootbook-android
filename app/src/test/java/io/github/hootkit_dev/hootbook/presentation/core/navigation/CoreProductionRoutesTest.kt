package io.github.hootkit_dev.hootbook.presentation.core.navigation

import android.app.Application
import android.net.Uri
import dev.hootkit.core.TransactionKind
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreProductionRoutesTest {
    @Test fun opaqueStringIdsAndBothSourceRefsRetainUriEncoding() {
        val uri=Uri.parse(coreTransactionsRoute(accountId="account/空 格",transactionId="tx?&=",categoryId="category/string"))
        assertEquals("account/空 格",uri.getQueryParameter("accountId"));assertEquals("tx?&=",uri.getQueryParameter("transactionId"));assertEquals("category/string",uri.getQueryParameter("categoryId"))
    }
    @Test fun realAnalyticsFilterPreservesDateExclusiveCurrencyAndKinds() {
        val uri=Uri.parse(coreTransactionsRoute(categoryId="cat",from="2026-01-01",to="2027-01-01",currency="USD",kinds="EXPENSE,INCOME"))
        assertEquals("2027-01-01",uri.getQueryParameter("to"));assertEquals("USD",uri.getQueryParameter("currency"));assertEquals("EXPENSE,INCOME",uri.getQueryParameter("kinds"))
    }
    @Test fun transferQuickEntryIsExplicitKindWithoutFakeAccountOrCurrency() {
        val uri=Uri.parse(coreTransactionsRoute(newKind=TransactionKind.TRANSFER))
        assertEquals("TRANSFER",uri.getQueryParameter("newKind"));assertNull(uri.getQueryParameter("accountId"));assertNull(uri.getQueryParameter("currency"))
    }
    @Test fun arabicUiLocaleStillProducesAsciiIsoDateFilterArguments() {
        val before=java.util.Locale.getDefault()
        try { java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            val date=coreRouteDate(2026,2,9);assertEquals("2026-02-09",date)
            assertEquals(java.time.LocalDate.of(2026,2,9),java.time.LocalDate.parse(date))
        } finally { java.util.Locale.setDefault(before) }
    }
    @Navigator.Name("core-route-fixture")
    class RouteNavigator : Navigator<NavDestination>() {
        override fun createDestination() = NavDestination(this)
        override fun navigate(destination: NavDestination, args: Bundle?, navOptions: NavOptions?, navigatorExtras: Extras?): NavDestination = destination
        override fun popBackStack(): Boolean = true
    }
    private fun postOnboardingNav(): NavHostController {
        val nav=NavHostController(ApplicationProvider.getApplicationContext<Application>())
        nav.setViewModelStore(ViewModelStore())
        val fixture=RouteNavigator();nav.navigatorProvider.addNavigator(fixture)
        val graph=NavGraph(nav.navigatorProvider.getNavigator<NavGraphNavigator>("navigation"))
        listOf("onboarding","home","settings","profile","appearance","app-lock","notifications","about","sync","investments","transactions","analytics").forEach { route -> graph.addDestination(fixture.createDestination().apply { this.route=route }) }
        graph.setStartDestination("onboarding");nav.graph=graph
        nav.navigate("home") { popUpTo("onboarding") { inclusive=true } }
        return nav
    }
    @Test fun freshOnboardingProfileThenHomeReachesRootAndRepeatedHomeDoesNotDuplicate() {
        val nav=postOnboardingNav();nav.navigate("profile");nav.selectCoreTab("home")
        assertEquals("home",nav.currentDestination?.route)
        repeat(3){nav.selectCoreTab("home");assertEquals("home",nav.currentDestination?.route)}
        assertFalse(nav.popBackStack())
    }
    @Test fun allSettingsChildrenReturnHomeWithoutRestoringChildStack() {
        val nav=postOnboardingNav()
        listOf("profile","appearance","app-lock","notifications","about","sync","investments").forEach { child ->
            nav.navigate("settings");nav.navigate(child);nav.selectCoreTab("home")
            assertEquals("home",nav.currentDestination?.route)
        }
    }
    @Test fun settingsChildBackAndOtherTabRestoreRemainUsable() {
        val nav=postOnboardingNav();nav.navigate("settings");nav.navigate("profile")
        assertTrue(nav.popBackStack());assertEquals("settings",nav.currentDestination?.route)
        nav.selectCoreTab("transactions");nav.selectCoreTab("analytics");nav.selectCoreTab("home")
        assertEquals("home",nav.currentDestination?.route)
        nav.selectCoreTab("transactions");assertEquals("transactions",nav.currentDestination?.route)
        nav.selectCoreTab("analytics");assertEquals("analytics",nav.currentDestination?.route)
        nav.selectCoreTab("home");assertEquals("home",nav.currentDestination?.route)
    }
    @Test fun defaultListAndCurrenciesHaveNoStaleSourceParameters() {assertEquals("transactions",coreTransactionsRoute());assertEquals("currencies",coreCurrenciesRoute())}
}
