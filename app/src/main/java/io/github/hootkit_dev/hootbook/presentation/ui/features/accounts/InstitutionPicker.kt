@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.accounts

import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.common.icons.Institution
import io.github.hootkit_dev.hootbook.presentation.common.icons.InstitutionCatalog

/** A dedicated account picker; custom names and the general icon picker remain available. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionPickerButton(
    // Only brokers, for the Broker account type
    brokersOnly: Boolean = false,
    onSelected: (Institution, String) -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val language = LocalConfiguration.current.locales[0].language
    val title = if (brokersOnly) R.string.institution_choose_broker else R.string.institution_choose
    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { visible = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (brokersOnly) Icons.AutoMirrored.Rounded.ShowChart else Icons.Rounded.AccountBalance,
            contentDescription = null, modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(title))
    }
    if (visible) {
        var query by rememberSaveable { mutableStateOf("") }
        var region by rememberSaveable { mutableStateOf<String?>(null) }
        val results = remember(query, region, brokersOnly) { InstitutionCatalog.search(query, region, brokersOnly) }
        HootbookModalBottomSheet(
            onDismissRequest = { visible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() }
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.institution_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        null to R.string.institution_region_all,
                        "CN" to R.string.institution_region_cn,
                        "HK" to R.string.institution_region_hk,
                        "SG" to R.string.institution_region_sg,
                        "US" to R.string.institution_region_us
                    ).forEach { (code, label) ->
                        FilterChip(selected = region == code, onClick = { region = code },
                            label = { Text(stringResource(label)) })
                    }
                }
                if (results.isEmpty()) {
                    Text(stringResource(R.string.institution_no_results),
                        modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(results, key = { it.id }) { institution ->
                        ListItem(
                            headlineContent = { Text(institution.displayName(language)) },
                            supportingContent = {
                                Text(if (language == "zh") institution.englishName else institution.chineseName)
                            },
                            leadingContent = {
                                Image(painterResource(institution.iconResId), contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(4.dp))
                            },
                            trailingContent = { Text("${institution.region} · ${institution.currency}",
                                style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.clickable(role = Role.Button) {
                                onSelected(institution, institution.displayName(language))
                                visible = false
                            }
                        )
                    }
                }
            }
        }
    }
}
