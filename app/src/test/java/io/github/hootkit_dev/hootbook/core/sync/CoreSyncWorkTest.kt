package io.github.hootkit_dev.hootbook.core.sync

import android.app.Application
import androidx.work.NetworkType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreSyncWorkTest {
    @Test fun requestRequiresNetworkAndUsesCoreRecommendedInitialDelay() {
        val request = coreSyncWorkRequest(60000)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(60000L, request.workSpec.initialDelay)
        assertTrue(CoreSyncWorkScheduler.WORK_NAME in request.tags)
    }
    @Test fun immediateRecommendationDoesNotCreateBusyRetrySpin() { assertEquals(1000L, coreSyncWorkRequest(0).workSpec.initialDelay) }
    @Test fun eachEnqueueHasDistinctRequestIdentityButOneUniqueTaskName() {
        assertNotEquals(coreSyncWorkRequest(30000).id, coreSyncWorkRequest(30000).id)
        assertEquals(setOf(CoreSyncWorkScheduler.WORK_NAME, CoreSyncWorker::class.java.name), coreSyncWorkRequest(30000).tags)
    }
}
