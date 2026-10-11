package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.applock

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager

/** Original device capability descriptions; authentication and lock policy stay in the controller. */
internal fun deviceBiometricCapability(context: Context, available: Boolean): BiometricCapability {
    if (available) return BiometricCapability.Available
    val methods = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return when (BiometricManager.from(context).canAuthenticate(methods)) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NoHardware
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.HardwareUnavailable
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NoneEnrolled
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricCapability.SecurityUpdateRequired
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricCapability.Unsupported
        else -> BiometricCapability.Unknown
    }
}

sealed class BiometricCapability {
    object Available : BiometricCapability()
    object NoHardware : BiometricCapability()
    object HardwareUnavailable : BiometricCapability()
    object NoneEnrolled : BiometricCapability()
    object SecurityUpdateRequired : BiometricCapability()
    object Unsupported : BiometricCapability()
    object Unknown : BiometricCapability()

    fun getErrorMessage(): String = when (this) {
        Available -> ""
        NoHardware -> "This device doesn't have biometric hardware"
        HardwareUnavailable -> "Biometric hardware is currently unavailable"
        NoneEnrolled -> "No biometric credentials enrolled. Please set up fingerprint or face unlock in device settings"
        SecurityUpdateRequired -> "Security update required for biometric authentication"
        Unsupported -> "Biometric authentication is not supported on this device"
        Unknown -> "Unknown biometric status"
    }
}
