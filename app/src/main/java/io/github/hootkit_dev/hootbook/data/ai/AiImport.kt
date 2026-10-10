package io.github.hootkit_dev.hootbook.data.ai

import io.github.hootkit_dev.hootbook.R

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A file the user shared, copied out of the sharing app while access lasts. */
data class AiAttachment(val name: String, val mimeType: String, val bytes: ByteArray) {
    val isPdf get() = mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
    val isImage get() = mimeType.startsWith("image/")
    /** Spreadsheets, documents and archives: binary formats the model cannot be handed as text. */
    val isUnsupported get() = TEXT_EXTENSIONS.none { name.endsWith(".$it", ignoreCase = true) } && (
        mimeType.startsWith("application/vnd.") || mimeType == "application/msword" ||
        mimeType == "application/zip" ||
        UNSUPPORTED_EXTENSIONS.any { name.endsWith(".$it", ignoreCase = true) }
    )

    private companion object {
        // Some phones label CSV files as Excel
        val TEXT_EXTENSIONS = listOf("csv", "tsv", "txt", "json")
        val UNSUPPORTED_EXTENSIONS = listOf("xlsx", "xls", "docx", "doc", "numbers", "pages", "key", "zip", "rar", "7z")
    }
}

class PdfPasswordRequired(val name: String) : Exception("$name is password-protected")

