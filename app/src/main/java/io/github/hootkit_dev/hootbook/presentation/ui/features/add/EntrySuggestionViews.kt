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
import io.github.hootkit_dev.hootbook.presentation.format.*
import dev.hootkit.core.CategoryKind
import dev.hootkit.core.PersonRelation
import dev.hootkit.core.Category
import dev.hootkit.core.QuickTemplate
import io.github.hootkit_dev.hootbook.presentation.ui.components.BrandIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.CategoryIcon
import io.github.hootkit_dev.hootbook.utils.CurrencyFormatter

/**
 * Saved templates, then templates suggested from history (marked with a sparkle). A tap
 * fills the form; a long press on a suggestion keeps it as a template or stops suggesting it.
 */
@Composable
fun TemplateRow(
    templates: List<QuickTemplate>,
    suggested: List<dev.hootkit.core.TemplateSuggestion>,
    categories: List<Category>,
    onSelect: (QuickTemplate) -> Unit,
    onSuggestedSelect: (dev.hootkit.core.TemplateSuggestion) -> Unit,
    onPin: (dev.hootkit.core.TemplateSuggestion) -> Unit,
    onDismiss: (dev.hootkit.core.TemplateSuggestion) -> Unit
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
                TemplateChip(name = template.name, merchant = template.merchant, categoryId = template.categoryId, amount = template.amount.takeIf { template.prefillAmount }, currency = template.currency, categories = categories, suggestedUses = null,
                    onClick = { onSelect(template) })
            }
            items(suggested, key = { "suggested:${it.key}" }) { suggestion ->
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    TemplateChip(
                        name = suggestion.name, merchant = suggestion.merchant, categoryId = suggestion.categoryId,
                        amount = suggestion.amount, currency = suggestion.currency,
                        categories = categories,
                        suggestedUses = suggestion.count.toInt(),
                        onClick = { onSuggestedSelect(suggestion) },
                        onLongClick = { menuOpen = true }
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Text(
                            text = stringResource(R.string.template_suggested_desc, suggestion.count.toInt()),
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
    name: String, merchant: String, categoryId: String?, amount: java.math.BigDecimal?, currency: String?,
    categories: List<Category>,
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
                merchantName = merchant,
                size = 22.dp,
                showBackground = false,
                categoryEntity = categories.find { it.id == categoryId },
            )
            val amountText = amount
                ?.let { " · " + CurrencyFormatter.formatCurrency(it, currency ?: "CNY") }
                ?: ""
            Text(
                text = name + amountText,
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

@Composable
fun MerchantSuggestionRow(
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        items(suggestions, key = { it }) { suggestion ->
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
                        merchantName = suggestion,
                        size = 20.dp,
                        showBackground = false,
                    )
                    Text(suggestion, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)

                }
            }
        }
    }
}
