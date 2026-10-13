package io.github.hootkit_dev.hootbook.acceptance

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.core.CoreRateRefreshCoordinator
import io.github.hootkit_dev.hootbook.core.backup.*
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.presentation.data.backup.*
import io.github.hootkit_dev.hootbook.presentation.app.LocaleController
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Native imports execute on actual production session; run on fresh acceptance APK before UI seeds. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NativeBackupJourneys {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupEntryPoint { fun rates(): CoreRateRefreshCoordinator }
    private lateinit var f:NativeScenarioFixture
    @Before fun ready():Unit=runBlocking{f=NativeScenarioFixture();f.core();f.preferences.ready()}
    private fun workflow(folder:String,apply:(suspend(BackupOperation)->Unit)?=null,access:CoreBackupAccess=SessionBackupAccess(f.session)):BackupWorkflow {
        val root=File(f.context.noBackupFilesDir,"native-acceptance/$folder-${UUID.randomUUID()}")
        val files=CoreBackupFiles(root);return BackupWorkflow(access,files,BackupOperationJournal(files),root,apply)
    }
    private fun applier():BackupPlatformApplier=BackupPlatformApplier(f.context,f.preferences,CorePlatformAssets(f.context),LocaleController(f.context),f.session,
        EntryPointAccessors.fromApplication(f.context.applicationContext,BackupEntryPoint::class.java).rates())
    private fun digest(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
    @Test fun aLegacyEmptyCoreSchemaAndActualProfileMerchantAssets()=runBlocking {
        val c=f.core()
        assertTrue("This test requires a fresh owned acceptance app",c.accounts().use{it.overview().accounts.isEmpty()})
        val image=f.png().readBytes();val zip=File(f.context.cacheDir,"native-legacy-${UUID.randomUUID()}.hbk")
        // Original old exporter/core-source contract: Cashiro Backup v1.0 + backup.json and fixed image roles.
        val backup=JSONObject("""{"_format":"Cashiro Backup v1.0","_created":"2026-10-11 12:00","database":{"accounts":[{"id":1,"name":"Legacy fixture wallet","last4":"wallet","mainCurrency":"CNY","isWallet":true,"createdAt":"2026-01-01T00:00:00"}],"account_balances":[{"id":1,"accountId":1,"bankName":"Legacy fixture wallet","accountLast4":"wallet","currency":"CNY","balance":"50","timestamp":"2026-01-01T00:00:00","sourceType":"MANUAL","isWallet":true,"createdAt":"2026-01-01T00:00:00"}],"transactions":[{"id":1,"accountId":1,"merchantName":"Legacy fixture merchant","amount":"3","category":"Others","transactionType":"EXPENSE","currency":"CNY","dateTime":"2026-01-02T12:00:00"}]},"preferences":{"profile":{"user_name":"Legacy fixture profile","profile_image_uri":"file:///old/private/avatar","banner_image_uri":"content://old/private/banner","show_banner_image":true}}}""")
        ZipOutputStream(zip.outputStream()).use{out->
            for((name,bytes)in listOf("backup.json" to backup.toString().toByteArray(),"profile/profile_image" to image,"profile/banner_image" to image,
                "merchant_icons/index.json" to "{\"legacy fixture merchant\":\"fixture.png\"}".toByteArray(),"merchant_icons/fixture.png" to image)){
                out.putNextEntry(ZipEntry(name));out.write(bytes);out.closeEntry()
            }
        }
        val before=digest(zip);val w=workflow("legacy",applier()::apply);val source=w.files.stage{zip.inputStream()}
        assertNotNull(w.inspect(source,true,null).second)
        val op=w.restore(source,true,ImportMode.MERGE,null,false)
        assertEquals(BackupPhase.PLATFORM_APPLIED,op.phase);assertEquals(before,digest(zip));assertEquals(source.sha256,digest(source.file))
        assertEquals("Legacy fixture profile",f.preferences.state.value.userName)
        val assets=CorePlatformAssets(f.context);assertNotNull(assets.resolve(f.preferences.state.value.avatar));assertNotNull(assets.resolve(f.preferences.state.value.banner));assertNotNull(assets.merchantIcon("Legacy fixture merchant"))
        assertTrue(c.accounts().use{it.overview().accounts.any{row->row.name=="Legacy fixture wallet"}})
        File(f.output,"legacy-fixture.hbk").writeBytes(zip.readBytes());f.note("native-legacy","phase=${op.phase} sourceSha=$before summary=${op.legacySummary}")
    }
    @Test fun bEncryptedExportWrongPasswordMergeAndPlatformAssetsRoundTrip()=runBlocking {
        val seed=f.seed();val assets=CorePlatformAssets(f.context);val image=assets.installImage(f.png())
        f.preferences.update{it.copy(userName="Encrypted fixture profile",avatar=image,banner=image,showBanner=true)}
        val a=applier();val snapshot=a.exportSnapshot();val opaque=File(f.context.cacheDir,"opaque-fixture.txt").apply{writeText("opaque retained")}
        val w=workflow("encrypted",a::apply)
        val exported=w.export(ExportOptions(includeAttachments=true,includeAi=false,includeBrokerage=false,password="fixture-password",preferencesJson=snapshot.preferencesJson,
            platformFiles=snapshot.files+BackupFile("platform/images/unknown.txt",opaque.path),device="native-acceptance"))
        val source=w.files.stage{exported.first.file.inputStream()};val original=digest(source.file)
        assertNotNull(w.inspect(source,false,"fixture-password").first)
        try{w.inspect(source,false,"wrong-password");fail("wrong password must fail before import")}catch(error:CoreException.Invalid){assertEquals("password",error.field)}
        val op=w.restore(source,false,ImportMode.MERGE,"fixture-password",false)
        assertEquals(BackupPhase.PLATFORM_APPLIED,op.phase);assertEquals(original,digest(source.file));assertNotNull(assets.resolve(f.preferences.state.value.avatar))
        assertTrue(w.preservedAssets().any{it.entryName.endsWith("images/unknown.txt")})
        assertNotNull(f.core().accounts().use{it.get(seed.account)})
        File(f.output,"encrypted-fixture.hkbak").writeBytes(exported.first.file.readBytes());f.note("native-backup","encrypted/inspect/passwordfail/merge/platformAssets phase=${op.phase} sourceSha=$original")
    }
    @Test fun cKnownCoreCommittedFailureRetriesOnlyPlatformNotLedger()=runBlocking {
        f.seed();val a=applier();val snapshot=a.exportSnapshot();var imports=0
        val actual=SessionBackupAccess(f.session)
        val counted=object:CoreBackupAccess {
            override suspend fun <T> backup(block:suspend(BackupInterface)->T):T=actual.backup{api->block(object:BackupInterface by api {
                override suspend fun importBackup(path:String,options:ImportOptions):ImportSummary{imports++;return api.importBackup(path,options)}
            })}
            override suspend fun <T> legacy(block:suspend(LegacyInterface)->T):T=actual.legacy(block)
        }
        var failPlatform=true
        val w=workflow("retry",{if(failPlatform)throw IOException("fixture platform unavailable") else a.apply(it)},counted)
        val exported=w.export(ExportOptions(platformFiles=snapshot.files,preferencesJson=snapshot.preferencesJson,password="fixture-password"))
        val source=w.files.stage{exported.first.file.inputStream()}
        try{w.restore(source,false,ImportMode.MERGE,"fixture-password",false);fail("platform failure must remain visible")}catch(_:IOException){}
        val op=w.recover().single();assertEquals(BackupPhase.CORE_COMMITTED,op.phase);assertEquals(1,imports)
        failPlatform=false;val done=w.finishPlatform(op);assertEquals(BackupPhase.PLATFORM_APPLIED,done.phase);assertEquals(1,imports)
        assertEquals(source.sha256,digest(source.file));f.note("native-platform-retry","actual native returned result then platform fail/retry; imports=$imports phase=${done.phase}")
    }
    @Test fun dNativeReplaceAllSafetyBackupAndRetainedSource()=runBlocking {
        val seed=f.seed();val w=workflow("replace",applier()::apply)
        val exported=w.export(ExportOptions(platformFiles=emptyList(),password="fixture-password"))
        val source=w.files.stage{exported.first.file.inputStream()}
        val extra=f.core().ledger().use{it.add(f.draft(null,seed.category,"${f.tag} After export"))}
        val op=w.restore(source,false,ImportMode.REPLACE_ALL,"fixture-password",true)
        assertEquals(BackupPhase.PLATFORM_APPLIED,op.phase);assertTrue(op.importSummary?.safetyBackup?.let{File(it).isFile}==true)
        try{f.core().ledger().use{it.get(extra)};fail("replace-all must remove post-export fixture")}catch(_:CoreException.NotFound){}
        val restored=f.core().ledger().use{it.list(TransactionFilter(from=null,to=null,kinds=emptyList(),accountIds=emptyList(),categoryIds=emptyList(),text="${f.tag} Merchant",currency=null,deleted=DeletedFilter.EXCLUDE,sort=SortOrder.NEWEST_FIRST),PageRequest(0u,50u)).items}
        assertEquals(1,restored.size);assertEquals(0,restored.single().amount.compareTo(java.math.BigDecimal("12.50")))
        assertNotNull(restored.single().accountId);assertEquals(source.sha256,digest(source.file)) // Core import may assign new IDs; do not invent an old->new mapping.
        f.note("native-replace","source preserved and actual Core safety backup=${op.importSummary?.safetyBackup}")
    }
    @Test fun eUnknownImportJournalRecoveryBlocksReplayAndRetainsOriginal()=runBlocking {
        val w=workflow("uncertain");val exported=w.export(ExportOptions(platformFiles=emptyList()))
        val source=w.files.stage{exported.first.file.inputStream()};val files=w.files
        BackupOperationJournal(files).write(BackupOperation(source,false,BackupPhase.IMPORTING))
        val recovered=w.recover().single();assertEquals(BackupPhase.UNCERTAIN,recovered.phase)
        try{w.restore(source,false,ImportMode.MERGE,null,false);fail("uncertain import must never replay")}catch(_:IllegalStateException){}
        assertEquals(source.sha256,digest(source.file));assertEquals(BackupPhase.UNCERTAIN,w.recover().single().phase)
        f.note("journal-fixture-only","IMPORTING fixture -> UNCERTAIN retained source/no replay. Not a claimed native process-kill commit receipt.")
    }
    /** Harness killpoint after actual native return but before App workflow receives it. */
    @Test fun fReturnedNativeImportPausedForActualProcessKill()=runBlocking {
        org.junit.Assume.assumeTrue("Explicit external kill host required",androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("hostProcessKill")=="true")
        val root=File(f.context.noBackupFilesDir,"hootkit-core/backup-ui")
        val files=CoreBackupFiles(root);val actual=SessionBackupAccess(f.session)
        var source:StagedBackup?=null
        val held=object:CoreBackupAccess {
            override suspend fun <T> backup(block:suspend(BackupInterface)->T):T=actual.backup{api->block(object:BackupInterface by api{
                override suspend fun importBackup(path:String,options:ImportOptions):ImportSummary{
                    val result=api.importBackup(path,options)
                    val selected=checkNotNull(source)
                    File(f.output,"actual-process-killpoint.json").writeText(JSONObject().put("root",root.path).put("operationId",selected.operationId)
                        .put("source",selected.file.path).put("sha256",selected.sha256).put("actualNativeReturned",true).put("nativeImports",1).put("added",result.added.toLong()).toString())
                    f.note("actual-process-killpoint","actual native import returned; fixture pauses delivery to workflow still IMPORTING; awaiting external owned process kill")
                    kotlinx.coroutines.awaitCancellation()
                }
            })}
            override suspend fun <T> legacy(block:suspend(LegacyInterface)->T):T=actual.legacy(block)
        }
        val w=BackupWorkflow(held,files,BackupOperationJournal(files),root)
        check(w.recover().none{it.blocksImport})
        val backup=w.export(ExportOptions(includeAi=false,includeBrokerage=false,platformFiles=emptyList()))
        source=files.stage{backup.first.file.inputStream()}
        w.restore(checkNotNull(source),false,ImportMode.MERGE,null,false)
        fail("Host must kill the owned process at explicit returned-native harness marker")
    }
    @Test fun gActualProcessKillRecoveryUsesProductionJournalAndBlocksReplay()=runBlocking{
        org.junit.Assume.assumeTrue("Prior actual host process-kill marker required",File(f.output,"actual-process-killpoint.json").isFile)
        val marker=JSONObject(File(f.output,"actual-process-killpoint.json").readText());assertTrue(marker.getBoolean("actualNativeReturned"))
        val root=File(marker.getString("root"));val files=CoreBackupFiles(root)
        val w=BackupWorkflow(SessionBackupAccess(f.session),files,BackupOperationJournal(files),root)
        val op=w.recover().single{it.source.operationId==marker.getString("operationId")}
        assertEquals(BackupPhase.UNCERTAIN,op.phase);assertEquals(marker.getString("sha256"),digest(op.source.file))
        try{w.restore(op.source,false,ImportMode.MERGE,null,false);fail("uncertain restart must not replay") }catch(_:IllegalStateException){}
        assertEquals(BackupPhase.UNCERTAIN,w.recover().single{it.source.operationId==op.source.operationId}.phase)
        f.note("actual-process-kill-recovery","real owned process killed/reopened; actual native had returned before harness pause; production journal UNCERTAIN/sourceSHA/no replay. Not spontaneous native commit-boundary receipt proof.")
    }

    @Test fun hRecordActualNativeLedgerBeforeSystemEntries()=runBlocking{
        val filter=TransactionFilter(null,null,emptyList(),emptyList(),emptyList(),null,null,DeletedFilter.INCLUDE,SortOrder.NEWEST_FIRST)
        val total=f.core().ledger().use{it.list(filter,PageRequest(0u,1u)).total}
        val template=f.core().plans().use{it.templates().firstOrNull()}
        File(f.output,"system-entry-baseline.json").writeText(JSONObject().put("total",total.toLong()).put("templateId",template?.id).put("templateName",template?.name).toString())
        f.note("system-entry-baseline","actual native ledger total=$total; stable generated templateId=${template?.id}; no seed/write")
    }
    @Test fun iActualSystemEntriesDidNotMutateNativeLedger()=runBlocking{
        org.junit.Assume.assumeTrue("Prior entry baseline required",File(f.output,"system-entry-baseline.json").isFile)
        val before=JSONObject(File(f.output,"system-entry-baseline.json").readText()).getLong("total")
        val filter=TransactionFilter(null,null,emptyList(),emptyList(),emptyList(),null,null,DeletedFilter.INCLUDE,SortOrder.NEWEST_FIRST)
        val after=f.core().ledger().use{it.list(filter,PageRequest(0u,1u)).total}.toLong();assertEquals(before,after)
        f.note("system-entry-after","actual CLI widget/tile/typed-entry journeys bracketed native same ledger total=$after; no implicit write")
    }

}
