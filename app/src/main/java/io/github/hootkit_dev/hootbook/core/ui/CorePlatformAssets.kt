package io.github.hootkit_dev.hootbook.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.BackupFile
import io.github.hootkit_dev.hootbook.core.backup.CoreBackupFiles
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Portable content-addressed platform images. Originals/restore staging are never deleted. */
@Singleton
class CorePlatformAssets @Inject constructor(@ApplicationContext private val context: Context) {
    val root = File(context.noBackupFilesDir,"hootkit-core/ui/assets").also { check(it.mkdirs()||it.isDirectory) }
    private val mutex=Mutex()
    private val changes=MutableStateFlow(0L)
    val revision=changes.asStateFlow()
    fun resolve(relative: String?): File? = relative?.let { CoreBackupFiles.safeChild(root,it).takeIf(File::isFile) }
    suspend fun importImage(uri: Uri): String = withContext(Dispatchers.IO) {
        io.github.hootkit_dev.hootbook.core.CoreUriBoundary.requireExternalDocument(context, uri)
        context.contentResolver.openInputStream(uri)?.use { installImage(it) } ?: error("Image provider is unavailable")
    }
    suspend fun installImage(source: File): String = withContext(Dispatchers.IO) { source.inputStream().use { installImage(it) } }
    private suspend fun installImage(input: InputStream): String {
        val bytes=readLimited(input,MAX_IMAGE_BYTES)
        require(hasImageSignature(bytes)) {"Invalid image file signature"}
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        require(options.outWidth in 1..12000 && options.outHeight in 1..12000 && options.outWidth.toLong()*options.outHeight<=48_000_000L) { "Invalid or oversized image" }
        val hash=sha(bytes); val name="images/$hash.image"; val file=CoreBackupFiles.safeChild(root,name)
        writeAtomic(file,bytes); changes.value++; return name
    }
    suspend fun saveMerchantIcon(merchant: String, uri: Uri) = withContext(Dispatchers.IO) {
        val image=importImage(uri)
        mutex.withLock { val index=readIndex(); index.put(key(merchant),image); writeAtomic(indexFile,index.toString().toByteArray()); changes.value++ }
    }
    suspend fun saveMerchantIcon(merchant: String, bitmap: android.graphics.Bitmap) = withContext(Dispatchers.IO) {
        val bytes=java.io.ByteArrayOutputStream().also {check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}.toByteArray()
        val image=installImage(java.io.ByteArrayInputStream(bytes))
        mutex.withLock {val index=readIndex();index.put(key(merchant),image);writeAtomic(indexFile,index.toString().toByteArray());changes.value++}
    }
    suspend fun removeMerchantIcon(merchant: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val index=readIndex(); index.remove(key(merchant)); writeAtomic(indexFile,index.toString().toByteArray()); changes.value++
    } }
    fun merchantIcon(merchant: String): File? = readIndex().optString(key(merchant)).takeIf {it.isNotBlank()}?.let(::resolve)
    private val indexFile get()=File(root,"merchant_icons/index.json")
    private fun readIndex(): JSONObject {
        val atomic=AtomicFile(indexFile)
        if(!indexFile.exists()&&!File(indexFile.path+".bak").exists()) return JSONObject()
        return atomic.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
    }
    suspend fun restoreMerchantIndex(indexSource: File, installed: Map<String,String>, legacy: Boolean) = withContext(Dispatchers.IO) {
        require(indexSource.length()<=256*1024)
        val input=JSONObject(indexSource.readText()); val entries=linkedMapOf<String,String>()
        require(input.length()<=1000)
        input.keys().forEach { merchant ->
            val name=input.getString(merchant)
            val path=if(legacy) "merchant_icons/$name" else name
            // Only files explicitly returned by Core may be installed/referenced, never guessed paths.
            val image=installed[path] ?: error("Merchant icon file is missing")
            require(resolve(image)!=null); entries[key(merchant)]=image
        }
        mutex.withLock { val current=readIndex(); entries.forEach { (k,v)->current.put(k,v) }; writeAtomic(indexFile,current.toString().toByteArray()); changes.value++ }
    }
    /** Complete live image set and portable index; unknown restore files remain in their journal export. */
    suspend fun exportFiles(): List<BackupFile> = withContext(Dispatchers.IO) {
        root.walkTopDown().filter {it.isFile&&!it.name.endsWith(".bak")}.map {
            BackupFile("platform/"+it.relativeTo(root).invariantSeparatorsPath,it.path)
        }.toList()
    }
    suspend fun migrateLegacyImage(reference: String?): String? = withContext(Dispatchers.IO) {
        if(reference.isNullOrBlank()) return@withContext null
        val uri=Uri.parse(reference)
        if(uri.scheme=="content") {
            if (uri.authority == context.packageName + ".fileprovider") {
                val parts = uri.pathSegments
                require(parts.size == 2 && parts[0] in setOf("avatars", "attachments"))
                val directory = File(context.filesDir, parts[0]).canonicalFile
                val old = File(directory, parts[1]); require(old.canonicalFile.parentFile == directory && old.isFile)
                return@withContext installImage(old) // Trusted old-file migration; never an external URI shortcut.
            }
            return@withContext importImage(uri)
        }
        val f=if(uri.scheme=="file") File(checkNotNull(uri.path)) else File(reference)
        // Existing platform assets only: this does not read a legacy database or external arbitrary path.
        val base=context.filesDir.canonicalPath+File.separator
        require(f.canonicalPath.startsWith(base)&&f.isFile)
        installImage(f)
    }
    companion object {
        const val MAX_IMAGE_BYTES=20L*1024*1024
        /** Sniff bytes, not provider MIME/filename. BitmapFactory still validates bounds/codec support. */
        internal fun hasImageSignature(bytes: ByteArray): Boolean {
            fun at(offset:Int,value:String)=bytes.size>=offset+value.length && value.indices.all{bytes[offset+it].toInt().and(255)==value[it].code}
            val png=bytes.size>=8 && bytes.take(8).map{it.toInt().and(255)}==listOf(137,80,78,71,13,10,26,10)
            val jpeg=bytes.size>=3 && bytes[0].toInt().and(255)==255 && bytes[1].toInt().and(255)==216 && bytes[2].toInt().and(255)==255
            val gif=at(0,"GIF87a")||at(0,"GIF89a")
            val webp=at(0,"RIFF")&&at(8,"WEBP")
            val bmp=bytes.size>=14&&at(0,"BM")
            val isobmff=at(4,"ftyp") && (listOf("heic","heix","hevc","hevx","mif1","msf1","avif","avis").any{brand->
                (8..minOf(bytes.size-4,64) step 4).any{offset->at(offset,brand)}
            })
            return png||jpeg||gif||webp||bmp||isobmff
        }
        fun key(merchant: String)=merchant.trim().lowercase(Locale.ROOT)
        fun sha(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        suspend fun readLimited(input: InputStream, limit: Long): ByteArray {
            val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(DEFAULT_BUFFER_SIZE); var count=0L
            while(true) { currentCoroutineContext().ensureActive(); val n=input.read(buffer); if(n<0)break; count+=n;require(count<=limit){"File exceeds size limit"};out.write(buffer,0,n) }
            require(count>0);return out.toByteArray()
        }
        fun writeAtomic(file: File, bytes: ByteArray) {
            check(file.parentFile!!.mkdirs()||file.parentFile!!.isDirectory)
            val atomic=AtomicFile(file);val out=atomic.startWrite()
            try {out.write(bytes);atomic.finishWrite(out)} catch(failure:Throwable){atomic.failWrite(out);throw failure}
        }
    }
}
