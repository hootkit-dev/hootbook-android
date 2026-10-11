package io.github.hootkit_dev.hootbook.presentation.core.common

import androidx.compose.foundation.Image
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
    val resource = remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
    if (resource != 0) Image(painterResource(resource), contentDescription = null, modifier = modifier)
    else Text(name.ifBlank { "◇" }, modifier)
}

@Composable
fun CoreVisualStylePicker(icon: String, color: String, enabled: Boolean, onIcon: (String) -> Unit, onColor: (String) -> Unit) {
    var showIcons by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CoreRecordIcon(icon, Modifier.size(40.dp))
        TextButton(enabled = enabled, onClick = { showIcons = true }) { Text(stringResource(R.string.core_visual_icon)) }
        TextButton(enabled = enabled, onClick = { showColors = true }) { Text(stringResource(R.string.core_visual_color)) }
    }
    if (showIcons) AlertDialog(onDismissRequest = { showIcons = false }, title = { Text(stringResource(R.string.core_visual_icon)) },
        text = { Box(Modifier.heightIn(max = 520.dp)) { IconSelector(selectedIconName = icon, onIconSelected = { onIcon(it); showIcons = false }) } },
        confirmButton = { TextButton(onClick = { showIcons = false }) { Text(stringResource(R.string.core_attachment_close)) } })
    if (showColors) AlertDialog(onDismissRequest = { showColors = false }, title = { Text(stringResource(R.string.core_visual_color)) },
        text = { ColorPickerContent(initialColor = runCatching { android.graphics.Color.parseColor(color) }.getOrDefault(android.graphics.Color.BLUE),
            onColorChanged = { onColor("#%06X".format(it and 0xFFFFFF)) }) },
        confirmButton = { TextButton(onClick = { showColors = false }) { Text(stringResource(R.string.core_attachment_close)) } })
}
