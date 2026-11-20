package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import keiyoushi.utils.md5
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/**
 * Volumes downloaded into [dir], which it owns, up to 300 MB. A volume is one file, known by an address of the
 * caller's choosing, and its pages are read out of it as `<address>#<page index>`, even while it's still downloading.
 */
internal class VolumeStore(private val dir: File) {

    private val locks = ConcurrentHashMap<String, Any>()

    private val indexes = object : LinkedHashMap<String, VolumeIndex>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VolumeIndex>) = size > 4
    }

    /** A volume still downloading into [tmp]; [size] is how far it has got, or [DONE] / [FAILED]. */
    internal class Partial(val tmp: File) {
        val size = MutableStateFlow(0L)

        @Volatile
        var error: Throwable? = null

        /** Waits for [bytes] of the file; false when it's complete and in place instead. */
        suspend fun await(bytes: Long): Boolean {
            val reached = size.first { it >= bytes || it == FAILED }
            if (reached == FAILED) throw error!!
            return reached != DONE
        }
    }

    private val partials = ConcurrentHashMap<String, Partial>()

    // Why a volume's last download failed, for pages asked for after it's gone
    private val failures = ConcurrentHashMap<String, Throwable>()

    /** A volume stored or on its way. */
    inner class Volume internal constructor(private val address: HttpUrl, private val partial: Partial?) {

        /** Waits until the first bytes are in, rethrowing the download's failure. */
        suspend fun awaitStarted() {
            partial?.await(1)
        }

        /** The addresses of the pages, served by [PageInterceptor]; for a PDF, once it's all in. */
        suspend fun pageUrls(): List<String> = List(pageCount()) { "$address#$it" }

        /** From the header of an AVIF volume as soon as it's in, for a PDF once it's all there. */
        private suspend fun pageCount(): Int {
            if (partial != null) {
                val streamed = try {
                    withContext(Dispatchers.IO) {
                        RandomAccessFile(partial.tmp, "r").use { VolumeFormat.streamedPageCount(it, partial::await) }
                    }
                } catch (_: FileNotFoundException) {
                    null // finished in the meantime
                }
                streamed?.let { return it }
                partial.await(DONE)
            }
            return withContext(Dispatchers.IO) { index(address).second.pages.size }
        }
    }

    init {
        // Downloads cut short by the app closing; none can be running yet
        dir.listFiles { file -> file.name.endsWith(".tmp") }?.forEach(File::delete)
    }

    /** The volume, when it's stored or on its way. */
    fun find(address: HttpUrl): Volume? {
        val key = key(address)
        partials[key]?.let { return Volume(address, it) }
        return if (File(dir, key).exists()) Volume(address, null) else null
    }

    /** Starts downloading the volume in [scope], unless it's stored or already on its way. */
    fun download(address: HttpUrl, scope: CoroutineScope, write: suspend (OutputStream) -> Unit): Volume {
        find(address)?.let { return it }
        val key = key(address)
        val stored = File(dir, key)
        val partial = Partial(File(dir, "$key.tmp"))
        partials.putIfAbsent(key, partial)?.let { return Volume(address, it) }
        failures.remove(key)
        scope.launch {
            try {
                dir.mkdirs()
                Counting(FileOutputStream(partial.tmp), partial.size).use { write(it) }
                synchronized(lock(key)) {
                    synchronized(indexes) { indexes.remove(key) }
                    if (!partial.tmp.renameTo(stored)) throw IOException("Failed to store volume")
                }
                partial.size.value = DONE
                trim(stored)
            } catch (e: Throwable) {
                partial.tmp.delete()
                partial.error = e
                failures[key] = e
                partial.size.value = FAILED
                if (e is CancellationException) throw e
            } finally {
                partials.remove(key, partial)
            }
        }
        return Volume(address, partial)
    }

    /**
     * The page a `<address>#<page index>` points to, waiting for it while the volume is still downloading unless
     * [isCanceled]. Null for a URL that isn't a page address.
     */
    fun page(url: HttpUrl, isCanceled: () -> Boolean): PageImage? {
        val page = url.fragment?.toIntOrNull() ?: return null
        partials[key(url)]?.let { partial ->
            runBlocking { partial.streamedPage(page, isCanceled) }?.let { return it }
        }
        val (file, index) = index(url)
        val slices = try {
            RandomAccessFile(file, "r").use { raf -> index.pages[page].map { raf.readBytes(it.offset, it.length) } }
        } catch (_: FileNotFoundException) {
            throw IOException(NOT_STORED) // trimmed just now
        }
        return VolumeFormat.page(index, slices)
    }

    private suspend fun Partial.streamedPage(page: Int, isCanceled: () -> Boolean): PageImage? = try {
        // An open file keeps working when the finished download is renamed
        RandomAccessFile(tmp, "r").use { raf ->
            withContext(Dispatchers.IO) {
                val read = async { VolumeFormat.streamedPage(raf, page, ::await) }
                while (!read.isCompleted) {
                    if (isCanceled()) {
                        read.cancel()
                        throw IOException("Canceled")
                    }
                    delay(250)
                }
                read.await()
            }
        }
    } catch (_: FileNotFoundException) {
        null
    }

    private class Counting(private val out: OutputStream, private val size: MutableStateFlow<Long>) : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            size.value += len
        }

        override fun close() = out.close()
    }

    /** The stored file and its index. Reading a volume keeps it from being trimmed for a while. */
    private fun index(url: HttpUrl): Pair<File, VolumeIndex> {
        val key = key(url)
        synchronized(lock(key)) {
            val file = File(dir, key)
            if (!file.exists()) {
                throw when (val failure = failures[key]) {
                    is IOException -> failure
                    null -> IOException(NOT_STORED)
                    else -> IOException(failure.message, failure)
                }
            }
            file.setLastModified(System.currentTimeMillis())
            synchronized(indexes) { indexes[key] }?.let { return file to it }

            val index = try {
                RandomAccessFile(file, "r").use(VolumeFormat::index).also { check(it.pages.isNotEmpty()) { "No pages" } }
            } catch (e: Exception) {
                // Something other than a volume, such as an error page, would otherwise stay stored as an empty one
                file.delete()
                throw IOException("Unsupported volume file", e)
            }
            synchronized(indexes) { indexes[key] = index }
            return file to index
        }
    }

    private fun lock(key: String) = locks.getOrPut(key) { Any() }

    private fun key(url: HttpUrl) = url.newBuilder().fragment(null).build().toString().md5().toHexString()

    /** Keeps the cache under its cap, oldest read first, sparing volumes read lately: someone may be reading them. */
    private fun trim(keep: File) {
        val recent = System.currentTimeMillis() - IN_USE.inWholeMilliseconds
        val files = dir.listFiles()?.filter { it != keep && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() } + keep.length()
        for (f in files.filter { it.lastModified() < recent }.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private companion object {
        const val MAX_CACHE_BYTES = 300L * 1024 * 1024
        val IN_USE = 10.minutes
        const val DONE = Long.MAX_VALUE
        const val FAILED = -1L
        const val NOT_STORED = "Volume is no longer cached, open the chapter again"
    }
}
