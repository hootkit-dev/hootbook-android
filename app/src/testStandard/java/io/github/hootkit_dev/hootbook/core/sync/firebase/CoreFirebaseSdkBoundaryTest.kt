package io.github.hootkit_dev.hootbook.core.sync.firebase

import android.app.Application
import android.os.Looper
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.FirebaseFirestoreException
import dev.hootkit.core.PlatformException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class CoreFirebaseSdkBoundaryTest {
    @Test fun wrappedStaleAndCryptoChangedSurviveFirestoreUnknownWrapperExactly() {
        for (error in listOf(PlatformException.Stale(), PlatformException.CryptoChanged())) {
            val wrapper = FirebaseFirestoreException("private remote details", FirebaseFirestoreException.Code.UNKNOWN, error)
            assertSame(error, coreFirebaseFailure(RuntimeException(wrapper)))
        }
    }
    @Test fun sdkPermissionNetworkNotFoundAndOtherCodesMapWithoutRawDetails() {
        assertTrue(coreFirebaseFailure(FirebaseFirestoreException("private", FirebaseFirestoreException.Code.PERMISSION_DENIED)) is PlatformException.Unauthorized)
        assertTrue(coreFirebaseFailure(FirebaseFirestoreException("private", FirebaseFirestoreException.Code.UNAVAILABLE)) is PlatformException.Network)
        assertTrue(coreFirebaseFailure(FirebaseFirestoreException("private", FirebaseFirestoreException.Code.NOT_FOUND)) is PlatformException.NotFound)
        val other = coreFirebaseFailure(FirebaseFirestoreException("private payload", FirebaseFirestoreException.Code.FAILED_PRECONDITION)) as PlatformException.Other
        assertFalse(other.detail.contains("private")); assertTrue(coreFirebaseFailure(IllegalStateException("Bearer private")) is PlatformException.Network)
    }
    @Test fun taskSuccessIsAwaitedWithoutAccessingAnyFirebaseAccountOrNetwork() = runTest {
        val task = TaskCompletionSource<String>(); val waiting = async { task.task.awaitCoreFirebase() }; runCurrent()
        task.setResult("result"); Shadows.shadowOf(Looper.getMainLooper()).idle(); runCurrent(); assertEquals("result", waiting.await())
    }
    @Test fun taskFailurePropagatesToSanitizingBoundaryAndCancellationDiscardsLateResult() = runTest {
        val failed = TaskCompletionSource<String>(); val result = async { runCatching { failed.task.awaitCoreFirebase() } }; runCurrent()
        val error = IllegalStateException("private credential"); failed.setException(error)
        assertSame(error, failed.task.exception)
        Shadows.shadowOf(Looper.getMainLooper()).idle(); runCurrent()
        val delivered = checkNotNull(result.await().exceptionOrNull())
        assertTrue(delivered is IllegalStateException); assertEquals(error.message, delivered.message)
        // Coroutine stack-trace recovery may clone the Throwable, but preserves original cause.
        assertTrue(generateSequence(delivered) { it.cause }.take(8).any { it === error })
        val mapped = coreFirebaseFailure(delivered) as PlatformException.Network
        assertFalse(mapped.detail.contains("private credential"))
        val late = TaskCompletionSource<String>(); val waiting = async { late.task.awaitCoreFirebase() }; runCurrent(); waiting.cancel()
        late.setResult("late private result"); Shadows.shadowOf(Looper.getMainLooper()).idle(); runCurrent(); assertTrue(waiting.isCancelled)
    }
}
