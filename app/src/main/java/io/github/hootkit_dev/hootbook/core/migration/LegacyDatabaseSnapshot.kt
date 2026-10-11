package io.github.hootkit_dev.hootbook.core.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.hootkit_dev.hootbook.core.backup.CoreBackupFiles
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** One-time format transport. Never opens the source SQLite file, runs Room, or computes money. */
internal class LegacyDatabaseSnapshot(private val context: Context) {
    val source: File get() = context.getDatabasePath("pennywise_database")

    suspend fun capture(directory: File, operationId: String): File = withContext(Dispatchers.IO) {
        require(operationId.matches(Regex("[0-9a-f-]{36}")))
        check(source.isFile) { "旧账本文件不存在；不会创建空旧数据库" }
        val raw = File(directory, "legacy-source").also { check(it.mkdirs()) }
        val originals = listOf(source, File(source.path + "-wal"), File(source.path + "-shm"))
        val before = originals.associate { it.name to fingerprint(it) }
        for (file in originals.filter(File::isFile)) copyBounded(file, File(raw, file.name))
        check(originals.all { before[it.name] == fingerprint(it) }) { "旧账本在读取时发生变化，请关闭其他旧版进程后重试" }
        // The raw source copy is retained for recovery. SQLite may only touch a separate working copy.
        val work = File(directory, "legacy-read").also { check(it.mkdirs()) }
        raw.listFiles().orEmpty().forEach { copyBounded(it, File(work, it.name)) }
        val schema = context.assets.open("migration/legacy-v72.json").use { JSONObject(it.bufferedReader().readText()) }
        val database = JSONObject()
        SQLiteDatabase.openDatabase(File(work, source.name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.version == schema.getInt("version")) { "旧账本版本暂不支持安全迁移，原文件已保留" }
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) { "旧账本完整性检查失败，原文件已保留" }
            }
            db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == schema.getString("identityHash")) { "旧账本结构未识别，停止迁移" }
            }
            val tables = schema.getJSONArray("tables")
            for (i in 0 until tables.length()) {
                val table = tables.getJSONObject(i)
                val name = table.getString("name")
                val fields = table.getJSONArray("fields")
                val rows = JSONArray()
                // Only fixed, reviewed schema names are interpolated, never user strings.
                require(name.matches(Regex("[a-z_]+")))
                db.rawQuery("SELECT * FROM `$name`", null).use { cursor ->
                    val expected = (0 until fields.length()).map { fields.getJSONObject(it).getString("column") }.toSet()
                    check(cursor.columnNames.toSet() == expected) { "旧账本表结构不匹配：$name" }
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        for (j in 0 until fields.length()) {
                            val field = fields.getJSONObject(j)
                            val index = cursor.getColumnIndexOrThrow(field.getString("column"))
                            val value = if (cursor.isNull(index)) {
                                check(!field.getBoolean("required")) { "旧账本含缺失必填字段：$name" }
                                JSONObject.NULL
                            } else when (field.getString("type")) {
                                "boolean" -> cursor.getLong(index).also { check(it == 0L || it == 1L) }.let { it == 1L }
                                "integer" -> cursor.getLong(index)
                                "text" -> cursor.getString(index)
                                "strings" -> JSONArray(cursor.getString(index).takeIf { it.isNotEmpty() && it.trim() != "[]" }?.split(',').orEmpty())
                                else -> error("不支持的旧账本字段类型")
                            }
                            row.put(field.getString("key"), value)
                        }
                        rows.put(row)
                    }
                }
                database.put(name, rows)
            }
        }
        val prefs = legacyPreferences(directory)
        val mainKey = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE).getString("main_account", null)
        val omissions = JSONObject().put("templates", database.getJSONArray("quick_templates").length())
            .put("defaultAccount", mainKey != null).put("originalDefaultAccountKey", mainKey ?: JSONObject.NULL)
            .put("missingAttachments", JSONArray()).put("missingAvatars", JSONArray())
        val root = JSONObject().put("_format", "Hootbook Legacy Recovery v1")
            .put("_created", java.time.LocalDateTime.now().toString())
            .put("metadata", JSONObject().put("database_version", 72).put("export_id", operationId)
                .put("omissions", omissions).put("sourceFingerprints", JSONObject(before)).put("legacyBridge", JSONObject().put("format", "hootbook.android.legacy-bridge")
                    .put("version", 1).put("sourceSchemaVersion", 72).put("operationId", operationId)))
            .put("database", database).put("preferences", prefs)
        val output = File(directory, "source.backup")
        val references = linkedSetOf<String>()
        val avatarReferences = linkedSetOf<String>()
        database.getJSONArray("transactions").let { rows -> for (i in 0 until rows.length()) {
            rows.getJSONObject(i).optString("attachments").split(',').filter(String::isNotBlank).forEach(references::add)
        } }
        database.getJSONArray("lend_borrow_transactions").let { rows -> for (i in 0 until rows.length()) {
            rows.getJSONObject(i).getJSONArray("attachments").let { a -> for (j in 0 until a.length()) references += a.getString(j) }
        } }
        database.getJSONArray("lend_borrow_persons").let { rows -> for (i in 0 until rows.length()) {
            rows.getJSONObject(i).optString("avatar").takeIf { it.startsWith("file:") }?.let { value ->
                val file = runCatching { File(java.net.URI(value)) }.getOrNull()
                val base = File(context.filesDir,"avatars").canonicalFile
                if (file != null && file.canonicalFile.parentFile == base) {
                    references += "avatars/${file.name}"
                    avatarReferences += "avatars/${file.name}"
                } else omissions.getJSONArray("missingAvatars").put(value)
            }
        } }
        // Missing assets are loss-preview facts, never silently claimed as restored.
        val readableReferences = references.mapNotNull { name ->
            val file = runCatching { CoreBackupFiles.safeChild(context.filesDir, name) }.getOrNull()
            if (file?.isFile == true) name to file
            else { omissions.getJSONArray(if (name in avatarReferences) "missingAvatars" else "missingAttachments").put(name); null }
        }
        var totalBytes = 0L
        ZipOutputStream(output.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("legacy-data.json")); val bytes = root.toString().toByteArray()
            totalBytes += bytes.size; check(totalBytes <= CoreBackupFiles.MAX_BACKUP_BYTES)
            zip.write(bytes); zip.closeEntry()
            for (file in raw.listFiles().orEmpty()) {
                totalBytes += file.length(); check(totalBytes <= CoreBackupFiles.MAX_BACKUP_BYTES)
                zip.putNextEntry(ZipEntry("source-database/${file.name}")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            File(directory, "legacy_preferences.preferences_pb").takeIf(File::isFile)?.let { file ->
                totalBytes += file.length(); check(totalBytes <= CoreBackupFiles.MAX_BACKUP_BYTES)
                zip.putNextEntry(ZipEntry("source-preferences/user_preferences.preferences_pb")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            for ((name, file) in readableReferences) {
                totalBytes += file.length(); check(totalBytes <= CoreBackupFiles.MAX_BACKUP_BYTES)
                zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
        output
    }

    private suspend fun legacyPreferences(directory: File): JSONObject {
        val source = File(context.filesDir, "datastore/user_preferences.preferences_pb")
        val currency = JSONObject()
        if (source.isFile) {
            val copy = File(directory, "legacy_preferences.preferences_pb")
            val before = fingerprint(source); copyBounded(source, copy)
            check(before == fingerprint(source)) { "旧偏好设置在读取时发生变化" }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val values = try { PreferenceDataStoreFactory.create(scope=scope,produceFile={copy}).data.first().asMap().mapKeys { it.key.name } }
                finally { scope.cancel() }
            listOf("unified_currency_enabled", "unified_currency_code", "default_currency_enabled", "default_currency_code").forEach { key ->
                values[key]?.let { currency.put(key, it) }
            }
            (values["custom_currencies_json"] as? String)?.let { currency.put("custom_currencies", JSONArray(it)) }
        }
        return JSONObject().put("currency", currency)
    }

    private fun copyBounded(source: File, target: File) {
        require(source.length() <= CoreBackupFiles.MAX_BACKUP_BYTES) { "旧数据文件超过迁移大小限制" }
        source.inputStream().use { input -> target.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024); var total = 0L
            while (true) { val n = input.read(buffer); if(n < 0) break
                total += n; check(total <= CoreBackupFiles.MAX_BACKUP_BYTES); output.write(buffer,0,n) }
        } }
    }
    companion object {
        internal fun fingerprint(file: File): String? = if (!file.isFile) null else {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buf = ByteArray(64*1024); while(true) {val n=input.read(buf);if(n<0)break;digest.update(buf,0,n)} }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
