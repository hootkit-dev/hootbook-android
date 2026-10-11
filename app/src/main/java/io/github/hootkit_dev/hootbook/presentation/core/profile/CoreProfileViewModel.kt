package io.github.hootkit_dev.hootbook.presentation.core.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.core.ui.*
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.core.analytics.emptyCoreFilter
import io.github.hootkit_dev.hootbook.presentation.core.analytics.coreDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoreProfileUiState(val busy: Boolean = true, val error: Int? = null)
data class CoreProfileLedgerState(val overview: AccountsOverview? = null, val summary: LedgerSummary? = null,
    val subscriptions: SubscriptionSummary? = null, val contacts: List<Person> = emptyList(), val transactionCount: UInt = 0u,
    val loading: Boolean = true, val error: Int? = null)

@HiltViewModel
class CoreProfileViewModel @Inject constructor(
    val preferences: CoreUiPreferencesStore,
    val assets: CorePlatformAssets,
    private val session: CoreSession,
    changes: ChangeBus,
    val attachments: CoreAttachmentStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutable = MutableStateFlow(CoreProfileUiState(false)); val state = mutable.asStateFlow()
    private val ledger = MutableStateFlow(CoreProfileLedgerState()); val ledgerState = ledger.asStateFlow()
    private var query: Job? = null; private var revision = 0L
    init {
        command { preferences.ready() }
        viewModelScope.launch { changes.observe(setOf(Table.ACCOUNTS, Table.ACCOUNT_CURRENCIES, Table.TRANSACTIONS, Table.SUBSCRIPTIONS, Table.PEOPLE, Table.DEBTS, Table.SETTINGS, Table.EXCHANGE_RATES)).collect { refresh() } }
    }
    fun refresh() {
        val ticket = ++revision; query?.cancel()
        query = viewModelScope.launch {
            try {
                val core = session.awaitCore(); val today = java.time.LocalDate.now().coreDate()
                val overview = core.accounts().use { it.overview() }
                val (summary, count) = core.ledger().use { it.summary(emptyCoreFilter()) to it.list(emptyCoreFilter(), PageRequest(0u, 1u)).total }
                val (subscriptions, contacts) = core.plans().use { it.subscriptionSummary(today) to it.people(false, today) }
                if (ticket == revision) ledger.value = CoreProfileLedgerState(overview, summary, subscriptions, contacts, count, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (ticket == revision) ledger.value = ledger.value.copy(loading = false, error = R.string.core_ui_operation_failed) }
        }
    }
    fun save(name: String, background: Int, showBanner: Boolean, avatar: String? = preferences.state.value.avatar,
        banner: String? = preferences.state.value.banner, onSaved: () -> Unit = {}) = command {
        preferences.update { it.copy(userName = name.trim(), profileBackground = background, showBanner = showBanner, avatar = avatar, banner = banner) }; onSaved()
    }
    fun prepareImage(uri: Uri?, prepared: (String) -> Unit) { if (uri != null) command { prepared(assets.importImage(uri)) } }
    fun presetAvatar(resource: Int, prepared: (String) -> Unit) = command {
        require(resource in PRESET_AVATARS)
        // Only bundled app artwork enters this path; external documents use the strict URI boundary above.
        val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, resource)
        val file = java.io.File.createTempFile("profile-preset-", ".png", context.cacheDir)
        try { file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }; prepared(assets.installImage(file)) }
        finally { bitmap.recycle(); file.delete() }
    }
    fun image(uri: Uri?, banner: Boolean) { if (uri != null) command { val path = assets.importImage(uri); preferences.update { if (banner) it.copy(banner = path) else it.copy(avatar = path) } } }
    fun removeImage(banner: Boolean) = command { preferences.update { if (banner) it.copy(banner = null) else it.copy(avatar = null) } }
    private fun command(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = CoreProfileUiState(true)
        viewModelScope.launch {
            try { block(); mutable.value = CoreProfileUiState(false) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = CoreProfileUiState(false, R.string.core_ui_operation_failed) }
        }
    }
    companion object {
        val PRESET_AVATARS = listOf(R.drawable.avatar_1, R.drawable.avatar_2, R.drawable.avatar_3, R.drawable.avatar_4, R.drawable.avatar_5,
            R.drawable.avatar_6, R.drawable.avatar_7, R.drawable.avatar_8, R.drawable.avatar_9, R.drawable.avatar_10)
    }
}
