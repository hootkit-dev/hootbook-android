package io.github.hootkit_dev.hootbook.presentation.ui.features.profile

import android.content.Context
import android.net.Uri
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keep existing content-addressed images; copy only new picker content or bundled avatars. */
internal suspend fun persistProfileImage(context: Context, assets: CorePlatformAssets, uri: Uri?): String? {
    if (uri == null) return null
    if (uri.scheme == "file") {
        val file = File(requireNotNull(uri.path)).canonicalFile
        require(file.path.startsWith(assets.root.canonicalPath + File.separator) && file.isFile)
        return file.relativeTo(assets.root.canonicalFile).invariantSeparatorsPath
    }
    if (uri.scheme != "android.resource") return assets.importImage(uri)
    require(uri.authority == context.packageName || uri.authority == "io.github.hootkit_dev.hootbook")
    val resource = requireNotNull(uri.lastPathSegment?.toIntOrNull())
    return withContext(Dispatchers.IO) {
        val file = File.createTempFile("profile-avatar-", ".image", context.cacheDir)
        try {
            context.resources.openRawResource(resource).use { input -> file.outputStream().use { input.copyTo(it) } }
            assets.installImage(file)
        } finally { file.delete() }
    }
}
