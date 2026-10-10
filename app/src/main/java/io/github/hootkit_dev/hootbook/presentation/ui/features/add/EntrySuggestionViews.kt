package io.github.hootkit_dev.hootbook.presentation.ui.features.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.database.entity.CategoryEntity
import io.github.hootkit_dev.hootbook.data.database.entity.QuickTemplateEntity
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategoryIcon
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter

/**
 * Saved templates, then templates suggested from history (marked with a sparkle). A tap
 * fills the form; a long press on a suggestion keeps it as a template or stops suggesting it.
 */
@Composable
fun TemplateRow(
    templates: List<QuickTemplateEntity>,
    suggested: List<SuggestedTemplate>,
    categories: List<CategoryEntity>,
    onSelect: (QuickTemplateEntity) -> Unit,
    onPin: (SuggestedTemplate) -> Unit,
    onDismiss: (SuggestedTemplate) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.quick_add_templates),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates, key = { "saved:${it.id}" }) { template ->
                TemplateChip(template = template, categories = categories, suggestedUses = null,
                    onClick = { onSelect(template) })
            }
            items(suggested, key = { "suggested:${it.key}" }) { suggestion ->
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    val template = remember(suggestion) { suggestion.toTemplate() }
                    TemplateChip(
                        template = template,
                        categories = categories,
                        suggestedUses = suggestion.uses,
                        onClick = { onSelect(template) },
                        onLongClick = { menuOpen = true }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Text(
                            text = stringResource(R.string.template_suggested_desc, suggestion.uses),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.template_suggested_pin)) },
                            onClick = { menuOpen = false; onPin(suggestion) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.template_suggested_dismiss)) },
                            onClick = { menuOpen = false; onDismiss(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateChip(
    template: QuickTemplateEntity,
    categories: List<CategoryEntity>,
    suggestedUses: Int?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val suggested = suggestedUses != null
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (suggested) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
        border = if (suggested) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BrandIcon(
                merchantName = template.merchantName,
                size = 22.dp,
                showBackground = false,
                categoryEntity = categories.find { it.name == template.category },
                category = template.category,
                subcategory = template.subcategory
            )
            val amountText = template.amount
                ?.takeIf { template.prefillAmount }
                ?.let { " · " + CurrencyFormatter.formatCurrency(it, template.currency ?: "CNY") }
                ?: ""
            Text(
                text = template.name + amountText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            if (suggested) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * The categories used most for the current type as one-tap chips, then "All" for the full
 * sheet. Falls back to the type's own categories in display order while there is no history.
 */
@Composable
fun CommonCategoryRow(
    common: List<String>,
    categories: List<CategoryEntity>,
    isIncome: Boolean,
    selected: String,
    onSelect: (String) -> Unit,
    onShowAll: () -> Unit,
    limit: Int = 8
) {
    val shown = remember(common, categories, isIncome) {
        val known = categories.associateBy { it.name }
        val fromHistory = common.mapNotNull { known[it] }
        val fillers = categories.filter { it.isIncome == isIncome && it !in fromHistory }
            .sortedBy { it.displayOrder }
        (fromHistory + fillers).take(limit)
    }
    if (shown.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.common_categories),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { category ->
                FilterChip(
                    selected = category.name == selected,
                    onClick = { onSelect(category.name) },
                    label = { Text(io.github.hootkit_dev.hootbook.presentation.common.categoryName(category.name), maxLines = 1) },
                    leadingIcon = { CategoryIcon(category, 18.dp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            item(key = "all") {
                FilterChip(
                    selected = false,
                    onClick = onShowAll,
                    label = { Text(stringResource(R.string.all_categories)) }
                )
            }
        }
    }
}

/** Merchants from history matching what is typed; a tap fills merchant, category and account. */
@Composable
fun MerchantSuggestionRow(
    suggestions: List<MerchantSuggestion>,
    categories: List<CategoryEntity>,
    onSelect: (MerchantSuggestion) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        items(suggestions, key = { it.merchant }) { suggestion ->
            Surface(
                onClick = { onSelect(suggestion) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BrandIcon(
                        merchantName = suggestion.merchant,
                        size = 20.dp,
                        showBackground = false,
                        categoryEntity = categories.find { it.name == suggestion.category },
                        category = suggestion.category,
                        subcategory = suggestion.subcategory
                    )
                    Text(suggestion.merchant, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(
                        text = io.github.hootkit_dev.hootbook.presentation.common.categoryName(suggestion.subcategory ?: suggestion.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
