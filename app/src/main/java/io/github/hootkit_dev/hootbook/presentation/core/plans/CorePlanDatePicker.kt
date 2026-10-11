package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.DateField
import io.github.hootkit_dev.hootbook.presentation.ui.components.DateTimeField

/** A plan's day (ISO date, blank when cleared) through the shared date picker field. */
@Composable
internal fun CorePlanDateField(value: String, label: String, saving: Boolean, allowClear: Boolean = true, change: (String) -> Unit) =
    DateField(value, label, change, enabled = !saving, allowClear = allowClear, emptyText = stringResource(R.string.txn_any))

/** A plan's moment (ISO date-time) through the shared date and time picker field. */
@Composable
internal fun CorePlanDateTimeField(value: String, label: String, saving: Boolean, change: (String) -> Unit) =
    DateTimeField(value, label, change, enabled = !saving, allowClear = false)
