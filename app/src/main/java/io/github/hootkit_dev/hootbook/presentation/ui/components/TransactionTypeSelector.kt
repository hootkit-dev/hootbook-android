package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hootkit.core.TransactionKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.format.transactionKindFullLabelRes
import io.github.hootkit_dev.hootbook.presentation.format.transactionKindLabelRes

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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        primaryKinds.forEach { kind ->
            val isSelected = selectedKind == kind
            Surface(
                onClick = { onSelectKind(kind) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(40.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                ) {
                    BasicText(
                        text = stringResource(kind.transactionKindLabelRes()),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 11.sp,
                            maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                            stepSize = 0.5.sp
                        )
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            val moreLabel = if (isSecondarySelected) {
                stringResource(selectedKind.transactionKindLabelRes())
            } else {
                stringResource(R.string.type_more)
            }

            Surface(
                onClick = { menuExpanded = true },
                shape = RoundedCornerShape(12.dp),
                color = if (isSecondarySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (isSecondarySelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .height(40.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSecondarySelected
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = moreLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSecondarySelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSecondarySelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp,
                            maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                            stepSize = 0.5.sp
                        ),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSecondarySelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
                                text = stringResource(kind.transactionKindFullLabelRes()),
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
