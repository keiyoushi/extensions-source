package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import keiyoushi.utils.readIntBigEndian
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.io.RandomAccessFile

internal class Slice(val offset: Long, val length: Int)

/**
 * A volume file is a "CUSTOM_A" container: a 16-byte magic, the page count, then per page its width, height and length
 * before the AVIF image. Some older volumes are image-only PDFs instead, which aren't read.
 */
internal object VolumeFormat {

    private const val MAGIC = 'C'.code.toByte()
    private const val COUNT_OFFSET = 16L
    private const val HEADER_SIZE = COUNT_OFFSET + 4
    private const val ENTRY_HEADER = 12L
    private const val MAX_PAGES = 5000
    private const val BROKEN = "The volume file is broken, open the chapter again"

    val AVIF = "image/avif".toMediaType()

    fun index(raf: RandomAccessFile): List<Slice> {
        raf.checkFormat()
        var entry = HEADER_SIZE
        return List(raf.pageCount()) {
            val length = raf.imageLength(entry)
            if (imageOffset(entry) + length > raf.length()) throw IOException(BROKEN)
            Slice(imageOffset(entry), length).also { entry = imageOffset(entry) + length }
        }
    }

    // The streamed reads take the download's `await`: true once that many bytes are in, false once the whole file has
    // moved into place instead, when the stored file is read.

    suspend fun streamedPageCount(raf: RandomAccessFile, await: suspend (Long) -> Boolean): Int? {
        if (!await(HEADER_SIZE)) return null
        raf.checkFormat()
        return raf.pageCount()
    }

    suspend fun streamedPage(raf: RandomAccessFile, page: Int, await: suspend (Long) -> Boolean): Slice? {
        if (!await(HEADER_SIZE)) return null
        raf.checkFormat()
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

    private fun RandomAccessFile.checkFormat() {
        if (readBytes(0, 1).first() != MAGIC) throw IOException("This volume is in an older format the extension can't read")
    }

    // The file's own numbers aren't trusted: a broken one would otherwise run the app out of memory
    private fun RandomAccessFile.pageCount() = readBytes(COUNT_OFFSET, 4).readIntBigEndian(0)
        .also { if (it !in 1..MAX_PAGES) throw IOException(BROKEN) }

    private fun RandomAccessFile.imageLength(entry: Long) = readBytes(entry + 8, 4).readIntBigEndian(0)
        .also { if (it < 0) throw IOException(BROKEN) }

    private fun imageOffset(entry: Long) = entry + ENTRY_HEADER
}

internal fun RandomAccessFile.readBytes(offset: Long, length: Int): ByteArray {
    seek(offset)
    return ByteArray(length).also { readFully(it) }
}
