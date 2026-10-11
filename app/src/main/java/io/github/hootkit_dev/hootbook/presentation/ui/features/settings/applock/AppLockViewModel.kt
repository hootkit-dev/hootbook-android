package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hootkit.core.*
import dev.hootkit.platform.*
import io.github.hootkit_dev.hootbook.presentation.data.*
import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import io.github.hootkit_dev.hootbook.core.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import android.content.Context
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hootkit_dev.hootbook.R

data class AppLockUiState(
    val isLockEnabled: Boolean = false,
    val isLocked: Boolean = true,
    val isLoaded: Boolean = false,
    val timeoutMinutes: Int = 1,
    val canUseBiometric: Boolean = false,
    val authenticationError: String? = null,
    val biometricCapability: BiometricCapability = BiometricCapability.Unknown,
    val saving: Boolean = false,
)
@HiltViewModel
class AppLockViewModel @Inject constructor(
    val controller: CoreAppLockController,
    private val preferences: CoreUiPreferencesStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutable = MutableStateFlow(AppLockUiState())
    val uiState = mutable.asStateFlow()
    init { viewModelScope.launch { combine(controller.state, preferences.state) { lock, p -> lock to p }
        .collect { (lock, p) -> mutable.update { it.copy(isLockEnabled = lock.enabled, isLocked = lock.locked,
            isLoaded = lock.loaded, timeoutMinutes = p.appLockTimeoutMinutes, canUseBiometric = controller.canAuthenticate(),
            biometricCapability = deviceBiometricCapability(context, controller.canAuthenticate()),
            authenticationError = lock.errorResource?.let(context::getString) ?: it.authenticationError) } } } }
    fun setAppLockEnabled(enabled: Boolean, activity: FragmentActivity? = null) = command {
        if (enabled) controller.enableWithAuthentication(requireNotNull(activity)) else controller.setEnabled(false)
    }
    fun setTimeoutMinutes(value: Int) = command { controller.setTimeout(value) }
    fun triggerAuthentication(activity: FragmentActivity) { controller.authenticate(activity) }
    fun clearAuthError() { mutable.update { it.copy(authenticationError = null) } }
    private fun command(block: suspend () -> Unit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true, authenticationError = null) }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(authenticationError = context.getString(R.string.msg_auth_failed)) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
}
