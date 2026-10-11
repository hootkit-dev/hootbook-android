@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.core.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hootkit.core.CategoryKind
import dev.hootkit.core.CycleUnit
import dev.hootkit.core.LocalDate
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection
import io.github.hootkit_dev.hootbook.presentation.ui.components.ListSectionScope
import io.github.hootkit_dev.hootbook.presentation.ui.components.detailRow
import io.github.hootkit_dev.hootbook.utils.DateFormats

@Composable internal fun cycleLabel(unit: CycleUnit) = stringResource(when (unit) {
    CycleUnit.DAY -> R.string.core_plans_day; CycleUnit.WEEK -> R.string.core_plans_week
    CycleUnit.MONTH -> R.string.core_plans_month; CycleUnit.YEAR -> R.string.core_plans_year
    CycleUnit.UNKNOWN -> R.string.core_plans_unknown_cycle
})

/** A plan date as the app shows dates (9月1日), or None. */
@Composable internal fun LocalDate?.planDisplay(): String =
    this?.let { DateFormats.shortDate(java.time.LocalDate.of(it.year, it.month.toInt(), it.day.toInt())) } ?: stringResource(R.string.core_plans_none)

/**
 * Account, category and subcategory of a plan as rows that open a picker. A reference to a
 * deleted record is shown, with a warning, rather than silently dropped.
 */
@Composable
internal fun PlanReferencePicker(refs: CorePlanReferences, accountId: String?, categoryId: String?, subcategoryId: String?,
    kind: CategoryKind?, saving: Boolean, account: (String?) -> Unit, category: (String?) -> Unit, subcategory: (String?) -> Unit) {
    val none = stringResource(R.string.core_plans_none)
    var picking by remember { mutableStateOf<String?>(null) }
    val categories = refs.categories.filter { kind == null || it.kind == kind }
    val selected = categories.find { it.id == categoryId }
    val missing = listOfNotNull(accountId?.takeIf { id -> refs.accounts.none { it.id == id } }, categoryId?.takeIf { selected == null },
        subcategoryId?.takeIf { id -> selected != null && selected.subcategories.none { it.id == id } })
    ListSection {
        referenceRow(R.string.core_plans_account, Icons.Rounded.AccountBalanceWallet, refs.accounts.find { it.id == accountId }?.name ?: accountId ?: none, !saving) { picking = "account" }
        referenceRow(R.string.core_plans_category, Icons.Rounded.Category, selected?.displayName ?: categoryId ?: none, !saving) { picking = "category" }
        if (selected != null && selected.subcategories.isNotEmpty())
            referenceRow(R.string.core_plans_subcategory, Icons.Rounded.Category, selected.subcategories.find { it.id == subcategoryId }?.displayName ?: subcategoryId ?: none, !saving) { picking = "subcategory" }
    }
    missing.forEach { Text(stringResource(R.string.core_plans_missing_reference, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    when (picking) {
        "account" -> ReferenceSheet(stringResource(R.string.core_plans_account), accountId, listOf(null to none) + refs.accounts.map { it.id to it.name }, { picking = null }, account)
        "category" -> ReferenceSheet(stringResource(R.string.core_plans_category), categoryId, listOf(null to none) + categories.map { it.id to it.displayName }, { picking = null }, category)
        "subcategory" -> ReferenceSheet(stringResource(R.string.core_plans_subcategory), subcategoryId,
            listOf(null to none) + selected?.subcategories.orEmpty().map { it.id to it.displayName }, { picking = null }, subcategory)
    }
}

private fun ListSectionScope.referenceRow(label: Int, icon: ImageVector, value: String, enabled: Boolean, onClick: () -> Unit) = item(
    leading = { Icon(icon, null) },
    headline = { Text(stringResource(label)) },
    trailing = { Text(value, Modifier.widthIn(max = 180.dp), color = MaterialTheme.colorScheme.primary, maxLines = 1) },
    chevron = true, onClick = if (enabled) onClick else null,
)

@Composable
private fun ReferenceSheet(title: String, selected: String?, options: List<Pair<String?, String>>, onDismiss: () -> Unit, change: (String?) -> Unit) {
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(title, Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            ListSection {
                options.forEach { (id, name) ->
                    item(headline = { Text(name) }, selected = id == selected,
                        trailing = if (id == selected) ({ Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        onClick = { change(id); onDismiss() })
                }
            }
        }
    }
}

/** A plan's account, category and subcategory as label / value rows. */
@Composable
internal fun ListSectionScope.planReferenceRows(refs: CorePlanReferences, accountId: String?, categoryId: String?, subcategoryId: String?) {
    val none = stringResource(R.string.core_plans_none)
    val category = refs.categories.find { it.id == categoryId }
    detailRow(stringResource(R.string.account), refs.accounts.find { it.id == accountId }?.name ?: accountId ?: none)
    detailRow(stringResource(R.string.category), listOfNotNull(category?.displayName ?: categoryId ?: none,
        subcategoryId?.let { id -> category?.subcategories?.find { it.id == id }?.displayName ?: id }).joinToString(" · "))
}
