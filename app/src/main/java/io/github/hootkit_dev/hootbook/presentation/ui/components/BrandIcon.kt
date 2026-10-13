package io.github.hootkit_dev.hootbook.presentation.ui.components

import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hootkit_dev.hootbook.utils.IconResolutionUtils
import dev.hootkit.core.Category
import dev.hootkit.core.Subcategory
import io.github.hootkit_dev.hootbook.presentation.common.icons.CategoryMapping
import io.github.hootkit_dev.hootbook.presentation.common.icons.IconProvider
import io.github.hootkit_dev.hootbook.presentation.common.icons.IconResource

/**
 * Displays a brand icon with intelligent fallback
 * Fallback priority: Brand Icon > Subcategory Icon > Category Icon
 */
@Composable
fun BrandIcon(
    merchantName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showBackground: Boolean = true,
    categoryEntity: Category? = null,
    subcategoryEntity: Subcategory? = null,
    category: String? = null,
    subcategory: String? = null,
    accountIconResId: Int = 0,
    accountIconName: String? = null,
    accountColorHex: String? = null
) {
    val context = LocalContext.current
    // An icon the user picked for this merchant comes before everything else
    val iconStore = remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, PlatformAssetsEntryPoint::class.java)
                .assets()
        }.getOrNull()
    }
    val revision = iconStore?.revision?.collectAsState()?.value
    val customIcon = remember(iconStore, revision, merchantName) { iconStore?.merchantIcon(merchantName) }
    if (customIcon != null) {
        AsyncImage(
            model = customIcon,
            contentDescription = merchantName,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
        return
    }
    val iconResource = remember(merchantName, categoryEntity, subcategoryEntity, category, subcategory, accountIconResId, accountIconName) {
        IconProvider.getIconForTransaction(
            context = context,
            merchantName = merchantName,
            categoryEntity = categoryEntity,
            subcategoryEntity = subcategoryEntity,
            category = category,
            subcategory = subcategory,
            accountIconResId = accountIconResId,
            accountIconName = accountIconName
        )
    }
    val brandColor = remember(merchantName, categoryEntity, subcategoryEntity, category, subcategory, accountColorHex) {
        IconProvider.getColorForTransaction(
            merchantName = merchantName,
            categoryEntity = categoryEntity,
            subcategoryEntity = subcategoryEntity,
            category = category,
            subcategory = subcategory,
            accountColorHex = accountColorHex
        )
    }

    val internalPadding = remember(iconResource, showBackground) {
        // Only pad inward when there is a background circle to inset from.
        // Without a background, padding just shrinks the visible icon.
        if (!showBackground || iconResource is IconResource.DrawableResource) 0.dp else 8.dp
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBackground) {
                    Modifier
                        .clip(CircleShape)
                        .background(
                            brandColor?.let { (colorHex, alpha) ->
                                Color(colorHex.toColorInt()).copy(alpha = alpha)
                            } ?: generateColorFromString(merchantName)
                        )
                } else {
                    Modifier
                }
            )
            .padding(internalPadding),
        contentAlignment = Alignment.Center
    ) {
        when (iconResource) {
            is IconResource.DrawableResource -> {
                // Decode bitmap logos off the UI thread at the displayed size.
                AsyncImage(
                    model = remember(iconResource.resId, context) {
                        ImageRequest.Builder(context).data(iconResource.resId)
                            .fetcherCoroutineContext(Dispatchers.IO)
                            .decoderCoroutineContext(Dispatchers.IO)
                            .build()
                    },
                    contentDescription = merchantName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is IconResource.VectorIcon -> {
                // Category icon fallback
                Icon(
                    imageVector = iconResource.icon,
                    contentDescription = merchantName,
                    tint = if (showBackground) Color.White else iconResource.tint,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is IconResource.TintedResIcon -> {
                AsyncImage(
                    model = remember(iconResource.resId, context) {
                        ImageRequest.Builder(context).data(iconResource.resId)
                            .fetcherCoroutineContext(Dispatchers.IO)
                            .decoderCoroutineContext(Dispatchers.IO)
                            .build()
                    },
                    contentDescription = merchantName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun CategoryIcon(
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = Color.Unspecified,
    iconResId: Int = 0,
    iconName: String? = null
) {
    val context = LocalContext.current
    val painter = if (!iconName.isNullOrEmpty()) {
        val resId = IconResolutionUtils.nameToResId(context, iconName)
        if (resId != 0) painterResource(id = resId) else {
            val categoryInfo = CategoryMapping.categories[category]
                ?: CategoryMapping.categories["Miscellaneous"]!!
            painterResource(id = categoryInfo.iconResId)
        }
    } else if (iconResId != 0) {
        val safeResId = IconResolutionUtils.getSafeResId(context, iconResId, 0)
        if (safeResId != 0) {
            painterResource(id = safeResId)
        } else {
            val categoryInfo = CategoryMapping.categories[category]
                ?: CategoryMapping.categories["Miscellaneous"]!!
            painterResource(id = categoryInfo.iconResId)
        }
    } else {
        val categoryInfo = CategoryMapping.categories[category]
            ?: CategoryMapping.categories["Miscellaneous"]!!
        painterResource(id = categoryInfo.iconResId)
    }


    Icon(
        painter = painter,
        contentDescription = category,
        tint = tint ?: Color.Unspecified,
        modifier = modifier.size(size)
    )
}

/**
 * Utility function to generate consistent colors from strings
 */
private fun generateColorFromString(str: String): Color {
    val colors = listOf(
        Color(0xFF6750A4), // Material Purple
        Color(0xFF0061A4), // Material Blue
        Color(0xFF006D40), // Material Green
        Color(0xFFB3261E), // Material Red
        Color(0xFF9A4521), // Material Orange
        Color(0xFF6D4C41), // Material Brown
        Color(0xFF455A64), // Material Blue Grey
        Color(0xFF5E35B1), // Deep Purple
        Color(0xFF43A047), // Green
        Color(0xFFE53935), // Red
    )

    val hash = str.hashCode()
    return colors[Math.abs(hash) % colors.size]
}

/**
 * Extension to convert hex string to Color Int
 */
private fun String.toColorInt(): Int {
    // Remove # if present and parse hex
    val hex = this.removePrefix("#")
    return android.graphics.Color.parseColor("#$hex")
}
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlatformAssetsEntryPoint { fun assets(): CorePlatformAssets }
