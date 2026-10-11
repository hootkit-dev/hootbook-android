package io.github.hootkit_dev.hootbook.core.backup

import android.util.AtomicFile
import dev.hootkit.core.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class BackupPhase { PREPARED, IMPORTING, CORE_COMMITTED, PLATFORM_APPLIED, PLATFORM_SKIPPED, UNCERTAIN, FAILED }
data class BackupOperation(
    val source: StagedBackup,
    val legacy: Boolean,
    val phase: BackupPhase,
    val importSummary: ImportSummary? = null,
    val legacySummary: LegacyImportSummary? = null,
) {
    val files: List<String> get() = importSummary?.platformFiles ?: legacySummary?.platformFiles.orEmpty()
    val preferences: String? get() = importSummary?.preferencesJson ?: legacySummary?.preferencesJson
    val blocksImport: Boolean get() = phase in setOf(BackupPhase.IMPORTING, BackupPhase.CORE_COMMITTED, BackupPhase.UNCERTAIN)
}

/** No passwords, keys, URI permissions or raw exception details are written to the journal. */
class BackupOperationJournal(private val files: CoreBackupFiles) {
    fun write(operation: BackupOperation) = writeFile(operation, "journal.json")
    internal fun writePreview(operation: BackupOperation) = writeFile(operation, "preview-journal.json")
    internal fun readPreview(id: String): BackupOperation? = readFile(id, "preview-journal.json")
    private fun writeFile(operation: BackupOperation, name: String) {
        val json = JSONObject().put("version", 1).put("id", operation.source.operationId)
            .put("source", operation.source.file.name).put("sha256", operation.source.sha256)
            .put("size", operation.source.size).put("legacy", operation.legacy).put("phase", operation.phase.name)
        operation.importSummary?.let { s -> json.put("summary", JSONObject()
            .put("added", s.added.toLong()).put("updated", s.updated.toLong()).put("skipped", s.skipped.toLong())
            .put("preferences", s.preferencesJson ?: JSONObject.NULL).put("files", JSONArray(s.platformFiles))
            .put("ai", s.aiRestored).put("brokerage", s.brokerageAdded.toLong())
            .put("safety", s.safetyBackup ?: JSONObject.NULL)) }
        operation.legacySummary?.let { s -> json.put("legacySummary", JSONObject()
            .put("imported", countJson(s.imported)).put("dropped", countJson(s.dropped))
            .put("currency", s.mainCurrency).put("preferences", s.preferencesJson ?: JSONObject.NULL)
            .put("files", JSONArray(s.platformFiles)).put("renamed", JSONObject(s.renamedCurrencies))) }
        val atomic = AtomicFile(CoreBackupFiles.safeChild(files.operation(operation.source.operationId), name))
        val output = atomic.startWrite()
        try { output.write(json.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (failure: Throwable) { atomic.failWrite(output); throw failure }
    }
    fun read(id: String): BackupOperation? = readFile(id, "journal.json")
    private fun readFile(id: String, name: String): BackupOperation? {
        val atomic = AtomicFile(CoreBackupFiles.safeChild(files.operation(id), name))
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
        val json = atomic.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(json.getInt("version") == 1 && json.getString("id") == id)
        val source = StagedBackup(id, CoreBackupFiles.safeChild(files.operation(id), json.getString("source")),
            json.getString("sha256"), json.getLong("size"))
        val summary = json.optJSONObject("summary")?.let { s -> ImportSummary(
            s.getLong("added").toUInt(), s.getLong("updated").toUInt(), s.getLong("skipped").toUInt(),
            nullable(s, "preferences"), strings(s.getJSONArray("files")), s.getBoolean("ai"),
            s.getLong("brokerage").toUInt(), nullable(s, "safety")) }
        val legacy = json.optJSONObject("legacySummary")?.let { s ->
            val renamed = s.getJSONObject("renamed")
            LegacyImportSummary(counts(s.getJSONArray("imported")), counts(s.getJSONArray("dropped")),
                s.getString("currency"), nullable(s, "preferences"), strings(s.getJSONArray("files")),
                renamed.keys().asSequence().associateWith(renamed::getString))
        }
        return BackupOperation(source, json.getBoolean("legacy"), BackupPhase.valueOf(json.getString("phase")), summary, legacy)
    }
    fun all(root: File): List<BackupOperation> = root.listFiles().orEmpty().filter { it.isDirectory }
        .sortedBy { it.lastModified() }.mapNotNull { read(it.name) }
    private fun countJson(rows: List<LegacyCount>) = JSONArray(rows.map { JSONObject().put("item", it.item).put("count", it.count.toLong()) })
    private fun counts(json: JSONArray) = (0 until json.length()).map { json.getJSONObject(it).let { row -> LegacyCount(row.getString("item"), row.getLong("count").toUInt()) } }
    private fun strings(json: JSONArray) = (0 until json.length()).map(json::getString)
    private fun nullable(json: JSONObject, key: String) = if (json.isNull(key)) null else json.getString(key)
}
