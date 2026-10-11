package io.github.hootkit_dev.hootbook.core

import dev.hootkit.core.AuthProvider
import dev.hootkit.core.RemoteStore
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class CoreCapabilitiesTest {
    @Test fun standardAdapterPendingSuppliesNoRemoteCapabilities() {
        val capabilities = CoreCapabilities(CoreSyncAvailability.STANDARD_ADAPTER_PENDING)
        assertNull(capabilities.auth)
        assertNull(capabilities.remote)
    }

    @Test(expected = IllegalArgumentException::class)
    fun availableStatusWithoutAdaptersIsRejected() { CoreCapabilities(CoreSyncAvailability.AVAILABLE) }

    @Test(expected = IllegalArgumentException::class)
    fun authWithoutRemoteIsRejected() {
        CoreCapabilities(CoreSyncAvailability.AVAILABLE, fake(AuthProvider::class.java))
    }

    @Test(expected = IllegalArgumentException::class)
    fun standardAdapterPendingWithAdaptersIsRejected() {
        CoreCapabilities(CoreSyncAvailability.STANDARD_ADAPTER_PENDING,
            fake(AuthProvider::class.java), fake(RemoteStore::class.java))
    }

    @Test fun realAvailabilityRequiresBothAdapters() {
        val auth = fake(AuthProvider::class.java)
        val remote = fake(RemoteStore::class.java)
        val capabilities = CoreCapabilities(CoreSyncAvailability.AVAILABLE, auth, remote)
        assertSame(auth, capabilities.auth)
        assertSame(remote, capabilities.remote)
    }

    @Test fun processDetectionIncludesApi26CrashSuffixAndUnknownIdentity() {
        assertTrue(CoreProcess.isCrashProcess("io.github.hootkit_dev.hootbook:crash"))
        assertTrue(CoreProcess.isCrashProcess(null as String?))
        assertFalse(CoreProcess.isCrashProcess("io.github.hootkit_dev.hootbook"))
        assertFalse(CoreProcess.isCrashProcess("io.github.hootkit_dev.hootbook:worker"))
    }

    private fun <T : Any> fake(type: Class<T>): T = requireNotNull(type.cast(Proxy.newProxyInstance(
        type.classLoader, arrayOf(type),
    ) { _, method, _ -> error("Unexpected adapter call: ${method.name}") }))
}
