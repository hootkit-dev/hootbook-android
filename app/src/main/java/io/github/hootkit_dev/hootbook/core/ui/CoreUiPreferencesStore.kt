package io.github.hootkit_dev.hootbook.core.ui

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

@Singleton
class CoreUiPreferencesStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(CoreUiPreferences())
    val state: StateFlow<CoreUiPreferences> = mutable.asStateFlow()
    private val warnings = MutableStateFlow<List<Int>>(emptyList())
    val migrationWarnings:StateFlow<List<Int>> = warnings.asStateFlow()
    private val initialized = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = initialized.asStateFlow()
    private val file = AtomicFile(File(context.noBackupFilesDir, "hootkit-core/ui/preferences.json"))
    /** Explicit device-authenticated recovery only; original bytes remain private and ledger untouched. */
    suspend fun recoverUi(ticket: CoreUiRecoveryTicket): CoreUiPreferences = withContext(Dispatchers.IO) { mutex.withLock {
        ticket.consume()
        val directory = File(context.noBackupFilesDir, "hootkit-core/ui/recovery-${java.util.UUID.randomUUID()}").also { check(it.mkdirs()) }
        listOf(file.baseFile, File(file.baseFile.path + ".bak"), File(context.filesDir, "datastore/user_preferences.preferences_pb"))
            .filter { it.isFile }.forEach { source -> source.copyTo(File(directory, source.name), overwrite = false) }
        // Unknown/enabled lock is never silently disabled. Authentication is not serialized/restored.
        val knownLock = try { JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }).getJSONObject("lock").get("enabled") as? Boolean } catch (_: Exception) { null }
        val recovered = CoreUiPreferences(appLockEnabled = knownLock ?: true)
        write(recovered); mutable.value = recovered; initialized.value = true
        warnings.value = (warnings.value + io.github.hootkit_dev.hootbook.R.string.core_shell_preferences_recovered).distinct()
        recovered
    } }
    suspend fun ready(): CoreUiPreferences = withContext(Dispatchers.IO) { mutex.withLock {
        if (!initialized.value) {
            val value = if (file.baseFile.exists() || File(file.baseFile.path+".bak").exists())
                file.openRead().use { input ->
                    val raw = input.readBytes().toString(Charsets.UTF_8)
                    val lock = JSONObject(raw).getJSONObject("lock")
                    require(lock.get("enabled") is Boolean) { "Local lock policy is missing" }
                    CoreUiPreferencesCodec.decode(raw, CoreUiPreferences())
                }
            else migratePlatformOnly()
            write(value); mutable.value = value; initialized.value = true
        }
        mutable.value
    } }
    suspend fun update(transform: (CoreUiPreferences) -> CoreUiPreferences): CoreUiPreferences {
        ready()
        return withContext(Dispatchers.IO) { mutex.withLock {
            val next = CoreUiPreferencesCodec.validate(transform(mutable.value))
            write(next); mutable.value = next; next
        } }
    }
    // Subscription mute IDs are process-ledger references and Core import can remap them.
    suspend fun exportJson(): String = CoreUiPreferencesCodec.encode(ready().copy(mutedSubscriptionIds=emptySet())).toString()
    suspend fun applyJson(json: String, legacy: Boolean = false): CoreUiPreferences = update { CoreUiPreferencesCodec.decode(json, it, legacy) }
    private fun write(p: CoreUiPreferences) {
        check(file.baseFile.parentFile!!.mkdirs() || file.baseFile.parentFile!!.isDirectory)
        val out=file.startWrite()
        try { out.write(CoreUiPreferencesCodec.encode(p).toString().toByteArray(Charsets.UTF_8)); file.finishWrite(out) }
        catch (failure: Throwable) { file.failWrite(out); throw failure }
    }
    /** Read the existing pure UI protobuf snapshot, not a legacy repository or Room. Never write it. */
    private suspend fun migratePlatformOnly(): CoreUiPreferences {
        val legacyFile=File(context.filesDir,"datastore/user_preferences.preferences_pb")
        if (!legacyFile.isFile) return CoreUiPreferences()
        val legacyScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val map=try { PreferenceDataStoreFactory.create(scope=legacyScope,produceFile={legacyFile}).data.first().asMap().mapKeys { it.key.name } }
            finally { legacyScope.cancel() }
        val theme=JSONObject(); val profile=JSONObject(); val developer=JSONObject(); val app=JSONObject(); val currency=JSONObject()
        listOf("is_dark_theme_enabled" to "dark_theme_enabled", "is_dynamic_color_enabled" to "dynamic_color_enabled", "is_amoled_mode" to "is_amoled_mode", "app_font" to "app_font", "theme_style" to "theme_style", "accent_color" to "accent_color", "blur_effects" to "blur_effects", "app_icon" to "app_icon").forEach { (target,source) -> map[source]?.let { theme.put(target,it) } }
        listOf("user_name","profile_background_color","show_banner_image").forEach { key -> map[key]?.let {profile.put(key,it)} }
        listOf("unified_currency_enabled","unified_currency_code","default_currency_enabled","default_currency_code").forEach { key -> map[key]?.let {currency.put(key,it)} }
        listOf("is_developer_mode_enabled" to "developer_mode_enabled", "is_token_info_enabled" to "token_info_enabled").forEach {(target,source)->map[source]?.let{developer.put(target,it)}}
        map["has_shown_scan_tutorial"]?.let {app.put("has_shown_scan_tutorial",it)}
        val home=JSONObject(); (map["home_widgets_order"] as? String)?.split(",")?.let { home.put("order",org.json.JSONArray(it)) }
        (map["hidden_home_widgets"] as? Set<*>)?.let {home.put("hidden",org.json.JSONArray(it))}
        // Unknown legacy non-security display enums do not strand the startup gate.
        val known = mapOf("app_font" to io.github.hootkit_dev.hootbook.data.preferences.AppFont.entries.map { it.name },
            "theme_style" to io.github.hootkit_dev.hootbook.data.preferences.ThemeStyle.entries.map { it.name },
            "accent_color" to io.github.hootkit_dev.hootbook.data.preferences.AccentColor.entries.map { it.name },
            "app_icon" to io.github.hootkit_dev.hootbook.data.preferences.AppIcon.entries.map { it.name })
        known.forEach { (key, values) -> if (theme.has(key) && theme.opt(key) !in values) {
            theme.remove(key); warnings.value = (warnings.value + io.github.hootkit_dev.hootbook.R.string.core_shell_legacy_defaults).distinct()
        } }
        val migrated=CoreUiPreferencesCodec.decode(JSONObject().put("theme",theme).put("profile",profile).put("developer",developer).put("app",app).put("currency",currency).put("home_widgets",home).toString(),CoreUiPreferences(),true)
        val assets=CorePlatformAssets(context)
        suspend fun legacyImage(reference:String?):String? = try {assets.migrateLegacyImage(reference)}
            catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(_:Exception){warnings.value=(warnings.value+io.github.hootkit_dev.hootbook.R.string.core_ui_legacy_image_unavailable).distinct();null}
        val avatar=legacyImage(map["profile_image_uri"] as? String)
        val banner=legacyImage(map["banner_image_uri"] as? String)
        // Older merchant index lived in SharedPreferences; migrate only existing platform files.
        val oldIcons=context.getSharedPreferences("merchant_icons",Context.MODE_PRIVATE).all
        for((name,value) in oldIcons) {
            val file=value as? String ?: continue
            val source=File(context.filesDir,"merchant_icons/$file")
            if(source.isFile && source.canonicalFile.parentFile==File(context.filesDir,"merchant_icons").canonicalFile) {
                val installed=legacyImage(source.toURI().toString()) ?: continue
                val temp=File(context.noBackupFilesDir,"hootkit-core/ui/merchant-migration.json")
                CorePlatformAssets.writeAtomic(temp,JSONObject().put(name,file).toString().toByteArray())
                assets.restoreMerchantIndex(temp,mapOf("merchant_icons/$file" to installed),true)
                temp.delete()
            }
        }
        return migrated.copy(avatar=avatar,banner=banner,appLockEnabled=map["app_lock_enabled"] as? Boolean ?: false,
            appLockTimeoutMinutes=(map["app_lock_timeout_minutes"] as? Int ?: 1).coerceIn(0,1440),
            notificationsEnabled=map["upcoming_notifications_enabled"] as? Boolean ?: true,
            scanReminderEnabled=map["scan_new_transactions_enabled"] as? Boolean ?: true,
            reminderMinutes=((map["scan_new_transactions_alert_time"] as? Long)?.toInt() ?:1200).coerceIn(0,1439),
            onboardingFinished = (map["onboarding_completed"] as? Boolean) ?: migrated.onboardingFinished,
            firstLaunchTime=map["first_launch_time"] as? Long ?:0,
            reviewShown=map["has_shown_review_prompt"] as? Boolean ?:false,
            lastReviewTime=map["last_review_prompt_time"] as? Long ?:0)
    }
}
