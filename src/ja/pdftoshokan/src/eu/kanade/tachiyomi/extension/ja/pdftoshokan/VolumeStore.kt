package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import keiyoushi.utils.inflate
import keiyoushi.utils.md5
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.File
import java.io.IOException
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

    fun index(url: HttpUrl, fetch: (HttpUrl) -> Response): Pair<File, VolumeIndex> {
        val key = url.newBuilder().fragment(null).build().toString().md5().toHexString()
        synchronized(locks.getOrPut(key) { Any() }) {
            val file = File(dir, key)
            if (!file.exists()) {
                synchronized(indexes) { indexes.remove(key) }
                download(url, file, fetch)
            }
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

    private fun download(url: HttpUrl, file: File, fetch: (HttpUrl) -> Response) {
        dir.mkdirs()
        val tmp = File(dir, "${file.name}.tmp")
        fetch(url.newBuilder().fragment(null).build()).use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            tmp.outputStream().use { response.body.byteStream().copyTo(it) }
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Failed to store volume")
        }
        trim(file)
    }

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
