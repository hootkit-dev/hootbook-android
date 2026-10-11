@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hootkit.core.Institution
import dev.hootkit.core.InstitutionKind
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.core.common.CoreRecordIcon
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet

/** Presets from the Core catalog, never a login or balance source. */
@Composable
internal fun InstitutionPicker(institutions: List<Institution>, brokersOnly: Boolean, onSelect: (Institution) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val visible = remember(institutions, brokersOnly, query) { institutions.filter { (!brokersOnly || it.kind == InstitutionKind.BROKER) &&
        (query.isBlank() || it.chineseName.contains(query, true) || it.englishName.contains(query, true) || it.key.contains(query, true)) } }
    HootbookModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 540.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(visible, key = { it.key }) { institution ->
                Surface(onClick = { onSelect(institution) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoreRecordIcon(institution.iconName, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) { Text(if (chinese) institution.chineseName else institution.englishName, style = MaterialTheme.typography.bodyLarge); Text(institution.currency, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
