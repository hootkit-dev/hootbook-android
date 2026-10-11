package io.github.hootkit_dev.hootbook.core.ui

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.BackupFile
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.backup.BackupOperation
import io.github.hootkit_dev.hootbook.core.backup.CoreBackupFiles
import io.github.hootkit_dev.hootbook.presentation.core.settings.CoreLocaleController
import io.github.hootkit_dev.hootbook.utils.IconSwitchingUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class PlatformBackupSnapshot(val preferencesJson: String, val files: List<BackupFile>)

/** Platform application only; no ledger SQL, credentials, business preferences or ID translation. */
@Singleton
class BackupPlatformApplier internal constructor(
    private val preferences: CoreUiPreferencesStore,
    private val assets: CorePlatformAssets,
    private val locale: io.github.hootkit_dev.hootbook.presentation.core.settings.CoreUiLocalePreference,
    private val setCoreLocale: suspend (String) -> Unit,
    private val switchIcon: suspend (io.github.hootkit_dev.hootbook.data.preferences.AppIcon) -> Unit,
) {
    @Inject constructor(@ApplicationContext context: Context, preferences: CoreUiPreferencesStore,
        assets: CorePlatformAssets, locale: CoreLocaleController, session: CoreSession) : this(
        preferences, assets, locale,
        { tag -> session.awaitCore().settings().use { it.setLocale(tag) } },
        { icon -> withContext(Dispatchers.Main.immediate) { IconSwitchingUtils.switchAppIcon(context, icon) } },
    )
    private val applyMutex=kotlinx.coroutines.sync.Mutex()
    suspend fun exportSnapshot(): PlatformBackupSnapshot {
        val json=JSONObject(preferences.exportJson()).put("locale",locale.selection())
        return PlatformBackupSnapshot(json.toString(),assets.exportFiles())
    }
    suspend fun apply(operation: BackupOperation, onboardingFinished: Boolean? = null) = applyMutex.withLock {
        val before=preferences.ready()
        val restored=withContext(Dispatchers.IO) {
            val directory=java.io.File(operation.source.file.parentFile,"platform")
            val files=operation.files.associateWith { name -> CoreBackupFiles.safeChild(directory,name).also {require(it.isFile)} }
            // Parse and validate every known preference/index/reference before installing anything.
            // Directory membership is not an image type: opaque files under profile/images/icons stay retained.
            val raw=operation.preferences?.let(::JSONObject)
            var decoded=operation.preferences?.let { CoreUiPreferencesCodec.decode(it,before,operation.legacy) } ?: before
            if(operation.legacy) {
                val currency=raw?.optJSONObject("currency")
                val renamed=operation.legacySummary?.renamedCurrencies.orEmpty()
                decoded=decoded.copy(
                    unifiedDisplayCurrency=currency?.optString("unified_currency_code")?.let{renamed[it]}?:decoded.unifiedDisplayCurrency,
                    defaultEntryCurrency=currency?.optString("default_currency_code")?.let{renamed[it]}?:decoded.defaultEntryCurrency,
                )
            }
            val wanted=linkedSetOf<String>()
            fun profileReference(reference:String?,field:String,roleEntry:String):String? {
                if(reference==null)return null
                val legacyProfile=raw?.optJSONObject("profile")
                val actual=if(operation.legacy && legacyProfile?.has(field)==true && files.containsKey(roleEntry)) roleEntry else reference
                if(files.containsKey(actual)){CoreBackupFiles.safeChild(directory,actual);wanted+=actual;return actual}
                require(assets.resolve(actual)!=null){"Restored profile image is missing"}
                return actual
            }
            val avatar=profileReference(decoded.avatar,"profile_image_uri","profile/profile_image")
            val banner=profileReference(decoded.banner,"banner_image_uri","profile/banner_image")
            val index=files["merchant_icons/index.json"]?.let { source ->
                require(source.length()<=256*1024)
                JSONObject(source.readText()).also { entries ->
                    require(entries.length()<=1000)
                    entries.keys().forEach { merchant ->
                        require(merchant.length<=200 && entries.get(merchant) is String)
                        val name=entries.getString(merchant)
                        if(operation.legacy)require(name==java.io.File(name).name && !name.contains('\\'))
                        val path=if(operation.legacy) "merchant_icons/$name" else name
                        CoreBackupFiles.safeChild(directory,path)
                        require(files.containsKey(path)){"Merchant icon file is missing"}
                        wanted+=path
                    }
                }
            }
            val installed=wanted.associateWith { name -> assets.installImage(files.getValue(name)) }
            decoded=decoded.copy(avatar=avatar?.let{installed[it]?:it},banner=banner?.let{installed[it]?:it},
                mutedSubscriptionIds=before.mutedSubscriptionIds,appLockEnabled=before.appLockEnabled,
                onboardingFinished=onboardingFinished ?: decoded.onboardingFinished)
            if(index!=null)assets.restoreMerchantIndex(files.getValue("merchant_icons/index.json"),installed,operation.legacy)
            decoded
        }
        val json=operation.preferences?.let(::JSONObject)
        val selected=if(!operation.legacy&&json?.has("locale")==true) json.getString("locale") else null
        val oldLocale=locale.selection()
        try {
            switchIcon(restored.appIcon)
            if(selected!=null && selected!=oldLocale) applyLocale(selected,oldLocale)
            preferences.update { restored }
        } catch(failure:Throwable) {
            try { switchIcon(before.appIcon) } catch(rollback:Throwable){failure.addSuppressed(rollback)}
            if(locale.selection()!=oldLocale) try { applyLocale(oldLocale,locale.selection()) } catch(rollback:Throwable){failure.addSuppressed(rollback)}
            throw failure
        }
    }
    private suspend fun applyLocale(selected: String, previous: String) {
        setCoreLocale(locale.effectiveTag(selected))
        try { locale.apply(selected) } catch(failure:Throwable) {
            try { setCoreLocale(locale.effectiveTag(previous)) } catch(rollback:Throwable){failure.addSuppressed(rollback)}
            throw failure
        }
    }
}
