package io.github.hootkit_dev.hootbook.core.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import io.github.hootkit_dev.hootbook.core.backup.*
import io.github.hootkit_dev.hootbook.data.preferences.*
import io.github.hootkit_dev.hootbook.presentation.app.UiLocalePreference
import dev.hootkit.core.*
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],manifest=Config.NONE)
class CoreUiPlatformTest {
    @get:Rule val temp=TemporaryFolder()
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun clear(){File(context.noBackupFilesDir,"hootkit-core/ui").deleteRecursively();File(context.filesDir,"datastore").deleteRecursively()}
    private fun image(file:File):File {file.parentFile?.mkdirs();file.outputStream().use{Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,it)};return file}
    @Test fun fullWhitelistRoundTripAndNoCredentialsOrBusinessFields(){
        val source=CoreUiPreferences(darkTheme=true,font=AppFont.SN_PRO,accent=AccentColor.RED,blur=true,userName="Person",avatar="images/a.image",banner="images/b.image",homeOrder=HomeWidget.entries.reversed(),hiddenHomeWidgets=setOf(HomeWidget.LOANS),unifiedDisplayCurrency="USD",defaultEntryCurrency="CNY",mutedSubscriptionIds=setOf("core-string-id"),onboardingFinished=true,appLockEnabled=true)
        val json=CoreUiPreferencesCodec.encode(source).put("api_key","NEVER_EXPORT").put("default_account",999).put("last_auth_timestamp",Long.MAX_VALUE)
        val result=CoreUiPreferencesCodec.decode(json.toString(),CoreUiPreferences())
        assertEquals(source,result);val exported=CoreUiPreferencesCodec.encode(result).toString()
        assertFalse(exported.contains("NEVER_EXPORT"));assertFalse(exported.contains("default_account"));assertFalse(exported.contains("last_auth"))
    }
    @Test fun legacyThemeProfileLayoutMappingDoesNotRestoreOldBusinessIds(){
        val source="""{"theme":{"app_font":"SN_PRO","accent_color":"GREEN","is_amoled_mode":true},"profile":{"user_name":"Legacy","profile_image_uri":"profile/profile_image"},"home_widgets":{"order":["LOANS","NETWORTH_SUMMARY"],"hidden":["RECENT_TRANSACTIONS"]},"currency":{"unified_currency_enabled":true,"unified_currency_code":"USD","default_account":123},"disabled_subscription_notification_ids":["12"]}"""
        val p=CoreUiPreferencesCodec.decode(source,CoreUiPreferences(),true)
        assertEquals(AppFont.SN_PRO,p.font);assertTrue(p.amoled);assertEquals("Legacy",p.userName);assertEquals(HomeWidget.LOANS,p.homeOrder.first());assertTrue(p.mutedSubscriptionIds.isEmpty())
    }
    @Test fun malformedKnownPreferencesFailWithoutPublishingPartialState()=runTest {
        clear();val prefs=CoreUiPreferencesStore(context);prefs.update{it.copy(userName="Original")}
        try{prefs.applyJson("""{"format":"hootbook.android.ui","version":1,"theme":{"dynamic":"false"},"profile":{"name":"Changed"}}""");fail()}catch(_:IllegalArgumentException){}
        assertEquals("Original",prefs.state.value.userName);assertEquals("Original",CoreUiPreferencesStore(context).ready().userName)
    }
    @Test fun unknownVersionRejectedAndHomeOrderNormalized(){
        try{CoreUiPreferencesCodec.decode("""{"format":"hootbook.android.ui","version":99}""",CoreUiPreferences());fail()}catch(_:IllegalArgumentException){}
        val p=CoreUiPreferencesCodec.validate(CoreUiPreferences(homeOrder=listOf(HomeWidget.LOANS,HomeWidget.LOANS)))
        assertEquals(HomeWidget.entries.size,p.homeOrder.size);assertEquals(p.homeOrder.size,p.homeOrder.distinct().size)
    }
    @Test fun contentAddressedAssetsArePortableIdempotentAndRejectTraversal()=runTest {
        clear();val assets=CorePlatformAssets(context);val source=image(temp.newFile("image.png"));val first=assets.installImage(source);val second=assets.installImage(source)
        assertEquals(first,second);assertNotNull(assets.resolve(first));assertTrue(source.exists())
        assertTrue(assets.exportFiles().any{it.entryName=="platform/$first"})
        for(path in listOf("../secret","/absolute","folder/../secret","folder\\secret"))try{assets.resolve(path);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun malformedAndOversizedImagesNeverBecomeProfileAssets()=runTest {
        clear();val assets=CorePlatformAssets(context);val file=temp.newFile("invalid").apply{writeText("not-image")}
        try{assets.installImage(file);fail()}catch(_:IllegalArgumentException){}
        assertTrue(assets.exportFiles().isEmpty())
        try{CorePlatformAssets.readLimited(ByteArrayInputStream(ByteArray(20)),10);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun legacyImageAndMerchantIndexActuallyInstalledAndReapplyHasNoLedgerWrite()=runTest {
        clear();val prefs=CoreUiPreferencesStore(context);val assets=CorePlatformAssets(context);val locale=FakeLocale()
        var restored = 0
        val applier=BackupPlatformApplier(prefs,assets,locale,{},{}, { restored++ });val op=operation(true,"""{"profile":{"user_name":"Restored","profile_image_uri":"file:///old/install/private/avatar.jpg"}}""",listOf("profile/profile_image","merchant_icons/icon.png","merchant_icons/index.json"))
        image(File(op.source.file.parentFile,"platform/profile/profile_image"));image(File(op.source.file.parentFile,"platform/merchant_icons/icon.png"))
        File(op.source.file.parentFile,"platform/merchant_icons/index.json").writeText("""{"Coffee":"icon.png"}""")
        applier.apply(op);assertEquals("Restored",prefs.state.value.userName);assertNotNull(assets.resolve(prefs.state.value.avatar));assertNotNull(assets.merchantIcon(" coffee "))
        assertEquals(1, restored)
        val first=prefs.state.value;applier.apply(op);assertEquals(first,prefs.state.value);assertTrue(op.source.file.exists());assertEquals(2, restored)
    }
    @Test fun localeFailureDoesNotApplyPreferencesAndCompensatesCoreLocale()=runTest {
        clear();val prefs=CoreUiPreferencesStore(context);prefs.update{it.copy(userName="Before")};val locale=FakeLocale().apply{failure=true};val calls=mutableListOf<String>()
        var restored = 0
        val applier=BackupPlatformApplier(prefs,CorePlatformAssets(context),locale,{calls+=it},{}, { restored++ })
        val json=CoreUiPreferencesCodec.encode(CoreUiPreferences(userName="After")).put("locale","ja").toString()
        try{applier.apply(operation(false,json,emptyList()));fail()}catch(_:IllegalStateException){}
        assertEquals("Before",prefs.state.value.userName);assertEquals(listOf("ja","en"),calls);assertEquals(0, restored)
    }
    @Test fun exportContainsActivePreferenceAndAssetReferences()=runTest {
        clear();val prefs=CoreUiPreferencesStore(context);val assets=CorePlatformAssets(context);val path=assets.installImage(image(temp.newFile("avatar.png")));prefs.update{it.copy(userName="Saved",avatar=path)}
        val snapshot=BackupPlatformApplier(prefs,assets,FakeLocale(),{},{}).exportSnapshot()
        assertEquals("Saved",JSONObject(snapshot.preferencesJson).getJSONObject("profile").getString("name"));assertTrue(snapshot.files.any{it.entryName=="platform/$path"})
    }

    @Test fun newBackupMerchantIndexAndProfileResolvePortableImagePaths()=runTest {
        clear();val prefs=CoreUiPreferencesStore(context);val assets=CorePlatformAssets(context)
        val source=image(temp.newFile("new-image.png"));val original=assets.installImage(source)
        val json=CoreUiPreferencesCodec.encode(CoreUiPreferences(avatar=original)).toString()
        val op=operation(false,json,listOf(original,"merchant_icons/index.json"))
        image(File(op.source.file.parentFile,"platform/$original"))
        File(op.source.file.parentFile,"platform/merchant_icons/index.json").apply{parentFile.mkdirs();writeText(JSONObject().put("store",original).toString())}
        BackupPlatformApplier(prefs,assets,FakeLocale(),{},{}).apply(op)
        assertNotNull(assets.resolve(prefs.state.value.avatar));assertNotNull(assets.merchantIcon("store"))
    }
    @Test fun backupNeverRestoresMuteIdentifiersOrDisablesDeviceSecurity()=runTest {
        clear();val prefs=CoreUiPreferencesStore(context);prefs.update{it.copy(appLockEnabled=true,mutedSubscriptionIds=setOf("existing-core-id"))}
        val json=CoreUiPreferencesCodec.encode(CoreUiPreferences(appLockEnabled=false,mutedSubscriptionIds=setOf("backup-remapped-id"))).toString()
        BackupPlatformApplier(prefs,CorePlatformAssets(context),FakeLocale(),{},{}).apply(operation(false,json,emptyList()))
        assertTrue(prefs.state.value.appLockEnabled);assertEquals(setOf("existing-core-id"),prefs.state.value.mutedSubscriptionIds)
        assertTrue(JSONObject(prefs.exportJson()).getJSONObject("notifications").getJSONArray("muted").length()==0)
    }
    @Test fun missingProfileFileDoesNotPublishPreferencesOrGuessOldUri()=runTest {
        clear();val prefs=CoreUiPreferencesStore(context);val applier=BackupPlatformApplier(prefs,CorePlatformAssets(context),FakeLocale(),{},{})
        val op=operation(true,"""{"profile":{"user_name":"Do not publish","profile_image_uri":"file:///unknown/image.jpg"}}""",emptyList())
        try{applier.apply(op);fail()}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}
        assertEquals("",prefs.state.value.userName);assertTrue(op.source.file.exists())
    }
    @Test fun malformedKnownSectionAndFractionalMinuteAreRejected(){
        for(json in listOf("""{"format":"hootbook.android.ui","version":1,"theme":"bad"}""", """{"format":"hootbook.android.ui","version":1,"notifications":{"minutes":12.5}}""")){
            try{CoreUiPreferencesCodec.decode(json,CoreUiPreferences());fail()}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun pureUiLegacyProtobufMigrationLeavesOriginalBytesAndDropsBusinessSecrets()=runTest {
        clear()
        val legacy=File(context.filesDir,"datastore/user_preferences.preferences_pb").apply{parentFile.mkdirs()}
        val legacyJob=kotlinx.coroutines.SupervisorJob();val scope=kotlinx.coroutines.CoroutineScope(legacyJob+kotlinx.coroutines.Dispatchers.IO)
        val store=androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope=scope,produceFile={legacy})
        store.updateData { androidx.datastore.preferences.core.mutablePreferencesOf(
            androidx.datastore.preferences.core.stringPreferencesKey("user_name") to "Migrated",
            androidx.datastore.preferences.core.booleanPreferencesKey("is_amoled_mode") to true,
            androidx.datastore.preferences.core.booleanPreferencesKey("app_lock_enabled") to true,
            androidx.datastore.preferences.core.longPreferencesKey("last_auth_timestamp") to Long.MAX_VALUE,
            androidx.datastore.preferences.core.stringPreferencesKey("base_currency") to "BAD",
            androidx.datastore.preferences.core.stringPreferencesKey("system_prompt") to "DO_NOT_COPY",
        ) }
        legacyJob.cancel();legacyJob.join()
        val original=legacy.readBytes();val prefs=CoreUiPreferencesStore(context);val p=prefs.ready()
        assertEquals("Migrated",p.userName);assertTrue(p.amoled);assertTrue(p.appLockEnabled)
        assertArrayEquals(original,legacy.readBytes());val json=prefs.exportJson()
        assertFalse(json.contains("DO_NOT_COPY"));assertFalse(json.contains("base_currency"));assertFalse(json.contains("last_auth"))
    }


    @Test fun opaqueFilesInsideKnownDirectoriesCommitPlatformPhaseAndStayRetained()=runTest {
        clear();val prefs=CoreUiPreferencesStore(context);val assets=CorePlatformAssets(context)
        val names=listOf("profile/readme.txt","images/data.bin","merchant_icons/custom-metadata.txt")
        val root=temp.newFolder();val files=CoreBackupFiles(root)
        val source=files.stage{ByteArrayInputStream("opaque-backup-source".toByteArray())}
        val access=io.github.hootkit_dev.hootbook.presentation.data.backup.BackupTestAccess().apply {
            summary=summary.copy(preferencesJson=CoreUiPreferencesCodec.encode(CoreUiPreferences(userName="Applied")).toString(),platformFiles=names)
            onImport={directory->for(name in names)File(directory,name).apply{parentFile.mkdirs();writeText("opaque-not-an-image")}}
        }
        val applier=BackupPlatformApplier(prefs,assets,FakeLocale(),{},{})
        val workflow=io.github.hootkit_dev.hootbook.presentation.data.backup.BackupWorkflow(access,files,BackupOperationJournal(files),root,applier::apply)
        val result=workflow.restore(source,false,ImportMode.MERGE,null,false)
        assertEquals(BackupPhase.PLATFORM_APPLIED,result.phase);assertEquals("Applied",prefs.state.value.userName);assertTrue(assets.exportFiles().isEmpty())
        for(name in names)assertEquals("opaque-not-an-image",File(files.platform(source.operationId),name).readText())
        assertEquals(names.toSet(),workflow.preservedAssets().map{it.entryName.removePrefix("platform/")}.toSet())
    }
    @Test fun disabledLockKeepsBackgroundNavigationAndEnabledLockHidesIt(){
        assertTrue(CoreAppLockState(loaded=true,enabled=false,locked=false,foreground=false).allowContent)
        assertFalse(CoreAppLockState(loaded=true,enabled=true,locked=false,foreground=false).allowContent)
        assertFalse(CoreAppLockState(loaded=false,enabled=false,locked=false,foreground=true).allowContent)
        assertFalse(CoreAppLockState(loaded=true,enabled=true,locked=true,foreground=true).allowContent)
    }
    private fun operation(legacy:Boolean,prefs:String,files:List<String>):BackupOperation {
        val root=temp.newFolder();val storage=CoreBackupFiles(root);val id=storage.newId();val source=File(storage.operation(id),"source.backup").apply{writeText("original")}
        return BackupOperation(StagedBackup(id,source,"sha",source.length()),legacy,BackupPhase.CORE_COMMITTED,
            if(!legacy)ImportSummary(1u,0u,0u,prefs,files,false,0u,null)else null,
            if(legacy)LegacyImportSummary(emptyList(),emptyList(),"CNY",prefs,files,emptyMap())else null)
    }
    private class FakeLocale:UiLocalePreference {
        var selected="en";var failure=false
        override fun selection()=selected
        override fun effectiveTag(selection:String)=selection.ifEmpty{"en"}
        override suspend fun apply(selection:String){if(failure)error("locale failed");selected=selection}
    }
    @Test fun lockPolicyUsesMonotonicTimeoutAndColdStartsLocked(){
        val p=CoreLockPolicy();p.configure(true,1);assertTrue(p.foreground(100));p.unlocked(100);p.background(200);assertFalse(p.foreground(60199));p.background(70000);assertTrue(p.foreground(130000))
    }
    @Test fun immediateLockDisableClockResetAndReauthentication(){
        val p=CoreLockPolicy();p.configure(true,0);p.unlocked(5);p.background(6);assertTrue(p.foreground(6));p.unlocked(8);p.background(9);assertTrue(p.foreground(1));p.configure(false,0);assertFalse(p.foreground(10))
    }
}
