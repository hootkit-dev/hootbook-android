package io.github.hootkit_dev.hootbook.presentation.core.analytics

import io.github.hootkit_dev.hootbook.presentation.format.coreDateText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.CoreSession
import dev.hootkit.platform.ChangeBus
import io.github.hootkit_dev.hootbook.R
import java.time.LocalDate as JavaDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoreAnalyticsQuery(val from:String=JavaDate.now().withDayOfMonth(1).toString(),val through:String=JavaDate.now().toString(),
    val allTime:Boolean=false,
    val kinds:Set<TransactionKind> = setOf(TransactionKind.EXPENSE),val currency:String="",val categoryId:String?=null){
    fun filter():TransactionFilter{if(allTime)return emptyCoreFilter().copy(kinds=kinds.toList(),currency=currency.trim().ifBlank{null},categoryIds=listOfNotNull(categoryId));val start=JavaDate.parse(from);val end=JavaDate.parse(through);require(start<=end)
        return emptyCoreFilter().copy(from=if(allTime)null else start.atStartOfDay().coreTime(),to=if(allTime)null else end.plusDays(1).atStartOfDay().coreTime(),kinds=kinds.toList(),currency=currency.trim().ifBlank{null},categoryIds=listOfNotNull(categoryId))}
}
data class CoreAnalyticsUiState(val query:CoreAnalyticsQuery=CoreAnalyticsQuery(),val summary:LedgerSummary?=null,val total:UInt=0u,
    val categories:List<Category> = emptyList(),val categoryCounts:Map<String,UInt> = emptyMap(),val days:List<CoreDailySummary> = emptyList(),val olderThrough:String?=null,
    val loading:Boolean=true,val loadingDays:Boolean=false,val error:Int?=null,val dailyFrom:String?=null)
@HiltViewModel
class CoreAnalyticsViewModel private constructor(private val session:CoreSession?,changes:ChangeBus,
    private val ledger: (suspend()->LedgerInterface)?,private val categoryQuery:(suspend()->List<Category>)?):ViewModel(){
    @Inject constructor(session:CoreSession,changes:ChangeBus):this(session,changes,null,null)
    internal constructor(changes:ChangeBus,ledger:suspend()->LedgerInterface,categories: suspend () -> List<Category> = { emptyList() }):this(null,changes,ledger,categories)
    private val mutable=MutableStateFlow(CoreAnalyticsUiState());val state=mutable.asStateFlow()
    private var request:Job?=null;private var revision=0L
    private suspend fun <T> withLedger(block:suspend(LedgerInterface)->T):T {
        if(ledger!=null){val api=ledger.invoke();return if(api is Disposable)api.use{block(api)}else block(api)}
        return session!!.awaitCore().ledger().use{block(it)}
    }
    init{viewModelScope.launch{changes.observe(setOf(Table.TRANSACTIONS,Table.CATEGORIES,Table.SUBCATEGORIES,Table.ACCOUNTS,Table.EXCHANGE_RATES,Table.SETTINGS)).collect{refresh()}}}
    fun apply(query:CoreAnalyticsQuery){mutable.update{it.copy(query=query)};refresh()}
    fun refresh(){val mine=++revision;request?.cancel();val selected=mutable.value.query;mutable.update{it.copy(loading=true,error=null,loadingDays=false)}
        request=viewModelScope.launch{try{
            val filter=selected.filter()
            val range=if(selected.allTime)withLedger{queryAllTimeDayRange(it,filter,JavaDate.now())}else JavaDate.parse(selected.from) to JavaDate.parse(selected.through)
            val (from,through)=range
            val start=maxOf(from,through.minusDays(30))
            val categories=categoryQuery?.invoke()?:session!!.awaitCore().categories().use{it.list(null,true)}
            var categoryCounts=emptyMap<String,UInt>()
            val data=withLedger{api->val summary=api.summary(filter)
                categoryCounts=(summary.expenseByCategory+summary.incomeByCategory).map{it.categoryId}.distinct().associateWith{api.list(filter.copy(categoryIds=listOf(it)),PageRequest(0u,1u)).total}
                Triple(summary,api.list(filter,PageRequest(0u,1u)).total,queryDailySummaries(api,start,through,filter))}
            if(mine==revision)mutable.update{it.copy(loading=false,summary=data.first,total=data.second,categories=categories,categoryCounts=categoryCounts,days=data.third,
                olderThrough=start.minusDays(1).takeIf{it>=from}?.toString(),dailyFrom=from.toString())}
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(mine==revision)mutable.update{it.copy(loading=false,error=R.string.core_analytics_query_failed)}}}
    }
    fun loadOlderDays(){val before=mutable.value;val through=before.olderThrough?.let(JavaDate::parse)?:return;if(before.loading||before.loadingDays)return
        val mine=++revision;request?.cancel();mutable.update{it.copy(loadingDays=true,error=null)}
        request=viewModelScope.launch{try{val from=JavaDate.parse(before.dailyFrom?:before.query.from);val start=maxOf(from,through.minusDays(30));val rows=withLedger{queryDailySummaries(it,start,through,before.query.filter())}
            if(mine==revision)mutable.update{it.copy(days=(rows+it.days).distinctBy{row->row.date.coreDateText()},olderThrough=start.minusDays(1).takeIf{it>=from}?.toString(),loadingDays=false)}
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){if(mine==revision)mutable.update{it.copy(loadingDays=false,error=R.string.core_analytics_query_failed)}}}
    }
}
