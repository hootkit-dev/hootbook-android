package io.github.hootkit_dev.hootbook.presentation.core.common

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.presentation.ui.components.IconSelector

/** Reuses only the existing stateless drawable/color UI, not its Entity/Repository/ViewModel. */
@Composable
fun CoreRecordIcon(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resource = remember(context, name) { IconResolutionUtils.nameToResId(context, name) }
    if (resource != 0) Image(painterResource(resource), contentDescription = null, modifier = modifier)
    else Icon(Icons.Default.Category, contentDescription = null, modifier = modifier)
}

@Composable
fun CoreVisualStylePicker(icon: String, color: String, enabled: Boolean, onIcon: (String) -> Unit, onColor: (String) -> Unit) {
    var showIcons by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    val swatch = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color)) }.getOrNull()
    io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection {
        item(headline = { Text(stringResource(R.string.core_visual_icon)) }, trailing = { CoreRecordIcon(icon, Modifier.size(32.dp)) },
            chevron = true, onClick = if (enabled) ({ showIcons = true }) else null)
        item(headline = { Text(stringResource(R.string.core_visual_color)) },
            trailing = { Box(Modifier.size(28.dp).background(swatch ?: MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape)) },
            chevron = true, onClick = if (enabled) ({ showColors = true }) else null)
    }
    if (showIcons) AlertDialog(onDismissRequest = { showIcons = false }, title = { Text(stringResource(R.string.core_visual_icon)) },
        text = { Box(Modifier.heightIn(max = 520.dp)) { IconSelector(selectedIconName = icon, onIconSelected = { onIcon(it); showIcons = false }) } },
        confirmButton = { TextButton(onClick = { showIcons = false }) { Text(stringResource(R.string.close)) } })
    if (showColors) AlertDialog(onDismissRequest = { showColors = false }, title = { Text(stringResource(R.string.core_visual_color)) },
        text = { ColorPickerContent(initialColor = runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(android.graphics.Color.BLUE),
            onColorChanged = { onColor("#%06X".format(it and 0xFFFFFF)) }) },
        confirmButton = { TextButton(onClick = { showColors = false }) { Text(stringResource(R.string.done)) } })
}

/** Just the color: one row with its swatch, opening the color picker. */
@Composable
fun CoreColorPickerRow(color: String, enabled: Boolean, label: String = stringResource(R.string.core_visual_color), onColor: (String) -> Unit) {
    var showColors by remember { mutableStateOf(false) }
    val swatch = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color)) }.getOrNull()
    io.github.hootkit_dev.hootbook.presentation.ui.components.ListSection {
        item(headline = { Text(label) },
            trailing = { Box(Modifier.size(28.dp).background(swatch ?: MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape)) },
            chevron = true, onClick = if (enabled) ({ showColors = true }) else null)
    }
    if (showColors) AlertDialog(onDismissRequest = { showColors = false }, title = { Text(label) },
        text = { ColorPickerContent(initialColor = runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(android.graphics.Color.BLUE),
            onColorChanged = { onColor("#%06X".format(it and 0xFFFFFF)) }) },
        confirmButton = { TextButton(onClick = { showColors = false }) { Text(stringResource(R.string.done)) } })
}
