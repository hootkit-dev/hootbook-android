package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ArrowLeft02

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavigationContent(onNavigateBack: () -> Unit) {
    TooltipIconButton(icon = Iconax.ArrowLeft02, label = stringResource(R.string.cd_navigate_back), onClick = onNavigateBack)
}
