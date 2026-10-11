package io.github.hootkit_dev.hootbook.core.migration

import io.github.hootkit_dev.hootbook.core.backup.CoreBackupFiles
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/** Transport only: values stay in the original schema and the frozen Core performs conversion. */
internal object LegacyCompatibleArchive {
    fun create(recovery: File, target: File, services: Map<String, ByteArray>): JSONObject {
        require(recovery.canonicalFile != target.canonicalFile && !target.exists())
        require(services.keys.all { it == "ai.json" || it == "brokerage.json" })
        ZipFile(recovery).use { input ->
            val entry = requireNotNull(input.getEntry("legacy-data.json"))
            require(entry.size in 1..CoreBackupFiles.MAX_BACKUP_BYTES)
            val document = input.getInputStream(entry).use { JSONObject(it.bufferedReader().readText()) }
            require(document.getString("_format") == "Hootbook Legacy Recovery v1")
            val metadata = document.getJSONObject("metadata")
            document.put("_format", "Cashiro Backup v1.0")
            // Core has no quick-template field in its legacy importer. The original full table
            // remains in recovery.zip and its exact count is shown before user confirmation.
            document.getJSONObject("database").remove("quick_templates")
            var size = 0L
            ZipOutputStream(target.outputStream()).use { output ->
                fun bytes(name: String, value: ByteArray) {
                    size += value.size; require(size <= CoreBackupFiles.MAX_BACKUP_BYTES)
                    output.putNextEntry(ZipEntry(name)); output.write(value); output.closeEntry()
                }
                bytes("backup.json", document.toString().toByteArray())
                input.entries().asSequence().filter { !it.isDirectory && it.name != "legacy-data.json" &&
                    !it.name.startsWith("source-database/") && !it.name.startsWith("source-preferences/") }.forEach { file ->
                    size += file.size; require(file.size >= 0 && size <= CoreBackupFiles.MAX_BACKUP_BYTES)
                    output.putNextEntry(ZipEntry(file.name))
                    input.getInputStream(file).use { it.copyTo(output) }; output.closeEntry()
                }
                services.forEach(::bytes)
            }
            return metadata
        }
    }
}
