package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.about

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.effects.BlurredAnimatedVisibility
import io.github.hootkit_dev.hootbook.presentation.ui.components.CustomTitleTopAppBar
import io.github.hootkit_dev.hootbook.presentation.ui.components.NavigationContent
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onNavigateBack: () -> Unit,
    coreSchemaVersion: UInt? = null
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    val libraries = remember {
        listOf(
            Library("Jetpack Compose", 1, "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),

            Library("Hilt", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),

            Library("HootKit Core (Rust & UniFFI)", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),

            Library("Haze", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),

            Library("Ktor", 1, "" +
                    "Apache License 2.0" +
                    "\nCopyright 2000-2023 JetBrains s.r.o.\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License."),

            Library("Coil", 1, "" +
                    "Apache License 2.0" +
                    "\nCopyright 2025 Coil Contributors\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License."),

            Library("Kotlinx Serialization", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),

            Library("DataStore", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),
            Library("Biometric Auth", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),
            Library("WorkManager", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),
            Library("Compose Charts", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n"),
            Library("Reorderable", 1,  "" +
                    "Apache License 2.0" +
                    "  \nCopyright [yyyy] [name of copyright owner]\n" +
                    "\n" +
                    "   Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                    "   you may not use this file except in compliance with the License.\n" +
                    "   You may obtain a copy of the License at\n" +
                    "\n" +
                    "       http://www.apache.org/licenses/LICENSE-2.0\n" +
                    "\n" +
                    "   Unless required by applicable law or agreed to in writing, software\n" +
                    "   distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "   See the License for the specific language governing permissions and\n" +
                    "   limitations under the License.\n" +
                    "\n")
        )
    }


    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = selectedLibrary?.name ?: stringResource(R.string.licenses),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { 
                    NavigationContent { 
                        if (selectedLibrary != null) {
                            selectedLibrary = null
                        } else {
                            onNavigateBack()
                        }
                    } 
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding =
                PaddingValues(
                    top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                    start = Dimensions.Padding.content,
                    end = Dimensions.Padding.content,
                    bottom = 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            item {
                BlurredAnimatedVisibility(
                    visible = selectedLibrary == null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(Spacing.xl))

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = coreSchemaVersion?.let { stringResource(R.string.license_version_format, io.github.hootkit_dev.hootbook.BuildConfig.VERSION_NAME, it.toInt()) } ?: io.github.hootkit_dev.hootbook.BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(Spacing.xl))

                        Text(
                            text = stringResource(R.string.license_disclaimer_software),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(Spacing.lg))

                        Text(
                            text = stringResource(R.string.license_disclaimer_rates),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(Spacing.lg))

                        Text(
                            text = stringResource(R.string.powered_by),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(Spacing.xxl))
                    }
                }
            }
            items(libraries) { library ->
                BlurredAnimatedVisibility(
                    visible = selectedLibrary == null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLibrary = library }
                            .padding(vertical = Spacing.md),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = library.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = pluralStringResource(R.plurals.licenses_count, library.licenseCount, library.licenseCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            item {
                BlurredAnimatedVisibility(
                    visible = selectedLibrary != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Text(
                        text = selectedLibrary?.licenseText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md)
                    )
                }
            }
        }
    }
}


data class Library(
    val name: String,
    val licenseCount: Int,
    val licenseText: String
)

