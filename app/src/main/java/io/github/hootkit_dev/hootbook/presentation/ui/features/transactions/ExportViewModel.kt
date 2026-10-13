package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.presentation.format.coreFailureText
import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hootkit.core.*
import dev.hootkit.platform.ChangeBus
import dev.hootkit.platform.CoreSession
import io.github.hootkit_dev.hootbook.core.attachments.CoreAttachmentStore
import io.github.hootkit_dev.hootbook.presentation.data.coreQuery
import io.github.hootkit_dev.hootbook.presentation.data.observe
import io.github.hootkit_dev.hootbook.presentation.data.withCore
import io.github.hootkit_dev.hootbook.presentation.export.CoreTransactionCsv
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExportPreview(
    val oldest: Transaction? = null,
    val newest: Transaction? = null,
    val total: UInt = 0u,
    val error: Int? = null,
)

@HiltViewModel
class ExportViewModel
@Inject
constructor(
    private val session: CoreSession,
    private val changes: ChangeBus,
    private val attachments: CoreAttachmentStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ExportState>(ExportState.Ready)
    val state = mutableState.asStateFlow()
    private val mutablePreview = MutableStateFlow(ExportPreview())
    val preview = mutablePreview.asStateFlow()
    private var previewJob: Job? = null

    fun observeFilter(filter: TransactionFilter) {
        previewJob?.cancel()
        mutablePreview.value = ExportPreview()
        previewJob =
            viewModelScope.launch {
                session
                    .observe(changes, ledgerTables) { core ->
                        coreQuery {
                            core.ledger().use { ledger ->
                                val oldest =
                                    ledger.list(
                                        filter.copy(sort = SortOrder.OLDEST_FIRST),
                                        PageRequest(0u, 1u),
                                    )
                                val newest =
                                    ledger.list(
                                        filter.copy(sort = SortOrder.NEWEST_FIRST),
                                        PageRequest(0u, 1u),
                                    )
                                ExportPreview(
                                    oldest.items.firstOrNull(),
                                    newest.items.firstOrNull(),
                                    newest.total,
                                )
                            }
                        }
                    }
                    .collect { result ->
                        mutablePreview.value =
                            result.getOrElse { ExportPreview(error = coreFailureText(it)) }
                    }
            }
    }

    fun stopPreview() {
        previewJob?.cancel()
    }

    fun reset() {
        if (state.value !is ExportState.Exporting) mutableState.value = ExportState.Ready
    }

    fun exportTransactions(filter: TransactionFilter) {
        if (state.value is ExportState.Exporting) return
        mutableState.value = ExportState.Exporting()
        val revision = changes.revisions.value
        viewModelScope.launch {
            var file: File? = null
            var complete = false
            try {
                val target = attachments.createShareFile("csv")
                file = target
                val count =
                    session.withCore { core ->
                        val accounts = core.accounts().use { it.overview().accounts }
                        val categories = core.categories().use { it.list(null, true) }
                        core.ledger().use { ledger ->
                            target.outputStream().use { output ->
                                CoreTransactionCsv.export(
                                    ledger,
                                    filter,
                                    output,
                                    accounts,
                                    categories,
                                    onProgress = { written, total ->
                                        mutableState.value = ExportState.Exporting(written, total)
                                    },
                                ) {
                                    changes.revisions.value == revision
                                }
                            }
                        }
                    }
                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        target,
                    )
                mutableState.value =
                    ExportState.Success(uri, target.name, count.toInt(), target.length())
                complete = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = ExportState.Error(coreFailureText(failure))
            } finally {
                if (!complete) file?.delete()
            }
        }
    }
}
