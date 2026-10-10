@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R

/**
 * Home's header: the avatar (opens Settings), today's date over the user's name, and AI
 * bookkeeping as the one action.
 */
@Composable
fun GreetingCard(
    modifier: Modifier = Modifier,
    userName: String,
    profileImageUri: Uri?,
    profileBackgroundColor: Color,
    onProfileClick: () -> Unit = {},
    onAiClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(profileBackgroundColor)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            // Resolve the bitmap at the avatar's constraints off the UI thread.
            AsyncImage(
                model = profileImageUri ?: R.drawable.avatar_1,
                contentDescription = stringResource(R.string.profile_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Today's date first: the context for whatever gets recorded
        Column(modifier = Modifier.weight(1f)) {
            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val date = remember(locale) {
                java.time.LocalDate.now().format(
                    java.time.format.DateTimeFormatter.ofPattern(if (locale.language == "zh") "M月d日 EEE" else "EEE, MMM d", locale)
                )
            }
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = userName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onAiClick != null) {
            FilledTonalButton(
                onClick = onAiClick,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_ai_button), maxLines = 1)
            }
        }
    }
}
