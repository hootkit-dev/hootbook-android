package io.github.hootkit_dev.hootbook.presentation.ui.components
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions

@Composable
fun CurrencyCard(
    code: String,
    symbol: String,
    name: String,
    isSelected: Boolean = false,
    // Width: a grid cell's weight, or a fixed width in a row
    modifier: Modifier = Modifier.width(96.dp),
    onCurrencyCardClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(Dimensions.Radius.md))
            .clickable(onClick = onCurrencyCardClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.inverseSurface
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = code.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                else
                    MaterialTheme.colorScheme.inverseSurface.copy(0.5f)
            )
            Text(
                text = symbol,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.inverseSurface
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                else
                    MaterialTheme.colorScheme.inverseSurface.copy(0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = MarqueeAnimationMode.Immediately,
                        initialDelayMillis = 1000,
                        velocity = 30.dp
                    )
            )
        }
    }
}
