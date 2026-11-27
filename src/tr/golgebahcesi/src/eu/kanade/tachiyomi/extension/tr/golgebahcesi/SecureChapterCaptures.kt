package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

internal typealias SecureReader = suspend (onCount: (Int) -> Unit, onPage: (Int, String) -> Unit) -> Unit

internal class SecureChapterCaptures(
    root: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val directory by lazy {
        // A process restart cannot resume an earlier WebView.
        root.deleteRecursively()
        root
    }
    private val captures = LinkedHashMap<String, Capture>(4, 0.75f, true)
    private val webViewSlots = Semaphore(2)

    fun get(path: String, reader: SecureReader): Capture = get(path, null, reader)

    suspend fun readPage(path: String, index: Int, reader: SecureReader): String {
        val capture = get(path, index, reader)
        capture.read(index)?.let { return it }
        // Android may reclaim cache files, including while a page is queued.
        synchronized(captures) {
            if (captures[path] === capture) remove(path)
        }
        return get(path, index, reader).read(index) ?: throw IOException("The reader cache was cleared. Try again.")
    }

    private fun get(path: String, index: Int?, reader: SecureReader): Capture = synchronized(captures) {
        captures[path]?.let { capture ->
            if (capture.canReuse(index)) return capture
            remove(path)
        }
        val capture = Capture()
        captures[path] = capture
        scope.launch {
            try {
                webViewSlots.withPermit { capture.run(reader) }
            } catch (e: CancellationException) {
                capture.fail(e)
                throw e
            } catch (e: Exception) {
                capture.fail(e)
            } finally {
                synchronized(captures) {
                    capture.finished = true
                    if (captures[path] !== capture) {
                        delete(capture)
                    } else {
                        val finished = captures.filterValues { it.finished }.keys
                        finished.take((finished.size - MAX_CACHED_CHAPTERS).coerceAtLeast(0)).forEach(::remove)
                    }
                }
            }
            delay(10.minutes)
            synchronized(captures) {
                if (captures[path] === capture) remove(path)
            }
        }
        capture
    }

    private fun remove(path: String) {
        val capture = captures.remove(path) ?: return
        if (capture.finished) delete(capture)
    }

    private fun delete(capture: Capture) {
        scope.launch(Dispatchers.IO) { capture.deleteFiles() }
    }

    internal inner class Capture {
        private val pages = CompletableDeferred<List<CompletableDeferred<Unit>>>()
        private var folder: File? = null

        @Volatile
        private var received: List<CompletableDeferred<Unit>>? = null

        @Volatile
        var finished = false

        @Volatile
        private var failed = false

        fun canReuse(index: Int?): Boolean {
            if (!failed) return true
            val page = index?.let { received?.getOrNull(it) } ?: return false
            return page.isCompleted && !page.isCancelled
        }

        suspend fun pageCount(): Int = pages.await().size

        suspend fun read(index: Int): String? {
            val page = pages.await().getOrNull(index) ?: throw IOException("Page ${index + 1} is no longer in the chapter")
            page.await()
            return withContext(Dispatchers.IO) {
                try {
                    file(index).readText()
                } catch (_: FileNotFoundException) {
                    null
                }
            }
        }

        suspend fun run(reader: SecureReader) = coroutineScope {
            val fileWrites = Channel<Pair<Int, String>>(Channel.UNLIMITED)
            val writer = launch(Dispatchers.IO) {
                val output = File(directory, UUID.randomUUID().toString())
                check(output.mkdirs()) { "Couldn't create the reader cache" }
                folder = output
                for ((index, data) in fileWrites) {
                    file(index).writeText(data)
                    pages.await()[index].complete(Unit)
                }
            }
            try {
                reader(::setPageCount) { index, data ->
                    fileWrites.trySend(index to data).getOrThrow()
                }
            } finally {
                fileWrites.close()
            }
            writer.join()
            check(pages.isCompleted && received.orEmpty().all { it.isCompleted }) { "The reader didn't load all pages" }
        }

        private fun setPageCount(count: Int) {
            require(count > 0)
            received?.let {
                require(it.size == count)
                return
            }
            val slots = List(count) { CompletableDeferred<Unit>() }
            received = slots
            pages.complete(slots)
        }

        fun fail(error: Exception) {
            failed = true
            pages.completeExceptionally(error)
            received.orEmpty().forEach { it.completeExceptionally(error) }
        }

        fun deleteFiles() {
            folder?.deleteRecursively()
        }

        private fun file(index: Int): File = checkNotNull(folder).resolve(index.toString())
    }

    companion object {
        private const val MAX_CACHED_CHAPTERS = 3
    }
}
