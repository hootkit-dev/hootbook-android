package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import io.github.hootkit_dev.hootbook.presentation.ui.theme.HootbookTheme
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions

@Composable
fun IphoneFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(0.48f) // iPhone 14/15 Pro aspect ratio
            .clip(RoundedCornerShape(40.dp))
            .background(Color(0xFF131313))
            .border(4.dp, Color(0xFF2C2C2C), RoundedCornerShape(40.dp)) // Darker bezel
            .padding(8.dp) // Bezel
    ) {
        // Outer Frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF000000)) // Pure black screen area
                .border(4.dp, Color(0xFF1A1A1A), RoundedCornerShape(32.dp))
        ) {
            // Screen Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                content()
            }

            // Dynamic Island / Notch
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .width(80.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            )

            // Home Indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .width(100.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IphoneFramePreview() {
    HootbookTheme {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            IphoneFrame(modifier = Modifier.height(600.dp)) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.screen_content), color = MaterialTheme.colorScheme.inverseSurface)
                }
            }
        }
    }
}
