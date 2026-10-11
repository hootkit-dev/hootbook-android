@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.navigation

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.platform.LocalView
import io.github.hootkit_dev.hootbook.presentation.navigation.*
import io.github.hootkit_dev.hootbook.presentation.ui.adaptive.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.NavController
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.*
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.presentation.core.accounts.*
import io.github.hootkit_dev.hootbook.presentation.core.categories.CoreCategoriesScreen
import io.github.hootkit_dev.hootbook.presentation.core.home.CoreHomeScreen
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreSettingsScreen
import io.github.hootkit_dev.hootbook.presentation.core.currencies.CoreCurrenciesScreen
import io.github.hootkit_dev.hootbook.presentation.core.backup.CoreBackupScreen
import io.github.hootkit_dev.hootbook.presentation.core.budgets.CoreBudgetsScreen
import io.github.hootkit_dev.hootbook.presentation.core.plans.*
import io.github.hootkit_dev.hootbook.presentation.core.debts.CoreDebtsScreen
import io.github.hootkit_dev.hootbook.presentation.core.transactions.CoreTransactionsScreen
import io.github.hootkit_dev.hootbook.presentation.core.analytics.CoreAnalyticsScreen
import io.github.hootkit_dev.hootbook.presentation.core.ai.CoreAiScreen
import io.github.hootkit_dev.hootbook.presentation.core.sync.CoreSyncScreen
import io.github.hootkit_dev.hootbook.presentation.core.investments.*
import io.github.hootkit_dev.hootbook.presentation.core.profile.*
import io.github.hootkit_dev.hootbook.presentation.core.appearance.CoreAppearanceScreen
import io.github.hootkit_dev.hootbook.presentation.core.applock.CoreAppLockSettingsScreen
import io.github.hootkit_dev.hootbook.presentation.core.notifications.CoreNotificationPreferencesScreen
import io.github.hootkit_dev.hootbook.presentation.core.onboarding.CoreOnboardingScreen
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about.*
import io.github.hootkit_dev.hootbook.widget.QuickEntry

internal fun coreRoute(path: String, vararg values: Pair<String, String?>): String = path + values.mapNotNull { (key, value) -> value?.let { "$key=${Uri.encode(it)}" } }
    .takeIf { it.isNotEmpty() }?.joinToString("&", "?").orEmpty()
internal fun coreTransactionsRoute(accountId: String? = null, transactionId: String? = null, subscriptionId: String? = null,
    templateId: String? = null, categoryId: String? = null, newKind: TransactionKind? = null, from: String? = null, to: String? = null, currency: String? = null, kinds: String? = null): String = coreRoute("transactions",
    "accountId" to accountId, "transactionId" to transactionId, "subscriptionId" to subscriptionId, "templateId" to templateId,
    "categoryId" to categoryId, "newKind" to newKind?.name,"from" to from,"to" to to,"currency" to currency,"kinds" to kinds)
internal fun coreRouteDate(year:Int,month:Int,day:Int):String = "%04d-%02d-%02d".format(java.util.Locale.ROOT,year,month,day)
internal fun coreCurrenciesRoute(currency: String? = null): String = coreRoute("currencies", "currency" to currency)
/** Onboarding is removed from the live stack; Home is the permanent post-onboarding anchor.
 * Home always means its root, not a saved Settings/Profile child. Other tabs retain their saved state.
 */
internal fun NavController.selectCoreTab(route: String) {
    val current = currentBackStackEntry
    val currentRoute = current?.destination?.route?.substringBefore('?')
    val isContextual = currentRoute == "transactions" && listOf(
        "accountId", "transactionId", "subscriptionId", "templateId", "categoryId", "newKind", "from", "to", "currency", "kinds"
    ).any { current?.arguments?.getString(it) != null }
    if (currentRoute == route && !isContextual) return
    navigate(route) {
        launchSingleTop = true
        restoreState = route != "home" && currentRoute != route
        popUpTo("home") { saveState = true }
    }
}
private fun arguments(vararg names: String) = names.map { name -> navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null } }

