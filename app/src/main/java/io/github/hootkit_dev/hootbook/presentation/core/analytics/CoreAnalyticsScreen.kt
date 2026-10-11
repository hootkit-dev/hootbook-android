@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hootkit_dev.hootbook.presentation.core.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R

@Composable
fun CoreAnalyticsScreen(onBack:()->Unit,onTransactions:(categoryId:String?)->Unit={},viewModel:CoreAnalyticsViewModel=hiltViewModel(),onFilterTransactions:((dev.hootkit.core.TransactionFilter)->Unit)?=null){
    val state by viewModel.state.collectAsStateWithLifecycle();val q=state.query
    var from by rememberSaveable(q.from){mutableStateOf(q.from)};var through by rememberSaveable(q.through){mutableStateOf(q.through)};var currency by rememberSaveable(q.currency){mutableStateOf(q.currency)}
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME){viewModel.refresh()}
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.core_analytics_title))},navigationIcon={TextButton(onClick=onBack){Text(stringResource(R.string.core_analytics_back))}})}){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=androidx.compose.ui.Alignment.TopCenter){LazyColumn(Modifier.widthIn(max=960.dp).fillMaxWidth().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(7L,30L,365L).forEach{days->FilterChip(selected=false,onClick={val today=java.time.LocalDate.now();viewModel.apply(q.copy(from=today.minusDays(days-1).toString(),through=today.toString(),allTime=false))},label={Text(stringResource(R.string.core_analytics_days,days.toString()))})}}}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf(R.string.core_analytics_this_month,R.string.core_analytics_last_month,R.string.core_analytics_this_year).forEach{label->FilterChip(false,{
                    val today=java.time.LocalDate.now();val range=when(label){
                        R.string.core_analytics_last_month->java.time.YearMonth.from(today).minusMonths(1).let{it.atDay(1) to it.atEndOfMonth()}
                        R.string.core_analytics_this_year->today.withDayOfYear(1) to today
                        else->today.withDayOfMonth(1) to today
                    };viewModel.apply(q.copy(from=range.first.toString(),through=range.second.toString(),allTime=false))
                },label={Text(stringResource(label))})}
            }}
            item{FilterChip(q.allTime,{viewModel.apply(q.copy(allTime=!q.allTime))},label={Text(stringResource(R.string.core_analytics_all_time))});if(q.allTime)Text(stringResource(R.string.core_analytics_all_time_note),style=MaterialTheme.typography.bodySmall)}
            item{OutlinedTextField(from,{from=it},label={Text(stringResource(R.string.core_analytics_from))},singleLine=true,enabled=!q.allTime);OutlinedTextField(through,{through=it},label={Text(stringResource(R.string.core_analytics_through))},singleLine=true,enabled=!q.allTime);OutlinedTextField(currency,{currency=it.uppercase(java.util.Locale.ROOT)},label={Text(stringResource(R.string.core_analytics_filter_currency))},singleLine=true)
                Button(onClick={viewModel.apply(q.copy(from=from,through=through,currency=currency))}){Text(stringResource(R.string.core_analytics_apply))}}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){TransactionKind.entries.filter{it!=TransactionKind.UNKNOWN}.forEach{kind->FilterChip(selected=kind in q.kinds,onClick={viewModel.apply(q.copy(kinds=if(kind in q.kinds)q.kinds-kind else q.kinds+kind))},label={Text(CoreKindLabel(kind))})}}}
            if(state.loading||state.loadingDays)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let{item{Text(stringResource(it),color=MaterialTheme.colorScheme.error);TextButton(onClick=viewModel::refresh){Text(stringResource(R.string.core_analytics_retry))}}}
            state.summary?.let{summary->item{Text(stringResource(R.string.core_analytics_totals,summary.currency),style=MaterialTheme.typography.titleLarge);Text(stringResource(R.string.core_analytics_income,summary.income.toPlainString()));Text(stringResource(R.string.core_analytics_expense,summary.expense.toPlainString()));Text(stringResource(R.string.core_analytics_net,summary.net.toPlainString()));Text(stringResource(R.string.core_analytics_count,state.total.toString()));if(!summary.complete)Text(stringResource(R.string.core_analytics_missing,summary.missingCurrencies.joinToString()),color=MaterialTheme.colorScheme.error)}
                summary.perCurrency.forEach{native->item{Text("${native.currency} · ${native.income.toPlainString()} / ${native.expense.toPlainString()}")}}
                item{Text(stringResource(R.string.core_analytics_categories),style=MaterialTheme.typography.titleLarge)}
                items(summary.expenseByCategory+summary.incomeByCategory,key={it.categoryId}){row->OutlinedCard(onClick={onFilterTransactions?.invoke(q.filter().copy(categoryIds=listOf(row.categoryId))) ?: onTransactions(row.categoryId)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("${state.categories.find{it.id==row.categoryId}?.displayName?:row.categoryId} · ${row.total.toPlainString()} ${summary.currency}");Text(stringResource(R.string.core_analytics_count,state.categoryCounts[row.categoryId]?.toString().orEmpty()))}}}
            }
            item{Text(stringResource(R.string.core_analytics_daily),style=MaterialTheme.typography.titleLarge);CoreAmountChart(state.days.map{it.summary.expense});Text(stringResource(R.string.core_analytics_daily_note),style=MaterialTheme.typography.bodySmall)}
            items(state.days,key={it.date.coreDateText()}){day->Column{Text("${day.date.coreDateText()} · ${day.transactions}");Text("${day.summary.income.toPlainString()} / ${day.summary.expense.toPlainString()} ${day.summary.currency}");if(!day.summary.complete)Text(stringResource(R.string.core_analytics_missing,day.summary.missingCurrencies.joinToString()),color=MaterialTheme.colorScheme.error)}}
            if(state.olderThrough!=null)item{OutlinedButton(onClick=viewModel::loadOlderDays,enabled=!state.loading&&!state.loadingDays){Text(stringResource(R.string.core_analytics_older))}}
            item{Text(stringResource(R.string.core_analytics_unsupported),style=MaterialTheme.typography.bodySmall)}
        }}
    }
}
