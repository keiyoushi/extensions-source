package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class SecureChapterCapturesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun firstPageIsAvailableWhileTheLastPageIsStillPending() = withCaptures { captures ->
        val releaseTail = CompletableDeferred<Unit>()
        val walkCount = AtomicInteger()
        val reader: SecureReader = { count, page ->
            walkCount.incrementAndGet()
            count(199)
            page(0, "first-image")
            releaseTail.await()
            (198 downTo 1).forEach { page(it, "image-$it") }
        }

        assertEquals(199, captures.get("chapter", reader).pageCount())
        val tail = async { captures.readPage("chapter", 198, reader) }
        assertEquals("first-image", captures.readPage("chapter", 0, reader))
        assertFalse(tail.isCompleted)

        releaseTail.complete(Unit)
        assertEquals("image-198", tail.await())
        assertEquals("image-1", captures.readPage("chapter", 1, reader))
        assertEquals(1, walkCount.get())
    }

    @Test
    fun aFailedCaptureUnblocksAnImageAndTheNextRequestRetries() = withCaptures { captures ->
        val releaseFailure = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val reader: SecureReader = { count, page ->
            val attempt = calls.incrementAndGet()
            count(2)
            page(0, "first-image")
            if (attempt == 1) {
                releaseFailure.await()
                throw IOException("Reader timed out")
            }
            page(1, "second-image")
        }

        val capture = captures.get("chapter", reader)
        assertEquals(2, capture.pageCount())
        assertEquals("first-image", captures.readPage("chapter", 0, reader))
        val waiting = async { runCatching { capture.read(1) } }
        releaseFailure.complete(Unit)
        assertEquals("Reader timed out", waiting.await().exceptionOrNull()?.message)
        assertEquals("first-image", captures.readPage("chapter", 0, reader))
        assertEquals("second-image", captures.readPage("chapter", 1, reader))
        assertEquals(2, calls.get())
    }

    @Test
    fun aMissingCacheFileStartsOneNewCapture() = withCaptures { captures ->
        val calls = AtomicInteger()
        val reader: SecureReader = { count, page ->
            val attempt = calls.incrementAndGet()
            count(1)
            page(0, "image-$attempt")
        }

        assertEquals("image-1", captures.readPage("cached chapter", 0, reader))
        tmp.root.walkTopDown().filter { it.isFile }.forEach { it.delete() }
        assertEquals("image-2", captures.readPage("cached chapter", 0, reader))
        assertEquals(2, calls.get())
    }

    @Test
    fun errorBeforeCountIsReportedToThePageListCaller() = withCaptures { captures ->
        val capture = captures.get("chapter") { _, _ -> throw IOException("Chapter locked") }
        assertEquals("Chapter locked", runCatching { capture.pageCount() }.exceptionOrNull()?.message)
    }

    private fun withCaptures(run: suspend CoroutineScope.(SecureChapterCaptures) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking {
                withTimeout(10.seconds) {
                    run(SecureChapterCaptures(tmp.newFolder(), scope))
                }
            }
        } finally {
            scope.cancel()
        }
    }
}
