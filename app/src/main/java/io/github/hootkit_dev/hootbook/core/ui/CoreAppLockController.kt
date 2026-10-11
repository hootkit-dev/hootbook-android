package io.github.hootkit_dev.hootbook.core.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hootkit_dev.hootbook.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Access is process-memory only. Restoring UI preferences never restores an authentication timestamp. */
data class CoreAppLockState(val loaded: Boolean=false, val enabled: Boolean=false, val locked: Boolean=true,
    val foreground: Boolean=true, val authenticating: Boolean=false, val errorResource: Int?=null) {
    val allowContent get()=loaded && !locked && (!enabled || foreground)
}

@Singleton
class CoreAppLockController @Inject constructor(@ApplicationContext private val context:Context, private val preferences:CoreUiPreferencesStore) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow(CoreAppLockState())
    val state=mutable.asStateFlow()
    private val policy=CoreLockPolicy()
    private var prompt:BiometricPrompt?=null
    private var loading:kotlinx.coroutines.Job?=null
    init { retryLoading() }
    fun retryLoading() {if(loading?.isActive==true)return;loading=scope.launch {
        try {
            val p=preferences.ready(); policy.configure(p.appLockEnabled,p.appLockTimeoutMinutes)
            mutable.value=mutable.value.copy(loaded=true,enabled=p.appLockEnabled,locked=p.appLockEnabled,errorResource=null)
            preferences.state.collect { next ->
                val changed=policy.enabled!=next.appLockEnabled
                policy.configure(next.appLockEnabled,next.appLockTimeoutMinutes)
                mutable.value=mutable.value.copy(enabled=next.appLockEnabled,locked=if(changed)next.appLockEnabled else mutable.value.locked)
            }
        } catch(failure:kotlinx.coroutines.CancellationException){throw failure}
        catch(_:Exception){mutable.value=mutable.value.copy(loaded=false,locked=true,errorResource=R.string.core_ui_lock_load_failed)}
    } }
    fun onBackground() {
        if(!mutable.value.foreground)return
        policy.background(SystemClock.elapsedRealtime())
        mutable.value=mutable.value.copy(foreground=false)
    }
    fun onForeground() {
        if(mutable.value.foreground)return
        val lock=policy.foreground(SystemClock.elapsedRealtime())
        mutable.value=mutable.value.copy(foreground=true,locked=if(policy.enabled)mutable.value.locked||lock else false)
    }
    fun lockNow() {if(policy.enabled){policy.lock();mutable.value=mutable.value.copy(locked=true)}}
    /** Disabling occurs only inside the already unlocked gate. Enabling requires fresh device auth. */
    suspend fun setEnabled(enabled:Boolean) {
        require(!enabled) {"Use enableWithAuthentication to enable app lock"}
        preferences.update {it.copy(appLockEnabled=false)}
        policy.configure(false,preferences.state.value.appLockTimeoutMinutes)
        mutable.value=mutable.value.copy(enabled=false,locked=false,errorResource=null)
    }
    suspend fun enableWithAuthentication(activity:FragmentActivity) {
        check(canAuthenticate()) {"Device authentication is not configured"}
        val authenticated=kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
            showPrompt(activity,
                success={if(continuation.isActive)continuation.resumeWith(Result.success(true))},
                failure={if(continuation.isActive)continuation.resumeWith(Result.success(false))})
            continuation.invokeOnCancellation {prompt?.cancelAuthentication()}
        }
        check(authenticated) {"Authentication cancelled or failed"}
        preferences.update {it.copy(appLockEnabled=true)}
        policy.configure(true,preferences.state.value.appLockTimeoutMinutes);policy.unlocked(SystemClock.elapsedRealtime())
        mutable.value=mutable.value.copy(enabled=true,locked=false,errorResource=null)
    }
    suspend fun setTimeout(minutes:Int) { require(minutes in 0..1440);preferences.update{it.copy(appLockTimeoutMinutes=minutes)};policy.configure(policy.enabled,minutes) }
    private fun authenticators() = if(Build.VERSION.SDK_INT>=30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    fun canAuthenticate()=BiometricManager.from(context).canAuthenticate(authenticators())==BiometricManager.BIOMETRIC_SUCCESS
    fun authenticate(activity:FragmentActivity) {
        showPrompt(activity, success={
            policy.unlocked(SystemClock.elapsedRealtime());mutable.value=mutable.value.copy(locked=false)
        },failure={})
    }
    private fun showPrompt(activity:FragmentActivity,success:()->Unit,failure:()->Unit) {
        if(mutable.value.authenticating||!mutable.value.loaded){failure();return}
        if(!canAuthenticate()){mutable.value=mutable.value.copy(errorResource=R.string.core_ui_lock_enroll);failure();return}
        mutable.value=mutable.value.copy(authenticating=true,errorResource=null)
        val current=BiometricPrompt(activity,ContextCompat.getMainExecutor(context),object:BiometricPrompt.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){
                mutable.value=mutable.value.copy(authenticating=false,errorResource=null);prompt=null;success()
            }
            override fun onAuthenticationError(errorCode:Int,errString:CharSequence){mutable.value=mutable.value.copy(authenticating=false,errorResource=R.string.core_ui_lock_failed);prompt=null;failure()}
            override fun onAuthenticationFailed(){mutable.value=mutable.value.copy(errorResource=R.string.core_ui_lock_failed)}
        })
        prompt=current
        current.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(context.getString(R.string.core_ui_unlock))
            .setSubtitle(context.getString(R.string.core_ui_lock_subtitle)).setAllowedAuthenticators(authenticators()).build())
    }

}

/** Pure monotonic timeout policy, independent of system wall-clock or persisted backup fields. */
internal class CoreLockPolicy {
    var enabled=false;private set
    private var timeout=1
    private var authenticated=false
    private var awayAt:Long?=null
    fun configure(enabled:Boolean,minutes:Int){this.enabled=enabled;timeout=minutes;if(!enabled){authenticated=false;awayAt=null}}
    fun unlocked(now:Long){authenticated=true;awayAt=if(awayAt!=null)now else null}
    fun lock(){authenticated=false}
    fun background(now:Long){if(awayAt==null)awayAt=now}
    fun foreground(now:Long):Boolean {
        val away=awayAt;awayAt=null
        if(!enabled)return false
        if(!authenticated)return true
        val expired=away!=null&&(now<away||now-away>=timeout*60_000L)
        if(expired)authenticated=false
        return expired
    }
}
