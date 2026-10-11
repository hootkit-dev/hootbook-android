package io.github.hootkit_dev.hootbook.presentation.core.navigation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.CoreAppBootstrap
import io.github.hootkit_dev.hootbook.core.ui.CoreSecureWindow
import io.github.hootkit_dev.hootbook.core.ui.CoreUiPreferencesStore
import io.github.hootkit_dev.hootbook.core.ui.CoreUiRecoveryTicket
import kotlinx.coroutines.launch

@Composable internal fun CorePreferencesRecovery(prefs: CoreUiPreferencesStore, bootstrap: CoreAppBootstrap) {
    CoreSecureWindow()
    val context = LocalContext.current; val activity = context.fragmentActivity(); val scope = rememberCoroutineScope()
    val recoveryTitle = stringResource(R.string.core_shell_recover_ui)
    var busy by remember { mutableStateOf(false) }; var failed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text(stringResource(R.string.core_shell_preferences_failed)); Text(stringResource(R.string.core_shell_recovery_note))
        if (failed) Text(stringResource(R.string.core_shell_recovery_auth_failed))
        Button(onClick = bootstrap::start, enabled = !busy) { Text(stringResource(R.string.core_shell_retry)) }
        OutlinedButton(enabled = activity != null && !busy, onClick = {
            val host = activity ?: return@OutlinedButton
            busy = true; failed = false
            val authenticators = if (android.os.Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val ticket = CoreUiRecoveryTicket()
                    scope.launch { try { prefs.recoverUi(ticket); bootstrap.start() } catch (_: Exception) { failed = true } finally { busy = false } }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { busy = false; failed = true }
            })
            try { prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(recoveryTitle).setAllowedAuthenticators(authenticators).build()) }
            catch (_: Exception) { busy = false; failed = true }
        }) { Text(stringResource(R.string.core_shell_recover_ui)) }
        TextButton(onClick = { try { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } catch (_: Exception) { failed = true } }) {
            Text(stringResource(R.string.core_shell_device_security))
        }
    }
}
private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) { is FragmentActivity -> this; is ContextWrapper -> baseContext.fragmentActivity(); else -> null }
