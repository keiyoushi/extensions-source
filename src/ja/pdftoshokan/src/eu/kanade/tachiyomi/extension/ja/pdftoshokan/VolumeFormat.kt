package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import keiyoushi.utils.inflate
import keiyoushi.utils.readIntBigEndian
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.io.RandomAccessFile

internal class Slice(val offset: Long, val length: Int)

/** Each page is a list of image slices; legacy PDF pages are split into strips stacked top to bottom. */
internal class VolumeIndex(val isAvif: Boolean, val pages: List<List<Slice>>)

/** Volume files: a "CUSTOM_A" container of AVIF images, or an older image-only PDF whose pages are JPEG strips. */
internal object VolumeFormat {

    // "CUSTOM_A" container: 16-byte magic, page count, then per page width, height and length before the image
    private const val AVIF_MAGIC = 'C'.code.toByte()
    private const val AVIF_COUNT_OFFSET = 16L
    private const val AVIF_ENTRY_HEADER = 12L
    private const val HEADER_SIZE = AVIF_COUNT_OFFSET + 4

    val AVIF = "image/avif".toMediaType()
    val JPEG = "image/jpeg".toMediaType()

    fun index(raf: RandomAccessFile): VolumeIndex = if (raf.isAvifContainer()) indexAvif(raf) else indexPdf(raf)

    // The streamed reads below take the download's `await`: true once that many bytes are in, false once the whole file
    // has moved into place instead, when the stored file is read. A PDF needs the whole file anyway.

    suspend fun streamedPageCount(raf: RandomAccessFile, await: suspend (Long) -> Boolean): Int? {
        if (!await(HEADER_SIZE) || !raf.isAvifContainer()) return null
        return raf.pageCount()
    }

    suspend fun streamedPage(raf: RandomAccessFile, page: Int, await: suspend (Long) -> Boolean): Slice? {
        if (!await(HEADER_SIZE) || !raf.isAvifContainer()) return null
        var entry = HEADER_SIZE
        repeat(page) {
            if (!await(imageOffset(entry))) return null
            entry = imageOffset(entry) + raf.imageLength(entry)
        }
        if (!await(imageOffset(entry))) return null
        val length = raf.imageLength(entry)
        if (!await(imageOffset(entry) + length)) return null
        return Slice(imageOffset(entry), length)
    }

    private fun indexAvif(raf: RandomAccessFile): VolumeIndex {
        var entry = HEADER_SIZE
        val pages = List(raf.pageCount()) {
            val length = raf.imageLength(entry)
            listOf(Slice(imageOffset(entry), length)).also { entry = imageOffset(entry) + length }
        }
        return VolumeIndex(isAvif = true, pages)
    }

    private fun RandomAccessFile.isAvifContainer() = readBytes(0, 1).first() == AVIF_MAGIC

    private fun RandomAccessFile.pageCount() = readBytes(AVIF_COUNT_OFFSET, 4).readIntBigEndian(0)

    private fun RandomAccessFile.imageLength(entry: Long) = readBytes(entry + 8, 4).readIntBigEndian(0)

    private fun imageOffset(entry: Long) = entry + AVIF_ENTRY_HEADER

    private class PdfObject(val dict: String, val streamOffset: Long, val streamLength: Int)

    private fun indexPdf(raf: RandomAccessFile): VolumeIndex {
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
            if (match == null) break
            if (end < 0) throw IOException("Unreadable PDF object at $pos")

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

internal fun RandomAccessFile.readBytes(offset: Long, length: Int): ByteArray {
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
