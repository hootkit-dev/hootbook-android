@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.update.GitHubRelease

@Composable
fun GitHubUpdateDialog(
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onDownload: (GitHubRelease) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(stringResource(R.string.update_available_body, release.title))
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { onDownload(release) }) {
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}
