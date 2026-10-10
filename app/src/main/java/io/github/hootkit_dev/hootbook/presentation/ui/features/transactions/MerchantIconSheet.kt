package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.data.icons.AppIconCandidate
import io.github.hootkit_dev.hootbook.data.icons.AppStoreIconSearch
import io.github.hootkit_dev.hootbook.data.icons.IconSearchException
import io.github.hootkit_dev.hootbook.data.icons.MerchantIconStore
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MerchantIconSearchState(
    val loading: Boolean = false,
    val results: List<AppIconCandidate>? = null,
    val error: String? = null,
    // The candidate being downloaded and saved
    val saving: AppIconCandidate? = null
)

@HiltViewModel
class MerchantIconViewModel @Inject constructor(
    private val store: MerchantIconStore,
    private val search: AppStoreIconSearch
) : ViewModel() {
    private val _state = MutableStateFlow(MerchantIconSearchState())
    val state: StateFlow<MerchantIconSearchState> = _state.asStateFlow()
    val icons = store.icons
    private var searching: Job? = null

    fun search(term: String) {
        if (term.isBlank()) return
        searching?.cancel()
        searching = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            _state.update {
                try {
                    it.copy(loading = false, results = search.search(term))
                } catch (e: IconSearchException) {
                    it.copy(loading = false, error = e.message)
                }
            }
        }
    }

    fun choose(merchantName: String, candidate: AppIconCandidate, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(saving = candidate, error = null) }
            try {
                store.save(merchantName, search.download(candidate))
                _state.update { it.copy(saving = null) }
                onDone()
            } catch (e: IconSearchException) {
                _state.update { it.copy(saving = null, error = e.message) }
            }
        }
    }

    fun reset(merchantName: String) = store.remove(merchantName)
}

/**
 * Picks an icon for every transaction with this merchant: searches the App Store for the name
 * (or what the user types), and keeps the chosen app icon on the device.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MerchantIconSheet(
    merchantName: String,
    onDismiss: () -> Unit,
    viewModel: MerchantIconViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val icons by viewModel.icons.collectAsState()
    val hasCustomIcon = icons.containsKey(MerchantIconStore.key(merchantName))
    var query by rememberSaveable { mutableStateOf(merchantName) }
    // The user opened this to look for an icon: search the merchant's name straight away
    LaunchedEffect(Unit) { viewModel.search(merchantName) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(stringResource(R.string.merchant_icon_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.merchant_icon_search_hint)) },
                trailingIcon = {
                    IconButton(onClick = { viewModel.search(query) }) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.merchant_icon_search_hint))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.merchant_icon_privacy, merchantName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.error?.let {
                Text(
                    stringResource(R.string.merchant_icon_error, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)) {
                val results = state.results
                when {
                    state.loading -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))
                    results != null && results.isEmpty() -> Text(
                        stringResource(R.string.merchant_icon_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    results != null -> LazyColumn {
                        items(results, key = { it.iconUrl }) { app ->
                            CandidateRow(
                                app = app,
                                saving = state.saving == app,
                                enabled = state.saving == null,
                                onClick = { viewModel.choose(merchantName, app, onDismiss) }
                            )
                        }
                    }
                }
            }
            if (hasCustomIcon) {
                OutlinedButton(
                    onClick = {
                        viewModel.reset(merchantName)
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.merchant_icon_reset))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CandidateRow(app: AppIconCandidate, saving: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AsyncImage(
            model = app.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.seller,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (saving) LoadingIndicator(modifier = Modifier.size(24.dp))
    }
}
