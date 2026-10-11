package io.github.hootkit_dev.hootbook.presentation.core.profile

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.core.ui.CorePlatformAssets

/** Display consumer for new transaction/home screens; contains no old Entity dependencies. */
@Composable
fun CoreMerchantIcon(merchant:String,assets:CorePlatformAssets,modifier:Modifier=Modifier){
    val revision by assets.revision.collectAsStateWithLifecycle();val image=remember(merchant,revision){assets.merchantIcon(merchant)}
    image?.let{AsyncImage(it,merchant,modifier)}
}
