package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.transactionKindLabelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTypeSelector(
    selectedKind: TransactionKind,
    onSelectKind: (TransactionKind) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val primaryKinds = listOf(
        TransactionKind.EXPENSE,
        TransactionKind.INCOME,
        TransactionKind.TRANSFER
    )
    val secondaryKinds = listOf(
        TransactionKind.INVESTMENT,
        TransactionKind.LOAN_OUT,
        TransactionKind.LOAN_IN
    )
    val isSecondarySelected = selectedKind in secondaryKinds

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        primaryKinds.forEach { kind ->
            val isSelected = selectedKind == kind
            FilterChip(
                selected = isSelected,
                onClick = { onSelectKind(kind) },
                label = {
                    Text(
                        text = stringResource(kind.transactionKindLabelRes()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderWidth = 0.dp,
                    borderColor = Color.Transparent,
                    selected = isSelected,
                    enabled = true
                )
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            val moreLabel = if (isSecondarySelected) {
                stringResource(selectedKind.transactionKindLabelRes())
            } else {
                stringResource(R.string.type_more)
            }

            FilterChip(
                selected = isSecondarySelected,
                onClick = { menuExpanded = true },
                label = {
                    Text(
                        text = moreLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderWidth = 0.dp,
                    borderColor = Color.Transparent,
                    selected = isSecondarySelected,
                    enabled = true
                )
            )

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp)
            ) {
                secondaryKinds.forEach { kind ->
                    val isKindSelected = selectedKind == kind
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(kind.transactionKindLabelRes()),
                                fontWeight = if (isKindSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (kind) {
                                    TransactionKind.INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
                                    TransactionKind.LOAN_OUT -> Icons.AutoMirrored.Filled.TrendingDown
                                    TransactionKind.LOAN_IN -> Icons.AutoMirrored.Filled.TrendingUp
                                    else -> Icons.Rounded.SwapHoriz
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isKindSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = if (isKindSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            menuExpanded = false
                            onSelectKind(kind)
                        }
                    )
                }
            }
        }
    }
}
