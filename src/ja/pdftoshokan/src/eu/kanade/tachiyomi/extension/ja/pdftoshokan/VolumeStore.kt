package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import keiyoushi.utils.inflate
import keiyoushi.utils.md5
import keiyoushi.utils.readIntBigEndian
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

class Slice(val offset: Long, val length: Int)

/** Each page is a list of image slices; legacy PDF pages are split into strips stacked top to bottom. */
class VolumeIndex(val isAvif: Boolean, val pages: List<List<Slice>>)

/**
 * Volumes are served as one big file, either a custom container of AVIF images
 * ("CUSTOM_A" magic) or an older image-only PDF (sometimes with its header stripped).
 * The whole file is downloaded once and individual pages are sliced out of it.
 */
class VolumeStore(private val dir: File) {

    private val locks = ConcurrentHashMap<String, Any>()

    private val indexes = object : LinkedHashMap<String, VolumeIndex>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VolumeIndex>) = size > 4
    }

    /** A volume still downloading into [tmp]; [size] is how far it has got, or [DONE] / [FAILED]. */
    private class Partial(val tmp: File) {
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

    /** Whether the volume is fully downloaded. */
    fun contains(url: HttpUrl) = File(dir, key(url)).exists()

    /** Starts downloading the volume in [scope], unless it's cached or already on its way. */
    fun download(url: HttpUrl, scope: CoroutineScope, write: suspend (OutputStream) -> Unit) {
        val key = key(url)
        val file = File(dir, key)
        if (file.exists()) return
        val partial = Partial(File(dir, "$key.tmp"))
        if (partials.putIfAbsent(key, partial) != null) return
        dir.mkdirs()
        scope.launch {
            try {
                Counting(FileOutputStream(partial.tmp), partial.size).use { write(it) }
                synchronized(locks.getOrPut(key) { Any() }) {
                    synchronized(indexes) { indexes.remove(key) }
                    if (!partial.tmp.renameTo(file)) throw IOException("Failed to store volume")
                }
                partial.size.value = DONE
                trim(file)
            } catch (e: Throwable) {
                partial.tmp.delete()
                partial.error = e
                partial.size.value = FAILED
                if (e is CancellationException) throw e
            } finally {
                partials.remove(key, partial)
            }
        }
    }

    /** The volume's page count: from the header of an AVIF volume as soon as it's in, for a PDF once it's all there. */
    suspend fun pageCount(url: HttpUrl): Int {
        partials[key(url)]?.let { partial ->
            if (partial.await(AVIF_HEADER)) {
                val head = try {
                    RandomAccessFile(partial.tmp, "r").use { it.readBytes(0, AVIF_HEADER.toInt()) }
                } catch (_: FileNotFoundException) {
                    null // finished in the meantime
                }
                if (head != null && head[0] == 'C'.code.toByte()) {
                    return head.readIntBigEndian(16)
                }
                partial.await(DONE)
            }
        }
        return withContext(Dispatchers.IO) { index(url).second.pages.size }
    }

    /** Whether the page is AVIF, and its image slices; waits for the page if the volume is still downloading. */
    fun page(url: HttpUrl, page: Int): Pair<Boolean, List<ByteArray>> {
        partials[key(url)]?.let { partial ->
            runBlocking { partial.avifPage(page) }?.let { return true to listOf(it) }
        }
        val (file, index) = index(url)
        val parts = RandomAccessFile(file, "r").use { raf ->
            index.pages[page].map { raf.readBytes(it.offset, it.length) }
        }
        return index.isAvif to parts
    }

    /** Walks the AVIF entries as they arrive: each is width, height and length, then the image. */
    private suspend fun Partial.avifPage(page: Int): ByteArray? = try {
        if (!await(AVIF_HEADER)) return null
        // An open file keeps working when the finished download is renamed
        RandomAccessFile(tmp, "r").use { raf ->
            if (raf.readBytes(0, 1)[0] != 'C'.code.toByte()) return null
            var entry = AVIF_HEADER
            repeat(page + 1) { i ->
                if (!await(entry + 12)) return null
                val length = raf.readBytes(entry + 8, 4).readIntBigEndian(0)
                if (i == page) {
                    if (!await(entry + 12 + length)) return null
                    return raf.readBytes(entry + 12, length)
                }
                entry += 12 + length
            }
            null
        }
    } catch (_: FileNotFoundException) {
        null
    } catch (e: IOException) {
        throw e
    } catch (e: Throwable) {
        throw IOException(e.message, e)
    }

    private class Counting(private val out: OutputStream, private val size: MutableStateFlow<Long>) : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            size.value += len
        }

        override fun close() = out.close()
    }

    fun index(url: HttpUrl): Pair<File, VolumeIndex> {
        val key = key(url)
        synchronized(locks.getOrPut(key) { Any() }) {
            val file = File(dir, key)
            if (!file.exists()) throw IOException("Volume is no longer cached, open the chapter again")
            file.setLastModified(System.currentTimeMillis())
            synchronized(indexes) { indexes[key] }?.let { return file to it }

            val index = try {
                RandomAccessFile(file, "r").use(::parse)
            } catch (e: Exception) {
                file.delete()
                throw IOException("Unsupported volume file", e)
            }
            synchronized(indexes) { indexes[key] = index }
            return file to index
        }
    }

    private fun key(url: HttpUrl) = url.newBuilder().fragment(null).build().toString().md5().toHexString()

    private fun trim(keep: File) {
        val files = dir.listFiles()?.filter { it != keep && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() } + keep.length()
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private fun parse(raf: RandomAccessFile): VolumeIndex {
        val head = ByteArray(16)
        raf.seek(0)
        raf.readFully(head)
        return if (head[0] == 'C'.code.toByte()) parseAvif(raf) else parsePdf(raf)
    }

    private fun parseAvif(raf: RandomAccessFile): VolumeIndex {
        raf.seek(16)
        val count = raf.readInt()
        val pages = List(count) {
            raf.readInt() // width
            raf.readInt() // height
            val length = raf.readInt()
            val slice = Slice(raf.filePointer, length)
            raf.seek(raf.filePointer + length)
            listOf(slice)
        }
        return VolumeIndex(isAvif = true, pages)
    }

    private class PdfObject(val dict: String, val streamOffset: Long, val streamLength: Int)

    private fun parsePdf(raf: RandomAccessFile): VolumeIndex {
        val objects = HashMap<Int, PdfObject>()
        val order = ArrayList<Int>()
        val size = raf.length()
        var pos = 0L

        while (pos < size) {
            var window = 4096
            var text: String
            var match: MatchResult?
            var end: Int
            while (true) {
                text = raf.readString(pos, window)
                match = OBJ_REGEX.find(text)
                end = match?.let { m -> text.indexOfAny(listOf("stream", "endobj"), m.range.last) } ?: -1
                if (match == null || end >= 0 || pos + window >= size || window >= 1 shl 20) break
                window *= 4
            }
            if (match == null || end < 0) break

            val num = match.groupValues[1].toInt()
            val bodyStart = match.range.last + 1
            val dict = text.substring(bodyStart, end)
            if (text.startsWith("stream", end)) {
                var dataStart = pos + end + "stream".length
                if (raf.byteAt(dataStart) == '\r'.code) dataStart++
                if (raf.byteAt(dataStart) == '\n'.code) dataStart++
                val length = LENGTH_REGEX.find(dict)?.groupValues?.get(1)?.toInt()
                    ?: raf.indexOf("endstream", dataStart).let { (it - dataStart).toInt() }
                objects[num] = PdfObject(dict, dataStart, length)
                val after = dataStart + length
                pos = raf.indexOf("endobj", after) + "endobj".length
            } else {
                objects[num] = PdfObject(dict, -1, 0)
                pos += end + "endobj".length
            }
            order += num
        }

        val pageNumbers = objects.values.firstOrNull { PAGES_REGEX.containsMatchIn(it.dict) }
            ?.let { KIDS_REGEX.find(it.dict) }
            ?.let { REF_REGEX.findAll(it.groupValues[1]).map { r -> r.groupValues[1].toInt() }.toList() }
            ?: order.filter { PAGE_REGEX.containsMatchIn(objects[it]!!.dict) }.sorted()

        val pages = pageNumbers.mapNotNull { pageNumber ->
            val page = objects[pageNumber] ?: return@mapNotNull null
            val xObjectDict = XOBJECT_REGEX.find(page.dict)?.let { m ->
                m.groupValues[2].ifEmpty { objects[m.groupValues[3].toInt()]?.dict.orEmpty() }
            }.orEmpty()
            val images = NAMED_REF_REGEX.findAll(xObjectDict)
                .associate { it.groupValues[1] to it.groupValues[2].toInt() }
                .filterValues { objects[it]?.dict?.contains("/Image") == true }

            val drawOrder = CONTENTS_REGEX.find(page.dict)
                ?.let { objects[it.groupValues[1].toInt()] }
                ?.let { content ->
                    val bytes = raf.readBytes(content.streamOffset, content.streamLength)
                    val ops = if (content.dict.contains("/FlateDecode")) bytes.inflate() else bytes
                    DRAW_REGEX.findAll(String(ops, Charsets.ISO_8859_1))
                        .map { it.groupValues[2] to it.groupValues[1].toFloat() }
                        .sortedByDescending { it.second } // PDF y axis points up
                        .mapNotNull { images[it.first] }
                        .toList()
                }
                ?.takeIf { it.isNotEmpty() }
                ?: images.values.toList()

            drawOrder.map { objects[it]!!.let { img -> Slice(img.streamOffset, img.streamLength) } }
                .takeIf { it.isNotEmpty() }
        }
        return VolumeIndex(isAvif = false, pages)
    }

    private fun RandomAccessFile.readBytes(offset: Long, length: Int): ByteArray {
        seek(offset)
        return ByteArray(length).also { readFully(it) }
    }

    private fun RandomAccessFile.readString(offset: Long, max: Int): String {
        val length = minOf(max.toLong(), length() - offset).toInt()
        return String(readBytes(offset, length), Charsets.ISO_8859_1)
    }

    private fun RandomAccessFile.byteAt(offset: Long): Int {
        seek(offset)
        return read()
    }

    private fun RandomAccessFile.indexOf(token: String, from: Long): Long {
        var pos = from
        while (pos < length()) {
            val chunk = readString(pos, 65536)
            val i = chunk.indexOf(token)
            if (i >= 0) return pos + i
            if (chunk.length < 65536) break
            pos += chunk.length - token.length
        }
        throw IOException("Missing $token")
    }

    companion object {
        private const val MAX_CACHE_BYTES = 300L * 1024 * 1024
        private const val AVIF_HEADER = 20L
        private const val DONE = Long.MAX_VALUE
        private const val FAILED = -1L

        private val OBJ_REGEX = Regex("""(\d+)\s+0\s+obj""")
        private val LENGTH_REGEX = Regex("""/Length\s+(\d+)\b(?!\s+\d+\s+R)""")
        private val PAGES_REGEX = Regex("""/Type\s*/Pages\b""")
        private val PAGE_REGEX = Regex("""/Type\s*/Page(?![a-zA-Z])""")
        private val KIDS_REGEX = Regex("""/Kids\s*\[([^\]]*)]""")
        private val REF_REGEX = Regex("""(\d+)\s+0\s+R""")
        private val XOBJECT_REGEX = Regex("""/XObject\s*(<<(.*?)>>|(\d+)\s+0\s+R)""", RegexOption.DOT_MATCHES_ALL)
        private val NAMED_REF_REGEX = Regex("""/([^\s/<>\[\]]+)\s+(\d+)\s+0\s+R""")
        private val CONTENTS_REGEX = Regex("""/Contents\s+(\d+)\s+0\s+R""")
        private val DRAW_REGEX = Regex(
            """[-\d.]+\s+[-\d.]+\s+[-\d.]+\s+[-\d.]+\s+[-\d.]+\s+([-\d.]+)\s+cm\s*/([^\s/]+)\s+Do""",
        )
    }
}
