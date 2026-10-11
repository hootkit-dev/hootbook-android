package io.github.hootkit_dev.hootbook.presentation.core.common

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
internal fun CoreTextField(value: String, label: String, saving: Boolean, changed: (String) -> Unit) {
    OutlinedTextField(value, changed, label = { Text(label) }, enabled = !saving, modifier = Modifier.fillMaxWidth(), singleLine = true)
}