/** Only production Core destinations. No old graph/engine/Room compatibility route. */
@Composable
fun CoreNavHost(session: CoreSession, capabilities: CoreCapabilities, preferences: CoreUiPreferencesStore, ingress: CoreIntentIngress) {
    val nav = rememberNavController(); val prefs by preferences.state.collectAsStateWithLifecycle()
    val pending by ingress.pending.collectAsStateWithLifecycle(); val shareFailure by ingress.failed.collectAsStateWithLifecycle()
    val current by nav.currentBackStackEntryAsState()
    val windowLayout = rememberWindowLayout()
    val view = LocalView.current
    val hostChrome = remember { HostChromeState() }
    val start = remember { if (prefs.onboardingFinished) "home" else "onboarding" }
    var recordHint by rememberSaveable { mutableStateOf<String?>(null) }
    var schema by remember(session) { mutableStateOf<UInt?>(null) }
    LaunchedEffect(session) { try { schema = session.awaitCore().version().schema } catch (_: Exception) { } }
    val back: () -> Unit = { nav.popBackStack() }
    fun go(route: String) { nav.navigate(route) { launchSingleTop = true } }
    fun record(table: Table, id: String) {
        when (table) {
            Table.TRANSACTIONS -> go(coreTransactionsRoute(transactionId = id))
            Table.ACCOUNTS -> go(coreRoute("accounts", "id" to id))
            Table.BUDGETS -> go(coreRoute("budgets", "id" to id))
            Table.SUBSCRIPTIONS -> go(coreRoute("subscriptions", "id" to id))
            Table.PEOPLE -> go(coreRoute("debts", "id" to id))
            Table.QUICK_TEMPLATES -> { recordHint = "$table · $id"; go("templates") }
            Table.CATEGORIES, Table.SUBCATEGORIES -> { recordHint = "$table · $id"; go("categories") }
            else -> { recordHint = "$table · $id"; go(if (table == Table.DEBTS) "debts" else "accounts") }
        }
    }
    CoreLocaleGate(session) {
        LaunchedEffect(pending.firstOrNull()?.token) {
            val entry = pending.firstOrNull() ?: return@LaunchedEffect
            val route = when (val target = entry.target) {
                CoreIngressTarget.Ai -> "ai"
                is CoreIngressTarget.Quick -> when (val quick = target.entry) {
                    QuickEntry.Entry.Home -> "home"
                    QuickEntry.Entry.NewTransaction -> coreTransactionsRoute(newKind = TransactionKind.EXPENSE)
                    QuickEntry.Entry.NewTransfer -> coreTransactionsRoute(newKind = TransactionKind.TRANSFER)
                    QuickEntry.Entry.NewSubscription -> coreRoute("subscriptions", "new" to "true")
                    is QuickEntry.Entry.Template -> coreTransactionsRoute(templateId = quick.id)
                    is QuickEntry.Entry.Subscription -> coreRoute("subscriptions", "id" to quick.id)
                }
            }
            go(route); ingress.consumed(entry.token)
        }
        CompositionLocalProvider(LocalWindowLayout provides windowLayout, LocalHostChrome provides hostChrome) {
            val route = current?.destination?.route.orEmpty().substringBefore('?')
            val entryScreen = current?.arguments?.let { args ->
                listOf("newKind", "transactionId", "subscriptionId", "templateId").any { args.getString(it) != null }
            } == true
            val mainScreen = route in setOf("home", "analytics", "transactions") && !entryScreen
            val showRail = windowLayout.useNavigationRail && route != "onboarding" && !entryScreen
            val showBottomBar = !windowLayout.useNavigationRail && mainScreen && hostChrome.suppressed == 0
            val showAdd = !entryScreen && hostChrome.suppressed == 0 && (route in setOf("home", "transactions", "subscriptions") ||
                (route == "budgets" && current?.arguments?.getString("id") != null))
            Row(Modifier.fillMaxSize()) {
                if (showRail) HootbookNavigationRail(navController = nav, currentDestination = current?.destination, visible = true)
                Scaffold(Modifier.weight(1f), contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = { HootbookBottomNavigation(navController = nav, currentDestination = current?.destination, visible = showBottomBar) },
                    floatingActionButton = {
                        AnimatedVisibility(showAdd, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                            FloatingActionButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                go(if (route == "subscriptions") coreRoute("subscriptions", "new" to "true")
                                   else coreTransactionsRoute(newKind = TransactionKind.EXPENSE))
                            }, containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                                Icon(Icons.Rounded.Add, stringResource(R.string.add_transaction_subscription_cd))
                            }
                        }
                    }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        if (shareFailure) TextButton(onClick = ingress::dismissFailure) { Text(stringResource(R.string.core_shell_share_failed)) }
                        recordHint?.let { hint -> Row { Text(hint, Modifier.weight(1f)); TextButton(onClick = { recordHint = null }) { Text(stringResource(R.string.core_shell_close)) } } }
                        Box(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "core-screen-" + current?.destination?.route.orEmpty().substringBefore('?') }, contentAlignment = Alignment.TopCenter) {
                            NavHost(nav, startDestination = start, modifier = Modifier.fillMaxSize(),
                                enterTransition = CoreNavigationMotion.enter,
                                exitTransition = CoreNavigationMotion.exit,
                                popEnterTransition = CoreNavigationMotion.popEnter,
                                popExitTransition = CoreNavigationMotion.popExit) {
                                composable("onboarding", enterTransition=HootbookTransitions.noneEnter, exitTransition=HootbookTransitions.noneExit) { CoreOnboardingScreen(session, preferences, ::go, onRecord=::record) { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
                                composable("home") { CoreHomeScreen(onAccounts={go("accounts")},onCategories={go("categories")},onTransactions={go("transactions")},onBudgets={go("budgets")},
                                    onSubscriptions={go("subscriptions")},onTemplates={go("templates")},onDebts={go("debts")},onSettings={go("settings")},onCurrencies={go("currencies")},onBackup={go("backup")},
                                    onProfile={go("profile")},onAppearance={go("appearance")},onAnalytics={go("analytics")},onAccount={go(coreRoute("accounts","id" to it))},onBudget={go(coreRoute("budgets","id" to it))},
                                    onTransaction={go(coreTransactionsRoute(transactionId=it))},onSubscription={go(coreRoute("subscriptions","id" to it))},onAi={go("ai")},onAccountCategory={go(coreRoute("accounts","kind" to it.name))}) }
                                composable("accounts?id={id}&history={history}&kind={kind}&create={create}", arguments=arguments("id","history","kind","create")) { entry -> CoreAccountsScreen(back,{go(coreTransactionsRoute(accountId=it))},initialAccountId=entry.arguments?.getString("id"),onConnectBroker={go("investments")},initialHistory=entry.arguments?.getString("history")=="true",initialCreate=entry.arguments?.getString("create")=="true",initialKind=entry.arguments?.getString("kind")?.let { raw -> AccountKind.entries.find { it.name == raw } },onBrokerage={connection,id->go(coreRoute("investments","connection" to connection.toString(),"id" to id))},onTransaction={go(coreTransactionsRoute(transactionId=it))}) }
                                composable("categories") { CoreCategoriesScreen(back) }
                                composable("budgets?id={id}", arguments=arguments("id")) { CoreBudgetsScreen(back,it.arguments?.getString("id")) }
                                composable("subscriptions?id={id}&new={new}", arguments=arguments("id","new")) { entry -> CoreSubscriptionsScreen(back,{go(coreTransactionsRoute(subscriptionId=it))},entry.arguments?.getString("id"),initialCreate=entry.arguments?.getString("new")=="true",onNewTransaction={go(coreTransactionsRoute(newKind=TransactionKind.EXPENSE))}) }
                                composable("templates") { CoreTemplatesScreen(back,{go(coreTransactionsRoute(templateId=it))}) }
                                composable("debts?id={id}&new={new}", arguments=arguments("id","new")) { entry -> CoreDebtsScreen(back,initialPersonId=entry.arguments?.getString("id"),initialCreatePerson=entry.arguments?.getString("new")=="true",onOpenTransactions={go(coreTransactionsRoute(transactionId=it))}) }
                                composable("transactions?accountId={accountId}&transactionId={transactionId}&subscriptionId={subscriptionId}&templateId={templateId}&categoryId={categoryId}&newKind={newKind}&from={from}&to={to}&currency={currency}&kinds={kinds}",arguments=arguments("accountId","transactionId","subscriptionId","templateId","categoryId","newKind","from","to","currency","kinds")) { entry -> CoreTransactionsScreen(back,
                                    accountId=entry.arguments?.getString("accountId"),initialTransactionId=entry.arguments?.getString("transactionId"),subscriptionId=entry.arguments?.getString("subscriptionId"),templateId=entry.arguments?.getString("templateId"),
                                    onNewSubscription={go(coreRoute("subscriptions","new" to "true"))},onManageDebts={go("debts")},onManageSubscriptions={go(coreRoute("subscriptions","id" to it))},onManageMerchantIcon={go(coreRoute("merchant-icons","name" to it))},
                                    initialFrom=entry.arguments?.getString("from"),initialTo=entry.arguments?.getString("to"),initialCurrency=entry.arguments?.getString("currency"),initialKinds=entry.arguments?.getString("kinds")?.split(",")?.mapNotNull{raw->TransactionKind.entries.find{it.name==raw}}.orEmpty(),initialCategoryId=entry.arguments?.getString("categoryId"),initialNewKind=entry.arguments?.getString("newKind")?.let { raw -> TransactionKind.entries.find { it.name==raw && it in setOf(TransactionKind.EXPENSE,TransactionKind.TRANSFER) } }) }
                                composable("analytics") { CoreAnalyticsScreen(back,{go(coreTransactionsRoute(categoryId=it))},onFilterTransactions={filter->go(coreTransactionsRoute(categoryId=filter.categoryIds.singleOrNull(),from=filter.from?.let{coreRouteDate(it.year,it.month.toInt(),it.day.toInt())},to=filter.to?.let{coreRouteDate(it.year,it.month.toInt(),it.day.toInt())},currency=filter.currency,kinds=filter.kinds.joinToString(","){it.name}))}) }
                                composable("ai") { CoreAiScreen(back) }
                                composable("sync") { CoreSyncScreen(back,{go("backup")},::record) }
                                composable("investments?connection={connection}&id={id}",arguments=arguments("connection","id")) { entry ->
                                    val vm:CoreInvestmentsViewModel=hiltViewModel(); val connection=entry.arguments?.getString("connection")?.toLongOrNull(); val id=entry.arguments?.getString("id")
                                    val brokerState by vm.state.collectAsStateWithLifecycle(); var routedBroker by rememberSaveable { mutableStateOf(false) }
                                    LaunchedEffect(connection,id,brokerState.loading) { if(!brokerState.loading&&!routedBroker&&connection!=null&&id!=null) { routedBroker=true; vm.selectAccount(BrokerAccountRef(connection,id)) } }
                                    CoreInvestmentsScreen(back,{go("accounts")},{go(coreRoute("accounts","id" to it,"history" to "true"))},vm,onAddInvestment={go(coreRoute("accounts","kind" to AccountKind.INVESTMENT.name,"create" to "true"))})
                                }
                                composable("settings") { CoreSettingsScreen(back,{go("currencies")},{go("backup")},onRoute=::go) }
                                composable("currencies?currency={currency}",arguments=arguments("currency")) { CoreCurrenciesScreen(back,it.arguments?.getString("currency")) }
                                composable("backup") { CoreBackupScreen(back, onSync={go("sync")}) }
                                composable("profile") { CoreProfileScreen(back,{go("debts")},{go("merchant-icons")},onPerson={go(coreRoute("debts","id" to it))},onAddPerson={go(coreRoute("debts","new" to "true"))}) }
                                composable("merchant-icons?name={name}",arguments=arguments("name")) { CoreMerchantIconsScreen(back,it.arguments?.getString("name")) }
                                composable("appearance") { CoreAppearanceScreen(back) }
                                composable("app-lock") { CoreAppLockSettingsScreen(back) }
                                composable("data-privacy") { CoreAppLockSettingsScreen(back, dataPrivacy = true) }
                                composable("notifications") { CoreNotificationPreferencesScreen(back) }
                                composable("about") { AboutScreen(back,{go("licenses")},coreSchemaVersion=schema) }
                                composable("licenses") { LicensesScreen(back) }
                            }
                        }
                    }
                }
            }
        }
    }
}
