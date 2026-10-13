package io.github.hootkit_dev.hootbook.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface CoreLocaleGateState {
    data object Loading : CoreLocaleGateState
    data class Ready(val tag: String) : CoreLocaleGateState
    data object Failed : CoreLocaleGateState
}

/** UI locale key, not table revisions, drives this gate. The root session is never reopened. */
internal class CoreLocaleReconciler(private val apply: suspend (String) -> Unit) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<CoreLocaleGateState>(CoreLocaleGateState.Loading)
    val state = mutable.asStateFlow()
    private var appliedTag: String? = null

    suspend fun reconcile(resolveTag: () -> String) = mutex.withLock {
        mutable.value = CoreLocaleGateState.Loading
        try {
            val tag = resolveTag()
            if (appliedTag != tag) {
                apply(tag)
                appliedTag = tag
            }
            mutable.value = CoreLocaleGateState.Ready(tag)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = CoreLocaleGateState.Failed }
    }
}
