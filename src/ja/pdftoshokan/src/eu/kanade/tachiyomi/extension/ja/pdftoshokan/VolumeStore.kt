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
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okio.source
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/** A page's AVIF image, read straight off disk. */
internal class PageImage(val image: BufferedSource, val length: Long)

/** Volumes in [dir], up to 300 MB, keyed by an address of the caller's; pages are `<address>#<page index>`. */
internal class VolumeStore(private val dir: File) {

    private val lock = Any()

    private val indexes = lru<List<Slice>>(4)

    private val failures = lru<Throwable>(16)

    /** A volume still downloading into [tmp]; [size] is how far it has got, or [DONE] / [FAILED]. */
    class PartialDownload(val tmp: File) {
        val size = MutableStateFlow(0L)

        @Volatile
        var error: Throwable? = null

        /** Waits for [bytes] of the file; false once it's complete and moved into place instead. */
        suspend fun await(bytes: Long): Boolean {
            val reached = size.first { it >= bytes || it == FAILED }
            if (reached == FAILED) throw error!!
            return reached != DONE
        }
    }

    private val partials = ConcurrentHashMap<String, PartialDownload>()

    /** A volume stored or on its way. */
    inner class Volume(private val address: HttpUrl, private val partial: PartialDownload?) {

        /** Rethrows the download's failure. */
        suspend fun awaitStarted() {
            partial?.await(1)
        }

        suspend fun pageUrls(): List<String> = List(pageCount()) { "$address#$it" }

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
            return withContext(Dispatchers.IO) { index(address).second.size }
        }
    }

    init {
        // Downloads cut short by the app closing; none can be running yet
        dir.listFiles { file -> file.name.endsWith(".tmp") }?.forEach(File::delete)
    }

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
        val partial = PartialDownload(File(dir, "$key.tmp"))
        partials.putIfAbsent(key, partial)?.let { return Volume(address, it) }
        synchronized(failures) { failures.remove(key) }
        scope.launch {
            try {
                dir.mkdirs()
                CountingOutputStream(FileOutputStream(partial.tmp), partial.size).use { write(it) }
                synchronized(lock) {
                    indexes.remove(key)
                    if (!partial.tmp.renameTo(stored)) throw IOException("Failed to store volume")
                }
                partial.size.value = DONE
                trim(stored)
            } catch (e: Throwable) {
                partial.tmp.delete()
                partial.error = e
                synchronized(failures) { failures[key] = e }
                partial.size.value = FAILED
                if (e is CancellationException) throw e
            } finally {
                partials.remove(key, partial)
            }
        }
        return Volume(address, partial)
    }

    /** Waits for the page while the volume is still downloading, unless [isCanceled]. Null for other URLs. */
    fun page(url: HttpUrl, isCanceled: () -> Boolean): PageImage? {
        val page = url.fragment?.toIntOrNull() ?: return null
        partials[key(url)]?.let { partial ->
            runBlocking { partial.streamedSlice(page, isCanceled) }?.let { slice ->
                try {
                    return PageImage(partial.tmp.open(slice), slice.length.toLong())
                } catch (_: FileNotFoundException) {
                    // Moved into place in the meantime
                }
            }
        }
        val (file, slices) = index(url)
        val slice = slices[page]
        return try {
            PageImage(file.open(slice), slice.length.toLong())
        } catch (_: FileNotFoundException) {
            throw IOException(NOT_STORED) // trimmed just now
        }
    }

    private suspend fun PartialDownload.streamedSlice(page: Int, isCanceled: () -> Boolean): Slice? = try {
        // An open file keeps working when the finished download is moved
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

    private class CountingOutputStream(private val out: OutputStream, private val size: MutableStateFlow<Long>) : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            size.value += len
        }

        override fun close() = out.close()
    }

    /** Reading a volume keeps it from being trimmed for a while. */
    private fun index(url: HttpUrl): Pair<File, List<Slice>> {
        val key = key(url)
        synchronized(lock) {
            val file = File(dir, key)
            if (!file.exists()) {
                throw when (val failure = synchronized(failures) { failures[key] }) {
                    is IOException -> failure
                    null -> IOException(NOT_STORED)
                    else -> IOException(failure.message, failure)
                }
            }
            file.setLastModified(System.currentTimeMillis())
            indexes[key]?.let { return file to it }

            val index = try {
                RandomAccessFile(file, "r").use(VolumeFormat::index).also { check(it.isNotEmpty()) { "No pages" } }
            } catch (e: Exception) {
                // Something other than a volume, such as an error page, would otherwise stay stored as an empty one
                file.delete()
                throw e as? IOException ?: IOException("Unsupported volume file", e)
            }
            indexes[key] = index
            return file to index
        }
    }

    private fun key(url: HttpUrl) = url.newBuilder().fragment(null).build().toString().md5().toHexString()

    /** Oldest read first, sparing volumes read lately: someone may be reading them. */
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

    private class LimitedSource(delegate: Source, private var remaining: Long) : ForwardingSource(delegate) {
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (remaining == 0L) return -1
            val read = super.read(sink, minOf(byteCount, remaining))
            if (read > 0) remaining -= read
            return read
        }
    }

    private companion object {
        const val MAX_CACHE_BYTES = 300L * 1024 * 1024
        val IN_USE = 10.minutes
        const val DONE = Long.MAX_VALUE
        const val FAILED = -1L
        const val NOT_STORED = "Volume is no longer cached, open the chapter again"

        fun <T> lru(capacity: Int) = object : LinkedHashMap<String, T>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>) = size > capacity
        }

        // The open file keeps working if it's moved or deleted meanwhile
        fun File.open(slice: Slice): BufferedSource {
            val input = FileInputStream(this)
            try {
                input.channel.position(slice.offset)
            } catch (e: Throwable) {
                input.close()
                throw e
            }
            return LimitedSource(input.source(), slice.length.toLong()).buffer()
        }
    }
}
