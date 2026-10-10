package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.HazeState

/**
 * The top app bar of the app's secondary screens.
 *
 * With two different scroll behaviors it is a Material 3 Expressive large flexible app bar:
 * the large title collapses into the bar as the content scrolls. With one (pass the same
 * behavior twice) it is a small top app bar. Either way the bar is tonal: surface at rest,
 * surfaceContainer once content scrolls under it, as Material 3 specifies; no blur or
 * gradient.
 *
 * [hazeState] and [blurEffects] are kept so existing call sites compile; they no longer do
 * anything here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomTitleTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehaviorSmall: TopAppBarScrollBehavior,
    scrollBehaviorLarge: TopAppBarScrollBehavior,
    title: String,
    hasBackButton: Boolean = false,
    @Suppress("UNUSED_PARAMETER") hasActionButton: Boolean = false,
    actionContent: @Composable () -> Unit = {},
    navigationContent: @Composable () -> Unit = {},
    extraInfoCard: @Composable () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState = HazeState(),
    @Suppress("UNUSED_PARAMETER") blurEffects: Boolean = false,
    showTitleInLargeBar: Boolean = true
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    val navigation: @Composable () -> Unit = { if (hasBackButton) navigationContent() }
    val actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = { actionContent() }

    if (scrollBehaviorLarge != scrollBehaviorSmall) {
        LargeFlexibleTopAppBar(
            title = {
                Column {
                    if (showTitleInLargeBar) Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    extraInfoCard()
                }
            },
            navigationIcon = navigation,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehaviorLarge,
            modifier = modifier
        )
    } else {
        TopAppBar(
            title = { if (showTitleInLargeBar) Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = navigation,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehaviorSmall,
            modifier = modifier
        )
    }
}
