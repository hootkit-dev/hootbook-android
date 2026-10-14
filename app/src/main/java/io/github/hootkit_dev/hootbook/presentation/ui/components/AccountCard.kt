package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.presentation.ui.theme.AccountCardElevation
import io.github.hootkit_dev.hootbook.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hootkit.core.Account
import dev.hootkit.core.AccountKind
import io.github.hootkit_dev.hootbook.presentation.common.icons.IconProvider
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Bag
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Balance
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Edit2
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Eye
import io.github.hootkit_dev.hootbook.presentation.ui.icons.EyeSlash
import io.github.hootkit_dev.hootbook.presentation.ui.icons.HierarchySquare3
import io.github.hootkit_dev.hootbook.presentation.ui.icons.History
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import io.github.hootkit_dev.hootbook.presentation.format.CoreMoneyPresentation

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountCard(
    account: Account,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onBalanceClick: (() -> Unit)? = null,
    content: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AccountCardElevation)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val context = LocalContext.current
            val iconResource = remember(account.name, 0, account.icon) {
                IconProvider.getIconForTransaction(
                    context = context,
                    merchantName = account.name,
                    accountIconResId = 0,
                    accountIconName = account.icon
                )
            }


            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top & Balance Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onBalanceClick != null) {
                                Modifier.clickable(onClick = onBalanceClick)
                            } else {
                                Modifier
                            }
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(if ((account.kind == AccountKind.CREDIT_CARD)) R.string.outstanding_label else R.string.balance_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = accountBalanceText(account),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (onBalanceClick != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Iconax.Edit2,
                                        contentDescription = stringResource(if (account.kind == AccountKind.CREDIT_CARD) R.string.update_outstanding_title else R.string.account_calibrate),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                AccountIncompleteIndicator(account)
                AccountCurrencyPills(account, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                // Bottom Section (Bank Info)
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = accountSubtitle(account),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {

                                BrandIcon(
                                    merchantName = account.name,
                                    size = 48.dp,
                                    showBackground = true,
                                    accountIconResId = 0,
                                    accountIconName = account.icon,
                                    accountColorHex = account.color
                                )
                            }
                        }

                    }
                    // Extra content (e.g., Credit Card stats, Linked Cards)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}