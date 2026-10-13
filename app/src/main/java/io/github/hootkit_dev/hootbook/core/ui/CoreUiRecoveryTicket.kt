package io.github.hootkit_dev.hootbook.core.ui

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** Short-lived, memory-only UI recovery capability issued after a device-auth success callback. */
class CoreUiRecoveryTicket internal constructor(private val issued: Long = SystemClock.elapsedRealtime()) {
    private val used = AtomicBoolean()
    internal fun consume() { check(SystemClock.elapsedRealtime() - issued in 0..30_000 && used.compareAndSet(false, true)) { "Authenticate again before UI recovery" } }
}
