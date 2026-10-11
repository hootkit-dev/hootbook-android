@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.quicktemplates

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.presentation.format.coreDateText
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookSwitch
import sh.calvin.reorderable.rememberReorderableLazyListState

import sh.calvin.reorderable.ReorderableItem

import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon


import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.draw.clip

import androidx.compose.material.icons.filled.DragHandle

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.background

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.features.categories.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation as CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal

/**
 * Settings → Quick templates. Lists the user's quick-add templates with edit (name, amount,
 * whether the amount is pre-filled), reorder, and delete. Templates are created from the Add
 * Transaction form or from a transaction's detail menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTemplatesScreen(
    onNavigateBack: () -> Unit,
    viewModel: QuickTemplatesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val templates = uiState.templates
    val categories = uiState.categories
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    var editing by remember { mutableStateOf<QuickTemplate?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.quick_add_templates),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        uiState.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(paddingValues)) }
        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(Dimensions.Padding.empty),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(R.string.quick_templates_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Local copy so the drag reorders immediately; the saved order arrives asynchronously.
            var ordered by remember(templates) { mutableStateOf(templates) }
            val lazyListState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                val list = ordered.toMutableList()
                list.add(to.index, list.removeAt(from.index))
                ordered = list
                viewModel.reorder(list)
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                contentPadding = PaddingValues(
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                    bottom = Dimensions.Padding.content
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(ordered, key = { it.id }) { template ->
                    ReorderableItem(state = reorderableState, key = template.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                        QuickTemplateRow(
                            template = template,
                            categoryEntity = categories[template.categoryId],
                            subcategoryEntity = uiState.subcategories[template.subcategoryId],
                            onEdit = { editing = template },
                            onDelete = { viewModel.delete(template.id) },
                            dragModifier = Modifier.draggableHandle(),
                            modifier = Modifier.shadow(elevation, RoundedCornerShape(Dimensions.Radius.md))
                        )
                    }
                }
            }
        }
    }

    editing?.let { template ->
        QuickTemplateEditDialog(
            template = template,
            onDismiss = { editing = null },
            onSave = { name, amount, prefill ->
                viewModel.update(template, name, amount, prefill) { editing = null }
            }
        )
    }
}

/** One template, laid out like a transaction row: category icon, name, details, actions, drag handle. */
@Composable
private fun QuickTemplateRow(
    template: QuickTemplate,
    categoryEntity: Category?,
    subcategoryEntity: Subcategory?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.Radius.md))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandIcon(
            merchantName = template.merchant,
            size = 40.dp,
            showBackground = true,
            categoryEntity = categoryEntity,
            category = categoryEntity?.displayName,
            subcategoryEntity = subcategoryEntity
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val notPrefilled = stringResource(R.string.quick_template_amount_not_prefilled)
            val amountText = template.amount?.let {
                (template.currency?.let { currency -> CurrencyFormatter.formatCurrency(it, currency) } ?: CurrencyFormatter.formatAmount(it)) +
                    if (template.prefillAmount) "" else " ($notPrefilled)"
            }
            Text(
                text = listOfNotNull(categoryEntity?.displayName, subcategoryEntity?.displayName, amountText).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onEdit) {
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
        }
        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
        }
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragModifier.padding(horizontal = Spacing.sm)
        )
    }
}

@Composable
private fun QuickTemplateEditDialog(
    template: QuickTemplate,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: BigDecimal?, prefillAmount: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(template.name) }
    var amountText by remember { mutableStateOf(template.amount?.toPlainString() ?: "") }
    var prefill by remember { mutableStateOf(template.prefillAmount) }
    val amount = amountText.trim().toBigDecimalOrNull()
    val amountInvalid = amountText.isNotBlank() && amount == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_template_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.quick_template_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.quick_template_amount)) },
                    isError = amountInvalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = prefill && amount != null,
                            enabled = amount != null,
                            role = Role.Switch,
                            onValueChange = { prefill = it }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.quick_template_prefill_amount),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HootbookSwitch(checked = prefill && amount != null, onCheckedChange = null, enabled = amount != null)
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { onSave(name, amount, prefill) }, enabled = !amountInvalid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
