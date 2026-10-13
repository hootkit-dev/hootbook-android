package io.github.hootkit_dev.hootbook.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.QuickTemplate
import dev.hootkit.core.Table
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.MainActivity
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.CoreProcess
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Typed user navigation only. The receiving form re-resolves the Core ID; never writes a ledger. */
object QuickEntry {
    const val MAX_TEMPLATES=4
    const val MAX_SHORTCUT_TEMPLATES=3
    const val ACTION_TRANSACTION="io.github.hootkit_dev.hootbook.action.ADD_TRANSACTION"
    const val ACTION_SUBSCRIPTION="io.github.hootkit_dev.hootbook.action.ADD_SUBSCRIPTION"
    const val ACTION_TRANSFER="io.github.hootkit_dev.hootbook.action.ADD_TRANSFER"
    const val ACTION_OPEN="io.github.hootkit_dev.hootbook.action.CORE_ENTRY"
    sealed interface Entry {
        data object NewTransaction:Entry
        data object NewSubscription:Entry
        data object NewTransfer:Entry
        data object Home:Entry
        data class Template(val id:String):Entry
        data class Subscription(val id:String):Entry
    }
    private fun validId(id:String)=id.isNotBlank()&&id.length<=512&&id.none{it.isISOControl()}
    fun intent(context:Context,entry:Entry):Intent {
        val (path,action,id)=when(entry){
            Entry.NewTransaction->Triple("transaction",ACTION_TRANSACTION,null)
            Entry.NewSubscription->Triple("new-subscription",ACTION_SUBSCRIPTION,null)
            Entry.NewTransfer->Triple("transfer",ACTION_TRANSFER,null)
            Entry.Home->Triple("home",ACTION_OPEN,null)
            is Entry.Template->{require(validId(entry.id));Triple("template",ACTION_TRANSACTION,entry.id)}
            is Entry.Subscription->{require(validId(entry.id));Triple("subscription",ACTION_OPEN,entry.id)}
        }
        val uri=Uri.Builder().scheme("hootbook").authority("core-entry").appendPath(path).apply{if(id!=null)appendPath(id)}.build()
        return Intent(context,MainActivity::class.java).setAction(action).setData(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    fun parse(intent:Intent):Entry? {
        val data=intent.data
        // Old installed static/tiles and pinned Long IDs remain a plain Add entry, never an ID mapping.
        if(data==null||data.scheme=="hootbook"&&data.host=="template")return when(intent.action){
            ACTION_TRANSACTION->Entry.NewTransaction;ACTION_SUBSCRIPTION->Entry.NewSubscription;ACTION_TRANSFER->Entry.NewTransfer;else->null
        }
        if(data.scheme!="hootbook"||data.host!="core-entry"||data.query!=null||data.fragment!=null||data.port!=-1||data.userInfo!=null)return null
        val p=data.pathSegments
        if(p.size==1)return when(p[0]){
            "transaction"->Entry.NewTransaction.takeIf{intent.action==ACTION_TRANSACTION}
            "new-subscription"->Entry.NewSubscription.takeIf{intent.action==ACTION_SUBSCRIPTION}
            "transfer"->Entry.NewTransfer.takeIf{intent.action==ACTION_TRANSFER}
            "home"->Entry.Home.takeIf{intent.action==ACTION_OPEN};else->null
        }
        if(p.size!=2||!validId(p[1]))return null
        return when(p[0]){
            "template"->Entry.Template(p[1]).takeIf{intent.action==ACTION_TRANSACTION}
            "subscription"->Entry.Subscription(p[1]).takeIf{intent.action==ACTION_OPEN};else->null
        }
    }
    fun templateIntent(context:Context,templateId:String)=intent(context,Entry.Template(templateId))
    fun addIntent(context:Context)=intent(context,Entry.NewTransaction)
    fun actionIntent(context:Context,action:String)=intent(context,when(action){ACTION_SUBSCRIPTION->Entry.NewSubscription;ACTION_TRANSFER->Entry.NewTransfer;else->Entry.NewTransaction})
    fun label(template:QuickTemplate):String {
        val amount=template.amount.takeIf{template.prefillAmount}
        val currency=template.currency
        return if(amount!=null&&currency!=null)template.name+" · "+CoreMoneyPresentation.format(amount,currency) else template.name
    }
    fun shortcutId(templateId:String)="core_template_"+templateId
    fun isTemplateShortcut(id:String)=id.startsWith("core_template_")
}

@Singleton
class QuickEntryPublisher @Inject constructor(@ApplicationContext private val context:Context,
    private val session:CoreSession,private val changes:ChangeBus,private val prefs:CoreUiPreferencesStore,
    @ApplicationScope private val scope:CoroutineScope) {
    private var job:Job?=null
    @Synchronized fun start(){
        if(job?.isActive==true||CoreProcess.isCrashProcess(context))return
        job=scope.launch {
            prefs.ready()
            combine(changes.observe(setOf(Table.QUICK_TEMPLATES)),prefs.state.map{it.appLockEnabled}.distinctUntilChanged()){_,_->Unit}
                .conflate().collect {
                    delay(500) // Bound rapid commits and launcher update quota; no retry loop on failure.
                    try {
                        val all=session.awaitCore().plans().use{it.templates()}
                        try{publish(all,prefs.state.value.appLockEnabled)}catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){android.util.Log.w("CoreQuickEntry","Launcher updates deferred")}
                        QuickAddWidget().updateAll(context)
                    }catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){android.util.Log.w("CoreQuickEntry","System entries await the next update")}
                }
        }
    }
    internal fun publish(all:List<QuickTemplate>,locked:Boolean){
        val manager=context.getSystemService(android.content.pm.ShortcutManager::class.java)
        val capacity=((manager?.maxShortcutCountPerActivity?:4)-ShortcutManagerCompat.getShortcuts(context,ShortcutManagerCompat.FLAG_MATCH_MANIFEST).size).coerceAtLeast(0)
        val shown=if(locked)emptyList() else all.take(minOf(QuickEntry.MAX_SHORTCUT_TEMPLATES,(capacity-2).coerceAtLeast(0)))
        val dynamic=shown.mapIndexed{rank,t->shortcut(t,rank)}+listOf(
            action("core_transfer",R.string.add_transfer,R.drawable.ic_shortcut_transfer,QuickEntry.Entry.NewTransfer,shown.size),
            action("core_subscription",R.string.add_subscription,R.drawable.ic_shortcut_add_subscription,QuickEntry.Entry.NewSubscription,shown.size+1))
        val limited=dynamic.take(capacity.coerceAtLeast(0))
        fun ShortcutInfoCompat.signature()=listOf(id,shortLabel.toString(),longLabel?.toString(),intent.dataString)
        val current=ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy{it.rank}.map{it.signature()}
        if(current!=limited.map{it.signature()}&&manager?.isRateLimitingActive!=true)ShortcutManagerCompat.setDynamicShortcuts(context,limited)
        val pinned=ShortcutManagerCompat.getShortcuts(context,ShortcutManagerCompat.FLAG_MATCH_PINNED).map{it.id}
        val byId=all.associateBy{QuickEntry.shortcutId(it.id)}
        val gone=pinned.filter{it.startsWith("template_")||QuickEntry.isTemplateShortcut(it)&&(locked||it !in byId)}
        if(gone.isNotEmpty())ShortcutManagerCompat.disableShortcuts(context,gone,context.getString(R.string.core_system_shortcut_unavailable))
        val live=pinned.filter{!locked&&QuickEntry.isTemplateShortcut(it)&&it in byId}.map{shortcut(byId.getValue(it),0)}
        if(live.isNotEmpty()){ShortcutManagerCompat.enableShortcuts(context,live);ShortcutManagerCompat.updateShortcuts(context,live)}
    }
    private fun action(id:String,label:Int,icon:Int,entry:QuickEntry.Entry,rank:Int)=ShortcutInfoCompat.Builder(context,id)
        .setShortLabel(context.getString(label)).setIcon(IconCompat.createWithResource(context,icon)).setIntent(QuickEntry.intent(context,entry)).setRank(rank).build()
    private fun shortcut(t:QuickTemplate,rank:Int)=ShortcutInfoCompat.Builder(context,QuickEntry.shortcutId(t.id)).setShortLabel(t.name.ifBlank{context.getString(R.string.add_transaction)})
        .setLongLabel(QuickEntry.label(t)).setIcon(IconCompat.createWithResource(context,R.drawable.ic_shortcut_add_transaction)).setIntent(QuickEntry.templateIntent(context,t.id)).setRank(rank).build()
}
