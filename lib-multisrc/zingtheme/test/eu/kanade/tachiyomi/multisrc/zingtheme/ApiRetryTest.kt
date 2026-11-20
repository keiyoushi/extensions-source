package eu.kanade.tachiyomi.multisrc.zingtheme

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class ApiRetryTest {
    @Test
    fun cancellationDoesNotInvalidateOrReloadNonce() = runBlocking {
        val cancellation = CancellationException("cancelled API")
        var loads = 0
        var invalidations = 0
        var requests = 0
        try {
            retryZing("decode_images", {
                loads++
                "nonce"
            }, { invalidations++ }) {
                requests++
                if (requests == 1) throw cancellation
                "unexpected recovery"
            }
            throw AssertionError("API cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(1, loads)
        assertEquals(1, requests)
        assertEquals(0, invalidations)
    }

    @Test
    fun retryRequestsUncachedConfiguration() = runBlocking {
        val refreshes = mutableListOf<Boolean>()
        var requests = 0
        val result = retryZing("decode_images", { refresh ->
            refreshes += refresh
            "nonce-${refreshes.size}"
        }, {}) { nonce ->
            requests++
            if (requests == 1) throw IOException("stale nonce")
            nonce
        }
        assertEquals("nonce-2", result)
        assertEquals(listOf(false, true), refreshes)
        assertEquals(2, requests)
    }

    @Test
    fun aFailedRetryKeepsItsCause() = runBlocking {
        val failure = IOException("server failure")
        var requests = 0
        try {
            retryZing("loadmore_tr", { "nonce" }, {}) {
                requests++
                throw failure
            }
            throw AssertionError("A failed retry must be reported")
        } catch (actual: Exception) {
            assertSame(failure, actual.cause)
        }
        assertEquals(2, requests)
    }

    @Test
    fun cancellationOnTheSecondApiAttemptIsNotWrapped() = runBlocking {
        val cancellation = CancellationException("cancelled retry")
        var requests = 0
        var invalidations = 0
        try {
            retryZing("decode_images", { "nonce" }, { invalidations++ }) {
                requests++
                if (requests == 1) throw IOException("stale nonce")
                throw cancellation
            }
            throw AssertionError("The retry cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        assertEquals(2, requests)
        assertEquals(1, invalidations)
    }

    @Test
    fun cancellingTheParentDoesNotStartRecovery() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var loads = 0
        var invalidations = 0
        val worker = launch {
            retryZing("decode_images", {
                loads++
                "nonce"
            }, { invalidations++ }) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        worker.cancel()
        worker.join()
        assertEquals(1, loads)
        assertEquals(0, invalidations)
    }

    @Test
    fun aTimeoutDoesNotRefreshConfiguration() = runBlocking {
        var loads = 0
        var invalidations = 0
        try {
            withTimeout(50) {
                retryZing("decode_images", {
                    loads++
                    "nonce"
                }, { invalidations++ }) { awaitCancellation() }
            }
            throw AssertionError("The timeout must propagate")
        } catch (_: CancellationException) {
            // Timeout is a cancellation; only real API failures should refresh the nonce.
        }
        assertEquals(1, loads)
        assertEquals(0, invalidations)
    }
}
