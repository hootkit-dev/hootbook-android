package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility

@Composable
fun PreferenceSwitch(
    visible: Boolean = true,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: @Composable () -> Unit = {},
    padding: PaddingValues = listItemPadding,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    isSingle: Boolean = false
) {
    BlurredAnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        ListItem(
            headline = { Text(title) },
            supporting = { if (subtitle.isNotEmpty())Text(subtitle) },
            leading = {
                leadingIcon()
            },
            trailing = { HootbookSwitch(checked = checked, onCheckedChange = null) },
            toggled = checked,
            onToggle = onCheckedChange,
            shape = when {
                isSingle -> ListItemPosition.Single.toShape()
                isFirst -> ListItemPosition.Top.toShape()
                isLast -> ListItemPosition.Bottom.toShape()
                else -> ListItemPosition.Middle.toShape()
            },
            padding = padding
        )
    }
}