/** Reads shared files and turns them into what the model reads. */
class AiAttachmentReader @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun copy(uris: List<Uri>): List<AiAttachment> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment ?: "file"
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_FILE_BYTES) throw AiException("$name is larger than 20 MB", R.string.ai_err_too_large, listOf(name))
                }
                out.toByteArray()
            } ?: return@mapNotNull null
            AiAttachment(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
        }
    }

    /** The attachments as model input. [passwords] unlock PDFs by name. */
    suspend fun parts(attachments: List<AiAttachment>, passwords: Map<String, String>): List<AiPart> =
        withContext(Dispatchers.Default) {
            attachments.flatMap { a ->
                when {
                    a.isUnsupported -> throw AiException(
                        "${a.name} cannot be read", R.string.ai_err_unsupported, listOf(a.name)
                    )
                    a.isPdf -> listOf(pdf(a, passwords[a.name]))
                    a.isImage -> image(a)
                    else -> listOf(AiPart.Text("File ${a.name}:\n" + decodeText(a.bytes)))
                }
            }
        }

    private fun pdf(a: AiAttachment, password: String?): AiPart.Pdf {
        PDFBoxResourceLoader.init(context)
        val document = try {
            PDDocument.load(a.bytes, password.orEmpty())
        } catch (e: InvalidPasswordException) {
            throw PdfPasswordRequired(a.name)
        }
        return document.use { doc ->
            val text = PDFTextStripper().getText(doc).trim()
            // The model cannot open an encrypted PDF: hand it an unlocked copy
            val bytes = if (doc.isEncrypted) {
                doc.isAllSecurityToBeRemoved = true
                ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
            } else a.bytes
            AiPart.Pdf(name = a.name, base64 = Base64.encodeToString(bytes, Base64.NO_WRAP), text = text)
        }
    }

    /**
     * Images at most [MAX_IMAGE_WIDTH] wide; a long screenshot is cut into overlapping tiles,
     * since a model shrinks a very tall image until its text is unreadable.
     */
    private fun image(a: AiAttachment): List<AiPart> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(a.bytes, 0, a.bytes.size, bounds)
        if (bounds.outWidth <= 0) throw AiException("${a.name} is not a readable image", R.string.ai_err_bad_image, listOf(a.name))
        val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(a.bytes, 0, a.bytes.size)
        } else {
            @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(a.bytes, 0, a.bytes.size, false)
        } ?: throw AiException("${a.name} is not a readable image", R.string.ai_err_bad_image, listOf(a.name))
        try {
            val width = bounds.outWidth
            val tileHeight = width * 2
            val overlap = width / 8
            val tops = generateSequence(0) { it + tileHeight - overlap }
                .takeWhile { it == 0 || it < bounds.outHeight - overlap }
                .take(MAX_TILES + 1).toList()
            if (tops.size > MAX_TILES) throw AiException("${a.name} is too long; share it in parts", R.string.ai_err_too_long, listOf(a.name))
            return tops.map { top ->
                val rect = Rect(0, top, width, minOf(bounds.outHeight, top + tileHeight))
                val options = BitmapFactory.Options().apply {
                    inSampleSize = generateSequence(1) { it * 2 }.first { width / (it * 2) < MAX_IMAGE_WIDTH }
                }
                val tile = decoder.decodeRegion(rect, options)
                val scaled = if (tile.width > MAX_IMAGE_WIDTH) {
                    Bitmap.createScaledBitmap(tile, MAX_IMAGE_WIDTH, tile.height * MAX_IMAGE_WIDTH / tile.width, true)
                        .also { tile.recycle() }
                } else tile
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                scaled.recycle()
                AiPart.Image("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            }
        } finally {
            decoder.recycle()
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        const val MAX_IMAGE_WIDTH = 1200
        const val MAX_TILES = 12
    }
}

/** UTF-8 when the bytes are valid UTF-8, otherwise GB18030 (what Alipay and many bank exports use). */
internal fun decodeText(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
} catch (e: CharacterCodingException) {
    String(bytes, Charset.forName("GB18030"))
}.removePrefix("\uFEFF")

/** What a session has done so far, shown while it runs. Raw texts are kept for the detail view. */
sealed interface AiStep {
    /** The opening request: the instructions and the user's message, files shown as placeholders. */
    data class Sent(val system: String, val message: String) : AiStep
    /** The request went to the model; [turn] counts from 1. Finished once its [Replied] follows. */
    data class Asking(val turn: Int) : AiStep
    data class Replied(
        val turn: Int,
        val millis: Long,
        val text: String,
        val thinking: String,
        val calls: List<AiToolCall>,
        val inputTokens: Int?,
        val outputTokens: Int?
    ) : AiStep
    /** The model looked up recorded transactions; [found] is null when the lookup failed. */
    data class Searched(
        val from: String?,
        val to: String?,
        val text: String?,
        val found: Int?,
        val input: JsonObject,
        val result: String
    ) : AiStep
    /** A write tool queued [changes]; [rejected] when it refused some of its input. */
    data class Proposed(
        val tool: String,
        val changes: List<LedgerChange>,
        val rejected: Boolean,
        val input: JsonObject,
        val result: String
    ) : AiStep
}

/** The result of one AI session: the proposed changes and the model's closing note. */
data class AiProposal(
    val changes: List<LedgerChange>,
    val summary: String,
    // The model was still at work when the turn limit stopped it: the list may be incomplete
    val stoppedAtLimit: Boolean = false
)

/** Runs the model over the user's files and request until it has proposed everything it means to. */
class AiLedgerSession @Inject constructor(
    private val chat: AiChat,
    private val settings: AiSettings,
    private val tools: LedgerTools
) {
    suspend fun run(parts: List<AiPart>, request: String, onStep: (AiStep) -> Unit = {}): AiProposal {
        val config = settings.config.value
        if (!config.isConfigured) throw AiException("Set up an AI provider first.", R.string.ai_err_not_configured)
        val context = tools.context()
        val conversation = AiConversation(config, systemPrompt(tools.describe(context)), tools.tools)
        val ask = request.trim().ifEmpty { DEFAULT_REQUEST }
        chat.addUser(conversation, parts + AiPart.Text(ask))

        onStep(AiStep.Sent(conversation.system, describe(parts, ask)))

        val queue = mutableListOf<LedgerChange>()
        repeat(MAX_TURNS) { turn ->
            onStep(AiStep.Asking(turn + 1))
            val started = SystemClock.elapsedRealtime()
            val reply = chat.send(conversation)
            onStep(
                AiStep.Replied(
                    turn + 1, SystemClock.elapsedRealtime() - started, reply.text.trim(), reply.thinking.trim(),
                    reply.calls, reply.inputTokens, reply.outputTokens
                )
            )
            if (reply.calls.isEmpty()) {
                if (reply.truncated) throw AiException("The answer was cut off. Try fewer pages at a time.", R.string.ai_err_truncated)
                return AiProposal(queue.toList(), reply.text.trim())
            }
            val finish = reply.calls.firstOrNull { it.name == LedgerTools.FINISH }
            val results = reply.calls.filter { it.name != LedgerTools.FINISH }.map { call ->
                val queuedBefore = queue.size
                tools.run(call, context, queue).also { result ->
                    onStep(step(call, result, queue.subList(queuedBefore, queue.size).toList()))
                }
            }
            val failed = results.any { it.isError || "Rejected:" in it.content }
            // Proposals and the summary came in one reply: no need to ask again
            if (finish != null && !failed) {
                return AiProposal(queue.toList(), finish.input.text("summary") ?: reply.text.trim())
            }
            val finishResult = finish?.let {
                AiToolResult(it.id, "Not finished: fix the rejected calls above, then call finish again.", isError = true)
            }
            chat.addToolResults(conversation, results + listOfNotNull(finishResult))
        }
        return AiProposal(queue.toList(), "", stoppedAtLimit = true)
    }

    private fun step(call: AiToolCall, result: AiToolResult, queued: List<LedgerChange>): AiStep =
        if (call.name == LedgerTools.FIND) {
            val found = if (result.isError) null
            else runCatching { Json.parseToJsonElement(result.content).jsonObject["count"]?.jsonPrimitive?.intOrNull }.getOrNull()
            AiStep.Searched(
                call.input.text("from_date"), call.input.text("to_date"), call.input.text("text"), found,
                call.input, result.content
            )
        } else {
            AiStep.Proposed(call.name, queued, result.isError || "Rejected:" in result.content, call.input, result.content)
        }

    /** The user's message as text, each file a placeholder: the detail view shows no base64. */
    private fun describe(parts: List<AiPart>, request: String): String {
        var images = 0
        return (parts.map { part ->
            when (part) {
                is AiPart.Text -> part.text
                is AiPart.Image -> "[image ${++images}]"
                is AiPart.Pdf -> "[PDF ${part.name}, ${part.text.length} characters of text]"
            }
        } + request).joinToString("\n\n")
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun systemPrompt(ledger: String) = """
        You keep the user's personal ledger in Hootbook, a bookkeeping app. Today is ${LocalDate.now()}.
        The user gives you screenshots, statements or other files, or just asks for a change. Record what
        they ask using the tools. Every write you make is only a proposal: the user reviews each one
        before anything is saved, so propose everything that applies rather than asking for confirmation.

        When reading a statement or screenshot:
        - Add each real transaction once. You need not look up what is already recorded: the app compares
          each proposal with the ledger and flags likely duplicates for the user.
        - Leave out balances, subtotals, rejected or pending lines and anything that is not a transaction.
        - Card purchases are EXPENSE on the card's account and refunds are INCOME on it; a payment from
          a bank account to a card is a TRANSFER between them.
        - Pick the account the document belongs to (card or account number, bank name). When it belongs
          to a card or account that is not listed, propose it with create_account first and use the
          ref that returns; leave account out only when the document does not say whose it is.
        - Use only the categories listed below.
        - Name the merchant as the user knows it: the shop or brand shown most prominently ("大八屋顶牛排馆",
          "星巴克", "中国联通"), not the registered company ("上海博明餐饮管理有限公司…") or the payment
          processor ("财付通"). Keep the document's language.
        - Always fill notes so the user can tell later what the money was for: what was bought or the
          service ("话费代扣", "外卖 2 份"), the platform it went through (美团, 微信支付, 支付宝) and, when
          shown, the card used (e.g. "AMEX 1008"). The registered company name may go here too.

        When the document shows an account's current balance (or a card's amount owed or credit limit) and it
        differs from the balance listed below, propose set_balance. Rename or re-type accounts with
        update_account only when the user asks.

        Work in as few replies as you can: put every tool call into one reply unless one needs another's
        result, and call finish in that same reply with your summary. Use find_transactions only to find
        transactions to change or delete, or to answer a question about them.

        $ledger
    """.trimIndent()

    companion object {
        const val MAX_TURNS = 16
        private const val DEFAULT_REQUEST = "Record the transactions in these files."
    }
}
