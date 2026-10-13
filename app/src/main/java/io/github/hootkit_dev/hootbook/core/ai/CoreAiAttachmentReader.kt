package io.github.hootkit_dev.hootbook.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.AiAttachment
import dev.hootkit.core.AiAttachmentKind
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class CoreAiPdfPasswordRequired(val fileId: String, val fileName: String) : Exception("PDF password required")
/** Pure file preparation only. Core performs protocol requests, text decoding, and all tools. */
class CoreAiAttachmentReader @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun prepare(files: List<CoreAiInboxFile>, inbox: CoreAiInboxPort, passwords: Map<String, String>): List<AiAttachment> =
        withContext(Dispatchers.Default) {
            require(files.sumOf { it.size } <= CoreAiInbox.MAX_BATCH_BYTES) { "AI input batch exceeds 20 MB" }
            var preparedBytes = 0L
            files.flatMap { file ->
                ensureActive()
                val data = inbox.bytes(file.id)
                check(data.size <= CoreAiInbox.MAX_FILE_BYTES) { "File is larger than 20 MB" }
                val prepared = when (kind(file.name, file.mimeType)) {
                    AiAttachmentKind.TEXT -> listOf(AiAttachment(AiAttachmentKind.TEXT, file.name, file.mimeType, data, null))
                    AiAttachmentKind.PDF -> listOf(pdf(file, data, passwords[file.id]))
                    AiAttachmentKind.IMAGE -> image(file, data)
                }
                preparedBytes += prepared.sumOf { it.data.size.toLong() + it.pdfText.orEmpty().toByteArray().size }
                require(preparedBytes <= CoreAiInbox.MAX_BATCH_BYTES) { "Prepared AI batch exceeds 20 MB; send fewer pages" }
                prepared
            }
        }
    private fun pdf(file: CoreAiInboxFile, data: ByteArray, password: String?): AiAttachment {
        PDFBoxResourceLoader.init(context)
        val document = try { PDDocument.load(data, password.orEmpty()) }
            catch (_: InvalidPasswordException) { throw CoreAiPdfPasswordRequired(file.id, file.name) }
        return document.use { doc ->
            val text = PDFTextStripper().getText(doc).trim()
            val unlocked = if (doc.isEncrypted) { doc.isAllSecurityToBeRemoved = true
                ByteArrayOutputStream().also { doc.save(it) }.toByteArray() } else data
            AiAttachment(AiAttachmentKind.PDF, file.name, "application/pdf", unlocked, text)
        }
    }
    private fun image(file: CoreAiInboxFile, data: ByteArray): List<AiAttachment> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val regions = tiles(bounds.outWidth, bounds.outHeight)
        require(regions.all { it.width().toLong() * it.height() <= 16_000_000L }) { "Image tile is too large; resize the screenshot first" }
        val decoder = if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(data, 0, data.size)
            else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(data, 0, data.size, false)
        checkNotNull(decoder) { "Image could not be decoded" }
        var outputBytes = 0L
        try { return regions.mapIndexed { index, rect ->
            val tile = checkNotNull(decoder.decodeRegion(rect, BitmapFactory.Options())) { "Image could not be decoded" }
            try {
                val resized = if (tile.width > 1200) Bitmap.createScaledBitmap(tile, 1200, (tile.height.toLong() * 1200 / tile.width).toInt(), true) else tile
                try { val out = ByteArrayOutputStream(); check(resized.compress(Bitmap.CompressFormat.JPEG, 85, out))
                    val encoded = out.toByteArray(); outputBytes += encoded.size
                    require(outputBytes <= CoreAiInbox.MAX_BATCH_BYTES) { "Prepared screenshots exceed 20 MB; send fewer tiles" }
                    AiAttachment(AiAttachmentKind.IMAGE, "${file.name}-${index + 1}.jpg", "image/jpeg", encoded, null)
                } finally { if (resized !== tile) resized.recycle() }
            } finally { tile.recycle() }
        } } finally { decoder.recycle() }
    }
    companion object {
        internal fun kind(name: String, mime: String): AiAttachmentKind {
            val lower = name.lowercase()
            val text = listOf("csv", "tsv", "txt", "json").any { lower.endsWith(".$it") }
            val unsupported = !text && (mime.startsWith("application/vnd.") || mime in setOf("application/msword", "application/zip") ||
                listOf("xlsx", "xls", "docx", "doc", "numbers", "pages", "key", "zip", "rar", "7z").any { lower.endsWith(".$it") })
            check(!unsupported) { "Export this file as CSV, PDF, or screenshot first" }
            return when { mime == "application/pdf" || lower.endsWith(".pdf") -> AiAttachmentKind.PDF
                mime.startsWith("image/") -> AiAttachmentKind.IMAGE
                else -> AiAttachmentKind.TEXT }
        }
        internal fun tiles(width: Int, height: Int): List<Rect> {
            check(width > 0 && height > 0) { "Image could not be decoded" }
            val tileHeight = width.toLong() * 2; val overlap = width / 8; val step = tileHeight - overlap
            val tops = generateSequence(0L) { it + step }.takeWhile { it == 0L || it < height.toLong() - overlap }.take(13).toList()
            check(tops.size <= 12) { "Screenshot is too long; send it in parts" }
            return tops.map { Rect(0, it.toInt(), width, minOf(height.toLong(), it + tileHeight).toInt()) }
        }
    }
}
