package io.github.hootkit_dev.hootbook.presentation.ui.features.ai

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * UniFFI resumes its poll continuation from a synchronous Rust waker. Never inline that
 * continuation on the thread holding a native session mutex. IO executes native calls off Main;
 * unconditional dispatch also prevents reentrant polling when cancel and the waker happen on the
 * same worker.
 */
internal object CoreAiNativeDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext) = true

    override fun dispatch(context: CoroutineContext, block: Runnable) =
        Dispatchers.IO.dispatch(context, block)
}
