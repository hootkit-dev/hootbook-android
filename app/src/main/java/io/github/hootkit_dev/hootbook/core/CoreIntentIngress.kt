package io.github.hootkit_dev.hootbook.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import io.github.hootkit_dev.hootbook.core.ai.CoreAiInbox
import io.github.hootkit_dev.hootbook.di.ApplicationScope
import io.github.hootkit_dev.hootbook.widget.QuickEntry
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface CoreIngressTarget {
    data object Ai : CoreIngressTarget
    data class Quick(val entry: QuickEntry.Entry) : CoreIngressTarget
}
data class CorePendingEntry(val token: String = UUID.randomUUID().toString(), val target: CoreIngressTarget)
/** App-owned copies outlive temporary sender grants; navigation waits outside the lock gate. */
@Singleton
class CoreIntentIngress @Inject constructor(private val inbox: CoreAiInbox, @ApplicationScope private val scope: CoroutineScope,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) {
    private val queue = MutableStateFlow<List<CorePendingEntry>>(emptyList()); val pending = queue.asStateFlow()
    private val failure = MutableStateFlow(false); val failed = failure.asStateFlow()
    private val file get() = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "hootkit-core/intent-inbox/pending.json"))
    init { try {
        if (file.baseFile.exists() || java.io.File(file.baseFile.path + ".bak").exists()) {
            val array = org.json.JSONArray(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            require(array.length() <= 100)
            queue.value = (0 until array.length()).map { index -> val row = array.getJSONObject(index)
                val token = row.getString("token"); require(java.util.UUID.fromString(token).toString() == token)
                val id = row.optString("id").takeIf { it.isNotBlank() }
                val target = when(row.getString("kind")) {
                    "ai" -> CoreIngressTarget.Ai
                    "home" -> CoreIngressTarget.Quick(QuickEntry.Entry.Home)
                    "transaction" -> CoreIngressTarget.Quick(QuickEntry.Entry.NewTransaction)
                    "transfer" -> CoreIngressTarget.Quick(QuickEntry.Entry.NewTransfer)
                    "new-subscription" -> CoreIngressTarget.Quick(QuickEntry.Entry.NewSubscription)
                    "template" -> CoreIngressTarget.Quick(QuickEntry.Entry.Template(checkNotNull(id)))
                    "subscription" -> CoreIngressTarget.Quick(QuickEntry.Entry.Subscription(checkNotNull(id)))
                    else -> error("Unknown pending entry")
                }; CorePendingEntry(token, target)
            }
        }
    } catch (_: Exception) { failure.value = true } }
    @Synchronized private fun append(entry: CorePendingEntry) { val next = queue.value + entry; require(next.size <= 100) { "Pending entry queue is full; originals retained" }; persist(next); queue.value = next }
    private fun persist(entries: List<CorePendingEntry>) {
        val array = org.json.JSONArray()
        entries.forEach { entry -> val row = org.json.JSONObject().put("token", entry.token)
            when(val target = entry.target) {
                CoreIngressTarget.Ai -> row.put("kind", "ai")
                is CoreIngressTarget.Quick -> when(val quick = target.entry) {
                    QuickEntry.Entry.Home -> row.put("kind", "home")
                    QuickEntry.Entry.NewTransaction -> row.put("kind", "transaction")
                    QuickEntry.Entry.NewTransfer -> row.put("kind", "transfer")
                    QuickEntry.Entry.NewSubscription -> row.put("kind", "new-subscription")
                    is QuickEntry.Entry.Template -> row.put("kind", "template").put("id", quick.id)
                    is QuickEntry.Entry.Subscription -> row.put("kind", "subscription").put("id", quick.id)
                }
            }; array.put(row)
        }
        check(file.baseFile.parentFile!!.mkdirs() || file.baseFile.parentFile!!.isDirectory)
        val stream = file.startWrite(); try { stream.write(array.toString().toByteArray()); file.finishWrite(stream) } catch (failure: Throwable) { file.failWrite(stream); throw failure }
    }
    fun accept(context: Context, intent: Intent?) {
        if (intent == null || intent.action == Intent.ACTION_MAIN) return
        try { require(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) == null)
            val clip = intent.clipData; if (clip != null) require((0 until clip.itemCount).none { clip.getItemAt(it).intent != null })
        } catch (_: Exception) { failure.value = true; return }
        val quick = QuickEntry.parse(intent)
        if (quick != null) { try { append(CorePendingEntry(target = CoreIngressTarget.Quick(quick))) } catch (_: Exception) { failure.value = true }; return }
        if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        // Never inspect/start nested Intents, arbitrary component extras or legacy Long identifiers.
        try {
            val mime = intent.type.orEmpty()
            require(mime.startsWith("image/") || mime == "application/pdf" || mime.startsWith("text/"))
            val uris = if (intent.action == Intent.ACTION_SEND_MULTIPLE)
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            val clipped = intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
            val documents = (uris + clipped).distinct().also { require(it.size <= 32); it.forEach { uri -> CoreUriBoundary.requireExternalDocument(context, uri) } }
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.also { require(it.toByteArray().size <= CoreAiInbox.MAX_FILE_BYTES) }
            if (documents.isEmpty() && text.isNullOrBlank()) return
            // Copy first while the original Activity still owns grants. Only typed entries survive.
            scope.launch {
                try { if (documents.isNotEmpty()) inbox.copy(documents); if (!text.isNullOrBlank()) inbox.addSharedText(text)
                    append(CorePendingEntry(target = CoreIngressTarget.Ai))
                } catch (_: Exception) { failure.value = true }
            }
        } catch (_: Exception) { failure.value = true }
    }
    @Synchronized fun consumed(token: String) { val next = queue.value.filterNot { it.token == token }; persist(next); queue.value = next }
    fun dismissFailure() { failure.value = false }
}
