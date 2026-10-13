package io.github.hootkit_dev.hootbook.presentation.format

import androidx.annotation.StringRes
import io.github.hootkit_dev.hootbook.R

/** Core diagnostics are for developers; UI failures use the existing localized retry message. */
@StringRes
internal fun coreFailureText(@Suppress("UNUSED_PARAMETER") error: Throwable): Int =
    R.string.onboarding_operation_failed
