@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.ui.components.*

/** The original link form's person carousel. Core owns amount, kind, date and title on linking. */
@Composable
internal fun LoanPersonSheet(
    people: List<Person>, attachmentStore: CoreAttachmentStore, busy: Boolean,
    onAddPerson: (String, String?, String?, String, String?, PersonRelation?) -> Unit,
    onDismiss: () -> Unit, onSelect: (Person) -> Unit,
) {
    var selectedId by remember { mutableStateOf(people.firstOrNull()?.id) }
    var addPerson by remember { mutableStateOf(false) }
    HootbookModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.add_record), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
            PersonCarousel(people, selectedId, attachmentStore, { selectedId = it }, { addPerson = true })
            Button(onClick = { people.find { it.id == selectedId }?.let(onSelect) },
                enabled = !busy && people.any { it.id == selectedId }, shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text(stringResource(R.string.save)) }
        }
    }
    if (addPerson) AddEditPersonSheet(attachmentStore = attachmentStore, onDismiss = { addPerson = false }, onSave = { name, phone, notes, color, avatar, relation ->
        onAddPerson(name, phone, notes, color, avatar, relation)
        addPerson = false
    })
}
