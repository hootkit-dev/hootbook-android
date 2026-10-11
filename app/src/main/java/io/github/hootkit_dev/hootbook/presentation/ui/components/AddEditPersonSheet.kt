@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import dev.hootkit.core.Person
import dev.hootkit.core.PersonRelation
import io.github.hootkit_dev.hootbook.presentation.ui.components.ColorPickerContent
import io.github.hootkit_dev.hootbook.presentation.ui.features.accounts.NumberPad
import io.github.hootkit_dev.hootbook.presentation.ui.components.PresetAvatarSelection
import io.github.hootkit_dev.hootbook.presentation.ui.icons.CloseCircle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.GalleryExport
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

val AVATAR_COLORS = listOf(
    "#8E81FF", "#2196F3", "#9C27B0", "#FF9800",
    "#E91E63", "#00BCD4", "#3F51B5", "#795548"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddEditPersonSheet(
    personToEdit: Person? = null,
    attachmentStore: CoreAttachmentStore,
    saving: Boolean = false,
    error: Int? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String?, notes: String?, color: String, avatar: String?, category: PersonRelation?) -> Unit
) {
    var name by remember { mutableStateOf(personToEdit?.name ?: "") }
    var phone by remember { mutableStateOf(personToEdit?.phone ?: "") }
    var notes by remember { mutableStateOf(personToEdit?.notes ?: "") }
    var selectedColor by remember { mutableStateOf(personToEdit?.color ?: AVATAR_COLORS.first()) }
    var avatarUri by remember { mutableStateOf(personToEdit?.avatar) }
    var category by remember { mutableStateOf(personToEdit?.relation ?: PersonRelation.OTHER) }
    var nameError by remember { mutableStateOf(false) }

    var showNumberPad by remember { mutableStateOf(false) }
    val numberPadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showNumberPad) {
        HootbookModalBottomSheet(
            onDismissRequest = { showNumberPad = false },
            sheetState = numberPadSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NumberPad(
                initialValue = phone,
                onDone = {
                    phone = it
                    showNumberPad = false
                },
                title = stringResource(R.string.phone_number_optional),
                doneButtonLabel = stringResource(R.string.done)
            )
        }
    }

    val platformContext = LocalContext.current.applicationContext
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var importError by remember { mutableStateOf<Int?>(null) }
    var importing by remember { mutableStateOf(false) }
    fun importAvatar(uri: Uri) {
        if (importing) return
        importing = true
        scope.launch {
            try {
                avatarUri = if (uri.scheme == "android.resource") {
                    platformContext.contentResolver.openInputStream(uri)?.use { attachmentStore.importStream(it, "png") }
                } else attachmentStore.importFrom(uri)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { importError = coreFailureText(failure) }
            finally { importing = false }
        }
    }

    // Persist the newly picked image into internal storage so the stored value is a
    // stable file:// Uri that survives app updates and reinstalls.
    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                importAvatar(uri)
            }
        }

    HootbookModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = if (personToEdit == null) stringResource(R.string.add_person) else stringResource(
                        R.string.edit_person
                    ),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    fontWeight = FontWeight.Bold
                )

                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                importError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                // Avatar preview
                val colorInt = try {
                    selectedColor.toColorInt()
                } catch (e: Exception) {
                    "#ff818eff".toColorInt()
                }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .shadow(2.dp,CircleShape)
                        .clip(CircleShape)
                        .background(Color(colorInt)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = name.take(1).uppercase().ifBlank { "?" }
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri?.let(attachmentStore::resolve),
                            contentDescription = stringResource(R.string.person_avatar),
                            modifier = Modifier.size(96.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                Column(
                    verticalArrangement = Arrangement.spacedBy(1.5.dp),
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                ) {
                    PersonTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) nameError = false
                        },
                        label = stringResource(R.string.person_name),
                        isError = nameError,
                        isTop = true
                    )

                    PersonTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = stringResource(R.string.phone_number_optional),
                        isMiddle = true,
                        modifier = Modifier.clickable { showNumberPad = true }
                    )

                    PersonTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = stringResource(R.string.notes_optional_ledger),
                        isBottom = true
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(horizontal = Dimensions.Padding.content)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PersonRelation.entries.filter { it != PersonRelation.UNKNOWN }.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = false,
                                selected = category == option,
                            ),
                            label = { Text(categoryLabel(option)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))


                PresetAvatarSelection(
                    selectedUri = avatarUri?.let { Uri.parse(it) },
                    onSelect = {
                        importAvatar(it)
                    }
                )

                Row(
                    modifier = Modifier
                        .padding(Dimensions.Padding.content)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Icon(Iconax.GalleryExport, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.gallery))
                    }
                    Button(
                        onClick = {
                            avatarUri = null
                        },
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Icon(Iconax.CloseCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.clear))
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                ColorPickerContent(
                    initialColor = colorInt,
                    onColorChanged = { colorRgb ->
                        selectedColor = String.format("#%06X", 0xFFFFFF and colorRgb)
                    }
                )
                Spacer(modifier = Modifier.height(80.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    enabled = !saving && !importing,
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                        } else {
                            onSave(name, phone, notes, selectedColor, avatarUri, category)
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = Dimensions.Padding.content)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(56.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: PersonRelation?): String = when (category) {
    PersonRelation.FRIEND -> stringResource(R.string.category_friends)
    PersonRelation.FAMILY -> stringResource(R.string.category_family)
    PersonRelation.COLLEAGUE -> stringResource(R.string.category_colleagues)
    PersonRelation.OTHER, PersonRelation.UNKNOWN, null -> stringResource(R.string.category_other)
}

@Composable
private fun PersonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    isError: Boolean = false,
    isTop: Boolean = false,
    isBottom: Boolean = false,
    isMiddle: Boolean = false,
    readOnly: Boolean = false,
) {
    val shape = when {
        isTop -> RoundedCornerShape(
            topStart = Dimensions.Radius.md, topEnd = Dimensions.Radius.md,
            bottomStart = Dimensions.Radius.xs, bottomEnd = Dimensions.Radius.xs
        )
        isBottom -> RoundedCornerShape(
            topStart = Dimensions.Radius.xs, topEnd = Dimensions.Radius.xs,
            bottomStart = Dimensions.Radius.md, bottomEnd = Dimensions.Radius.md
        )
        isMiddle -> RoundedCornerShape(Dimensions.Radius.xs)
        else -> RoundedCornerShape(Dimensions.Radius.md)
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        singleLine = singleLine,
        maxLines = maxLines,
        isError = isError,
        readOnly = readOnly,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    )
}
