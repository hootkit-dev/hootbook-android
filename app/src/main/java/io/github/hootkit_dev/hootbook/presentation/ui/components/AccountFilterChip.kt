package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hootkit.core.Account
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.remember
import io.github.hootkit_dev.hootbook.presentation.common.icons.IconProvider

@Composable
fun AccountFilterChip(
    account: Account,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

            TiledScrollingIconBackground(
                iconResource = iconResource,
                opacity = 0.05f,
                iconSize = 48.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandIcon(
                    merchantName = account.name,
                    size = 48.dp,
                    showBackground = true,
                    accountIconResId = 0,
                    accountIconName = account.icon,
                    accountColorHex = account.color
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = accountSubtitle(account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                    )
                }
            }
            if (isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp).fillMaxSize()
                    )
                }
            }
        }
    }
}
