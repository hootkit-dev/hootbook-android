package io.github.hootkit_dev.hootbook.presentation.format

import androidx.annotation.StringRes
import dev.hootkit.core.ConflictReason
import dev.hootkit.core.CoreException
import dev.hootkit.core.InvalidReason
import io.github.hootkit_dev.hootbook.R

/** Maps structured Core errors to specific user-facing messages when applicable. */
@StringRes
internal fun coreFailureText(error: Throwable): Int =
    when (error) {
        is CoreException.Conflict ->
            when (error.reason) {
                ConflictReason.TAG_EXISTS -> R.string.core_tags_error_exists
                else -> R.string.onboarding_operation_failed
            }
        is CoreException.Invalid ->
            when {
                error.field == "receivable" -> R.string.core_receivable_exceeds_amount
                error.field == "collected" -> R.string.core_collected_exceeds_amount
                error.reason == InvalidReason.NOT_ALLOWED -> R.string.core_conflict_per_field_deleted
                else -> R.string.onboarding_operation_failed
            }
        else -> R.string.onboarding_operation_failed
    }
