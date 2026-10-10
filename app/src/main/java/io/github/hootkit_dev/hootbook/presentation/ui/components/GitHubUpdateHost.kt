package io.github.hootkit_dev.hootbook.presentation.ui.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about.GitHubUpdateViewModel

@Composable
fun GitHubUpdateHost() {
    val viewModel: GitHubUpdateViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkForUpdate(promptIfAvailable = true)
    }

    val release = state.available
    if (state.showDialog && release != null) {
        GitHubUpdateDialog(
            release = release,
            onDismiss = { viewModel.dismissDialog() },
            onDownload = { item ->
                viewModel.hideDialog()
                val target = item.apkUrl ?: item.htmlUrl
                if (target.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                }
            }
        )
    }
}
