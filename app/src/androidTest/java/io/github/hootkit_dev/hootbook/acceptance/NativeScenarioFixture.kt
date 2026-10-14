package io.github.hootkit_dev.hootbook.acceptance

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.hootkit.core.*
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.BuildConfig
import io.github.hootkit_dev.hootbook.HootbookApplication
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.widget.QuickAddWidget
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate as JavaDate
import java.time.LocalDateTime as JavaTime
import java.util.UUID
import kotlinx.coroutines.withTimeout

/** Test-only; shares actual production Hilt Core, never opens another DB or edits SQL. */
class NativeScenarioFixture {
    val context:Context=InstrumentationRegistry.getInstrumentation().targetContext
    init {
        check(BuildConfig.DEBUG&&BuildConfig.CORE_ACCEPTANCE&&context.packageName.endsWith(".coreacceptance"))
        check(context.applicationContext is HootbookApplication){"A plain/isolated Application is not real App acceptance"}
    }
    private val entry=EntryPointAccessors.fromApplication(context.applicationContext,QuickAddWidget.WidgetEntryPoint::class.java)
    val session:CoreSession=entry.coreSession()
    val preferences:CoreUiPreferencesStore=entry.preferences()
    val output=File(context.filesDir,"device-acceptance").apply{mkdirs()}
    val tag="Acceptance-"+UUID.randomUUID().toString().take(8)
    suspend fun core()=withTimeout(30_000){session.awaitCore()}
    fun text(id:Int)=context.getString(id)
    fun note(case:String,message:String){File(output,"native-events.log").appendText("${java.time.Instant.now()} $case $message\n");android.util.Log.i("NativeAcceptance","$case $message")}
    suspend fun seed():Seed {
        val c=core();check(c.version().spec=="1.1")
        val now=JavaTime.now().nativeTime();val day=JavaDate.now()
        val account=c.accounts().use{it.create(NewAccount("$tag Wallet",null,AccountKind.WALLET,"CNY",BigDecimal("1000"),now,null,null,null,null,"wallet","#336699",emptyList()))}
        val second=c.accounts().use{it.create(NewAccount("$tag Savings",null,AccountKind.BANK,"CNY",BigDecimal("100"),now,null,null,null,null,"bank","#663399",emptyList()))}
        val category=c.categories().use{it.list(CategoryKind.EXPENSE,false).first().id}
        val transaction=c.ledger().use{it.add(draft(account,category,"$tag Merchant",BigDecimal("12.50"),now))}
        val template=c.plans().use{it.createTemplate(QuickTemplateDraft("$tag Template",TransactionKind.EXPENSE,"$tag Merchant",category,null,BigDecimal("4"),true,account,"CNY","fixture"))}
        val subscription=c.plans().use{it.createSubscription(SubscriptionDraft("$tag Subscription",Money(BigDecimal("8"),"CNY"),Cycle(1u,CycleUnit.MONTH),null,day.nativeDate(),account,category,null,"fixture"))}
        val person=c.plans().use{it.createPerson(PersonDraft("$tag Person","+15551234567","fixture","#336699",null,null))}
        val debt=c.plans().use{it.addDebt(DebtDraft(person,DebtKind.LENT,BigDecimal("20"),"CNY",now,null,"$tag Debt",null,emptyList()))}
        val start=day.minusYears(3).withMonth(1).withDayOfMonth(31)
        val budget=c.budgets().use{it.create(NewBudget("$tag Monthly",BudgetKind.EXPENSE,BudgetPeriod.MONTHLY,Money(BigDecimal("200"),"CNY"),start.nativeDate(),null,null,"#336699",emptyList()))}
        val seed=Seed(account,second,category,transaction,template,subscription,person,debt,budget)
        note("seed","spec=${c.version().spec} ids=$seed")
        return seed
    }
    fun draft(account:String?,category:String?,merchant:String,amount:BigDecimal=BigDecimal.ONE,at:LocalDateTime=JavaTime.now().nativeTime(),kind:TransactionKind=TransactionKind.EXPENSE,to:String?=null)=TransactionDraft(
        kind=kind,amount=amount,currency="CNY",dateTime=at,accountId=account,toAccountId=to,toCurrency=if(to!=null)"CNY" else null,toAmount=if(to!=null)amount else null,
        categoryId=category,subcategoryId=null,merchant=merchant,notes="native fixture",subscriptionId=null,attachments=emptyList())
    /** Root-approved TEST-ONLY fixture CA, same existing singleton transport, HTTPS/hostname guard unchanged. */
    fun fixtureTlsTransport():()->Unit{
        val component=(context.applicationContext as dagger.hilt.internal.GeneratedComponentManager<*>).generatedComponent()
        fun provided(name:String):Any{val field=component.javaClass.getDeclaredField(name).apply{isAccessible=true};return (field.get(component) as javax.inject.Provider<*>).get()!!}
        val transport=provided("provideHttpTransportProvider") as dev.hootkit.platform.OkHttpTransport
        val factory=provided("provideCoreFactoryProvider")
        val factoryHttp=factory.javaClass.getDeclaredField("http").apply{isAccessible=true}.get(factory)
        check(factoryHttp===transport){"Fixture must use existing singleton native callback"}
        val field=transport.javaClass.getDeclaredField("client").apply{isAccessible=true}
        val original=field.get(transport) as okhttp3.OkHttpClient
        val cert=File(output,"fixture-ca.pem").inputStream().use{java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(it)}
        val store=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply{load(null,null);setCertificateEntry("fixture-only",cert)}
        val trust=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply{init(store)}.trustManagers.single() as javax.net.ssl.X509TrustManager
        val ssl=javax.net.ssl.SSLContext.getInstance("TLS").apply{init(null,arrayOf(trust),java.security.SecureRandom())}
        val fixture=original.newBuilder().sslSocketFactory(ssl.socketFactory,trust).build()
        check(fixture.hostnameVerifier===original.hostnameVerifier){"Default hostname verifier must remain"}
        field.set(transport,fixture);check(field.get(transport)===fixture)
        note("tls-fixture","existing Hilt factory transport identity verified; testclient trusts ONLY own CA, default hostname verifier intact, no App/Core/host/device trust config change")
        return {field.set(transport,original);check(field.get(transport)===original);note("tls-rollback","original singleton OkHttpClient identity restored")}
    }
    /** Real installed Firebase SDK + App backend against demo loopback, never real credentials. */
    suspend fun firebaseDemoRoundTrip(){
        val clientType=Class.forName("io.github.hootkit_dev.hootbook.core.sync.firebase.CoreFirebaseClient")
        val client=clientType.getConstructor(Context::class.java).newInstance(context)
        val auth=clientType.getMethod("getAuth").invoke(client)
        val task=auth.javaClass.getMethod("signInAnonymously").invoke(auth)
        val tasks=Class.forName("com.google.android.gms.tasks.Tasks")
        tasks.getMethod("await",Class.forName("com.google.android.gms.tasks.Task"),Long::class.javaPrimitiveType,java.util.concurrent.TimeUnit::class.java)
            .invoke(null,task,30L,java.util.concurrent.TimeUnit.SECONDS)
        val user=auth.javaClass.getMethod("getCurrentUser").invoke(auth)
        val uid=user.javaClass.getMethod("getUid").invoke(user) as String
        val backendType=Class.forName("io.github.hootkit_dev.hootbook.core.sync.firebase.CoreFirebaseV2Backend")
        val first=backendType.getConstructor(clientType).newInstance(client) as io.github.hootkit_dev.hootbook.core.sync.CoreV2Backend
        val second=backendType.getConstructor(clientType).newInstance(client) as io.github.hootkit_dev.hootbook.core.sync.CoreV2Backend
        val path="users/$uid/v2_meta/device_acceptance"
        first.transaction{tx->tx.set(path,mapOf("counter" to 1L,"fixture" to tag))}
        check(second.getServer(path)?.fields?.get("counter")==1L)
        second.transaction{tx->check(tx.get(path)?.fields?.get("counter")==1L);tx.set(path,mapOf("counter" to 2L,"fixture" to tag))}
        check(first.getServer(path)?.fields?.get("counter")==2L)
        try{first.getServer("users/not-$uid/v2_meta/device_acceptance");error("Cross UID read must reject")}
        catch(_:PlatformException.Unauthorized){}
        first.deleteBatch(listOf(path));check(second.getServer(path)==null)
        auth.javaClass.getMethod("signOut").invoke(auth)
        note("firebase-demo","real Firebase SDK anonymous demo Auth + SERVER read/write + transaction across two backend wrappers + crossUID denied + delete; not two Core sync engines/live Google")
    }

    fun png():File=File(context.cacheDir,"fixture-${UUID.randomUUID()}.png").also{f->
        val bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.BLUE)
        try{f.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{bitmap.recycle()}
    }
    suspend fun attachment():String {
        val store=CoreAttachmentStore(context)
        val ref=store.importStream(png().inputStream(),"png");store.retainForCore(listOf(ref));check(store.resolve(ref)?.isFile==true);return ref
    }
}
data class Seed(val account:String,val second:String,val category:String,val transaction:String,val template:String,val subscription:String,val person:String,val debt:String,val budget:String)
fun JavaDate.nativeDate()=LocalDate(year,monthValue.toUByte(),dayOfMonth.toUByte())
fun JavaTime.nativeTime()=LocalDateTime(year,monthValue.toUByte(),dayOfMonth.toUByte(),hour.toUByte(),minute.toUByte(),second.toUByte(),0u)
