package io.github.hootkit_dev.hootbook.core.attachments

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.hootkit_dev.hootbook.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CoreAttachmentImage(reference: String, store: CoreAttachmentStore, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, reference) { value = decodeAttachmentThumbnail(store.resolve(reference)) }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = modifier) }
        ?: Text(stringResource(R.string.core_attachment_document), modifier)
}

internal suspend fun decodeAttachmentThumbnail(file: File?): Bitmap? = withContext(Dispatchers.IO) {
    if (file == null) return@withContext null
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/** Integrator must add exactly core_attachment_share / core-attachment-share; no broad-root fallback. */
@Composable
fun CoreAttachmentPreview(reference: String, store: CoreAttachmentStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failure by remember(reference) { mutableStateOf(false) }
    var busy by remember(reference) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.core_attachment_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoreAttachmentImage(reference, store, Modifier.fillMaxWidth().heightIn(max = 320.dp))
            Text(reference, style = MaterialTheme.typography.bodySmall)
            if (store.resolve(reference) == null) Text(stringResource(R.string.core_attachment_missing))
            if (failure) Text(stringResource(R.string.core_attachment_action_failed), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Row {
            for (share in listOf(false, true)) TextButton(enabled = !busy && store.resolve(reference) != null, onClick = {
                scope.launch {
                    busy = true; failure = false
                    try {
                        val file = store.copyForSharing(reference)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        check(uri.path?.startsWith("/core_attachment_share/") == true)
                        val mime = when (file.extension.lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "pdf" -> "application/pdf"; "txt" -> "text/plain"; "csv" -> "text/csv"; else -> "application/octet-stream" }
                        val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                            else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.clipData = android.content.ClipData.newRawUri("attachment", uri)
                        context.startActivity(Intent.createChooser(intent, null))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failure = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(if (share) R.string.core_attachment_share else R.string.core_attachment_open)) }
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_attachment_close)) } })
}
