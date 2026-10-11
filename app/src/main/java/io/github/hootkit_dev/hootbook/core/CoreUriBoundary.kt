package io.github.hootkit_dev.hootbook.core

import android.content.Context
import android.net.Uri

/** Shared confused-deputy boundary for SEND, SAF, attachment/avatar/merchant/image inputs. */
object CoreUriBoundary {
    fun requireExternalDocument(context: Context, uri: Uri): Uri {
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank() && uri.userInfo == null && uri.port == -1 && uri.fragment == null) { "Choose an external content document" }
        val authority = uri.authority!!.substringAfterLast('@').lowercase(java.util.Locale.ROOT)
        require(authority != context.packageName.lowercase(java.util.Locale.ROOT) + ".fileprovider" &&
            !authority.startsWith(context.packageName.lowercase(java.util.Locale.ROOT) + ".")) { "Private application documents are not accepted as external input" }
        return uri
    }
}
