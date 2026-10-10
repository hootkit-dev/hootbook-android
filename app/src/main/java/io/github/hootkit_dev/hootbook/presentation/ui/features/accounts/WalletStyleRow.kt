package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Wallet-inspired information row; native theme roles, adaptive text, no image imitation. */
@Composable
internal fun WalletStyleRow(
    title: String, amount: String, subtitle: String,
    icon: @Composable () -> Unit, modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier) {
        val stack = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f || amount.length > 17
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(start = 16.dp, end = if (trailing == null) 16.dp else 0.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (stack) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.titleMedium)
                        Text(amount, Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) trailing()
        }
    }
}
