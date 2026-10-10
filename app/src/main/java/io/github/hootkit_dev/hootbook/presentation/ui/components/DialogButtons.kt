@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.material3.LoadingIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Dialogs follow Material 3: a solid, opaque container with the extra-large shape, and
// end-aligned actions — a text button to dismiss, a filled one to confirm — whose corners
// morph when pressed (Expressive). No blur: a dialog has nothing behind it worth showing.

object HootbookDialogDefaults {
    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
}

/** The dialog's main action; [destructive] colors it as an error (delete, reset). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shapes = ButtonDefaults.shapes(),
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        } else ButtonDefaults.buttonColors(),
        modifier = modifier
    ) {
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(24.dp), color = LocalContentColor.current)
        } else {
            Text(text)
        }
    }
}

/** Closes the dialog without acting: cancel, done, not now. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = modifier) {
        Text(text)
    }
}

/** Both actions in a row, for dialogs built on Dialog rather than AlertDialog. */
@Composable
fun DialogActionsRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}
