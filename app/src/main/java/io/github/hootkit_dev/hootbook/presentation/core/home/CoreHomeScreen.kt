@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import coil3.compose.AsyncImage
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.preferences.HomeWidget
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import io.github.hootkit_dev.hootbook.presentation.format.CoreKindLabel
import io.github.hootkit_dev.hootbook.presentation.core.profile.CoreMerchantIcon

@Composable
fun CoreHomeScreen(
    onAccounts:()->Unit,onCategories:()->Unit,onTransactions:()->Unit,onBudgets:()->Unit,
    onSubscriptions:()->Unit,onTemplates:()->Unit,onDebts:()->Unit,onSettings:()->Unit,
    onCurrencies:()->Unit,onBackup:()->Unit,
    onProfile:()->Unit={},onAppearance:()->Unit={},onAnalytics:()->Unit={},
    onAccount:(String)->Unit={onAccounts()},onBudget:(String)->Unit={onBudgets()},
    onTransaction:(String)->Unit={onTransactions()},onSubscription:(String)->Unit={onSubscriptions()},
    viewModel:CoreHomeViewModel=hiltViewModel(),platform:CoreHomePlatformViewModel=hiltViewModel(),
){
    val state by viewModel.state.collectAsStateWithLifecycle();val prefs by platform.preferences.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME){viewModel.refresh()}
    Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.TopCenter){
        LazyColumn(Modifier.widthIn(max=1120.dp).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{val background=if(prefs.profileBackground!=0)Color(prefs.profileBackground)else MaterialTheme.colorScheme.surface
                Surface(color=background,contentColor=if(background.luminance()<0.5f)Color.White else Color.Black){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(stringResource(R.string.core_complete_greeting,prefs.userName.ifBlank{stringResource(R.string.core_complete_default_name)}),style=MaterialTheme.typography.headlineMedium);TextButton(onClick=onProfile,modifier=Modifier.semantics{contentDescription="core-home-profile"}){Text(stringResource(R.string.core_complete_profile))}}
                runCatching{platform.assets.resolve(prefs.avatar)}.getOrNull()?.let{AsyncImage(it,stringResource(R.string.core_complete_profile),Modifier.size(64.dp))}
            }}}
            if(prefs.showBanner)item{runCatching{platform.assets.resolve(prefs.banner)}.getOrNull()?.let{AsyncImage(it,stringResource(R.string.core_complete_banner),Modifier.fillMaxWidth().heightIn(min=100.dp,max=240.dp))}}
            if(state.loading||state.widgetsLoading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.errorResource?.let{item{Text(stringResource(it),color=MaterialTheme.colorScheme.error);TextButton(onClick=viewModel::refresh){Text(stringResource(R.string.core_complete_refresh))}}}
            state.widgetError?.let{item{Text(stringResource(it),color=MaterialTheme.colorScheme.error);TextButton(onClick=viewModel::refresh){Text(stringResource(R.string.core_complete_refresh))}}}
            prefs.homeOrder.filterNot{it in prefs.hiddenHomeWidgets}.forEach{widget->
                when(widget){
                    HomeWidget.NETWORTH_SUMMARY->item(key=widget.name){state.overview?.let{overview->HomeCard{
                        Text(stringResource(R.string.core_complete_net_worth),style=MaterialTheme.typography.titleLarge)
                        Text(money(overview.netWorth,state.customSymbols),style=MaterialTheme.typography.headlineMedium)
                        state.displayNetWorth?.let{display->if(display.complete)Text(CoreMoneyPresentation.format(display.amount,display.currency,state.customSymbols))else Text(stringResource(R.string.core_complete_display_missing,display.currency),color=MaterialTheme.colorScheme.error)}
                        if(!overview.netWorthComplete)Text(stringResource(R.string.core_complete_incomplete),color=MaterialTheme.colorScheme.error)
                        overview.creditAvailable?.let{Text(stringResource(R.string.core_complete_credit,money(it,state.customSymbols)))}
                        overview.nearestDue?.let{Text(stringResource(R.string.core_complete_card_due,it.dueDate.coreDateText()))}
                        state.summary?.let{summary->Text(stringResource(R.string.core_complete_income,summary.income.toPlainString(),summary.currency));Text(stringResource(R.string.core_complete_expense,summary.expense.toPlainString(),summary.currency));Text(stringResource(R.string.core_complete_net,summary.net.toPlainString(),summary.currency));if(!summary.complete)Text(stringResource(R.string.core_complete_missing,summary.missingCurrencies.joinToString()),color=MaterialTheme.colorScheme.error)}
                        TextButton(onClick=onAnalytics){Text(stringResource(R.string.core_complete_analytics))}
                    }}}
                    HomeWidget.ACCOUNT_CAROUSEL->item(key=widget.name){Text(stringResource(R.string.core_complete_accounts),style=MaterialTheme.typography.titleLarge)
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(state.overview?.accounts.orEmpty(),key={it.id}){account->Card(onClick={onAccount(account.id)},modifier=Modifier.widthIn(min=240.dp,max=360.dp)){
                            Column(Modifier.padding(16.dp)){Text(account.name,style=MaterialTheme.typography.titleMedium);account.last4?.let{Text(it)};Text(money(account.total,state.customSymbols));if(!account.totalComplete)Text(stringResource(R.string.core_complete_incomplete));account.securities?.let{Text(stringResource(R.string.core_complete_securities,money(it,state.customSymbols)))};account.broker?.let{Text(it.asOf.coreDateText())};account.pockets.forEach{pocket->Text(CoreMoneyPresentation.format(pocket.balance,pocket.currency,state.customSymbols))}}
                        }}}
                        TextButton(onClick=onAccounts){Text(stringResource(R.string.core_complete_manage_accounts))}
                    }
                    HomeWidget.LOANS->item(key=widget.name){state.debts?.let{summary->HomeCard{
                        Text(stringResource(R.string.core_complete_debts),style=MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.core_complete_receivable,summary.receivable.toPlainString(),summary.currency));Text(stringResource(R.string.core_complete_payable,summary.payable.toPlainString(),summary.currency));Text(stringResource(R.string.core_complete_debt_people,summary.people.toString(),summary.peopleOverdue.toString()))
                        if(!summary.complete)Text(stringResource(R.string.core_complete_incomplete),color=MaterialTheme.colorScheme.error)
                        TextButton(onClick=onDebts){Text(stringResource(R.string.core_complete_debts))}
                    }}}
                    HomeWidget.BUDGET_CAROUSEL->item(key=widget.name){Text(stringResource(R.string.core_complete_budgets),style=MaterialTheme.typography.titleLarge)
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(state.budgets,key={it.id}){budget->Card(onClick={onBudget(budget.id)},modifier=Modifier.widthIn(min=260.dp,max=380.dp)){
                            Column(Modifier.padding(16.dp)){Text(budget.name,style=MaterialTheme.typography.titleMedium);Text("${budget.current.spent.toPlainString()} / ${budget.amount.amount.toPlainString()} ${budget.amount.currency}");budget.display?.let{Text("${it.spent.toPlainString()} / ${it.amount.toPlainString()} ${it.currency}")};if(!budget.current.complete)Text(stringResource(R.string.core_complete_missing,budget.current.missingCurrencies.joinToString()),color=MaterialTheme.colorScheme.error)}
                        }}}
                        TextButton(onClick=onBudgets){Text(stringResource(R.string.core_complete_budgets))}
                    }
                    HomeWidget.UPCOMING_SUBSCRIPTIONS->item(key=widget.name){HomeCard{
                        Text(stringResource(R.string.core_complete_upcoming),style=MaterialTheme.typography.titleLarge)
                        state.subscriptions?.let{summary->Text(stringResource(R.string.core_complete_subscription_cost,summary.monthly.toPlainString(),summary.yearly.toPlainString(),summary.currency));if(!summary.complete)Text(stringResource(R.string.core_complete_missing,summary.missingCurrencies.joinToString()),color=MaterialTheme.colorScheme.error)}
                        state.upcoming.forEach{row->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){CoreMerchantIcon(row.name,platform.assets,Modifier.size(40.dp));TextButton(onClick={onSubscription(row.id)}){Text("${row.name} · ${row.nextDue?.coreDateText().orEmpty()} · ${money(row.amount,state.customSymbols)}")}}}
                        TextButton(onClick=onSubscriptions){Text(stringResource(R.string.core_complete_subscriptions))}
                    }}
                    HomeWidget.RECENT_TRANSACTIONS->item(key=widget.name){HomeCard{
                        Text(stringResource(R.string.core_complete_recent),style=MaterialTheme.typography.titleLarge)
                        state.recent.forEach{row->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){CoreMerchantIcon(row.merchant,platform.assets,Modifier.size(40.dp));TextButton(onClick={onTransaction(row.id)}){Column{Text(row.merchant);Text("${CoreKindLabel(row.kind)} · ${CoreMoneyPresentation.format(row.amount,row.currency,state.customSymbols)}")}}}}
                        TextButton(onClick=onTransactions){Text(stringResource(R.string.core_complete_transactions))}
                    }}
                    HomeWidget.TRANSACTION_HEATMAP->item(key=widget.name){HomeCard{
                        Text(stringResource(R.string.core_complete_heatmap),style=MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.core_complete_heatmap_note),style=MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){state.heatmap.forEach{day->
                            Surface(color=if(day.transactions==0u)MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.small){Text("${day.date.day}: ${day.transactions}",Modifier.padding(8.dp),style=MaterialTheme.typography.labelSmall)}
                        }}
                    }}
                }
            }
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick=onTransactions){Text(stringResource(R.string.core_complete_transactions))};OutlinedButton(onClick=onAccounts){Text(stringResource(R.string.core_complete_accounts))};OutlinedButton(onClick=onBudgets){Text(stringResource(R.string.core_complete_budgets))};OutlinedButton(onClick=onSubscriptions){Text(stringResource(R.string.core_complete_subscriptions))};OutlinedButton(onClick=onDebts){Text(stringResource(R.string.core_complete_debts))};OutlinedButton(onClick=onAnalytics){Text(stringResource(R.string.core_complete_analytics))}
                OutlinedButton(onClick=onTemplates){Text(stringResource(R.string.core_complete_templates))};OutlinedButton(onClick=onCategories){Text(stringResource(R.string.core_complete_categories))}
                OutlinedButton(onClick=onSettings){Text(stringResource(R.string.core_complete_settings))};OutlinedButton(onClick=onCurrencies){Text(stringResource(R.string.core_complete_currencies))}
                OutlinedButton(onClick=onBackup){Text(stringResource(R.string.core_complete_backup))};OutlinedButton(onClick=onAppearance){Text(stringResource(R.string.core_complete_layout))}
            }}
        }
    }
}
@Composable private fun HomeCard(content: @Composable ColumnScope.() -> Unit){ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
private fun money(money:Money,symbols:Map<String,String> = emptyMap())=CoreMoneyPresentation.format(money.amount,money.currency,symbols)
