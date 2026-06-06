package eu.kanade.tachiyomi.extension.ja.mechacomi

import okio.Buffer
import okio.BufferedSource
import okio.InflaterSource
import java.util.zip.Inflater

object Utils {
    private const val EOCD_SIG = 0x06054b50L
    private const val CDH_SIG = 0x02014b50L
    private const val LFH_SIG = 0x04034b50L

    private const val EOCD_MIN_LEN = 22
    private const val LFH_MIN_LEN = 30

    const val MAX_EOCD_SEARCH = EOCD_MIN_LEN + 0xFFFF + 1 // 65558

    const val METHOD_DEFLATE = 8

    class Entry(
        val name: String,
        val method: Int,
        val compressedSize: Long,
        val localHeaderOffset: Long,
    )

    class Eocd(
        val cdOffset: Long,
        val cdSize: Long,
        @Suppress("unused") val totalEntries: Int,
    )

    private fun u16(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, off: Int): Long = (b[off].toLong() and 0xFF) or
        ((b[off + 1].toLong() and 0xFF) shl 8) or
        ((b[off + 2].toLong() and 0xFF) shl 16) or
        ((b[off + 3].toLong() and 0xFF) shl 24)

    fun findEocd(tail: ByteArray): Eocd {
        var i = tail.size - EOCD_MIN_LEN
        while (i >= 0) {
            if (u32(tail, i) == EOCD_SIG) {
                return Eocd(
                    cdOffset = u32(tail, i + 16),
                    cdSize = u32(tail, i + 12),
                    totalEntries = u16(tail, i + 10),
                )
            }
            i--
        }
        throw Exception("EOCD record not found")
    }

    fun parseCentralDirectory(cd: ByteArray): List<Entry> {
        val entries = ArrayList<Entry>()
        var p = 0
        while (p + 46 <= cd.size && u32(cd, p) == CDH_SIG) {
            val method = u16(cd, p + 10)
            val compressedSize = u32(cd, p + 20)
            val nameLen = u16(cd, p + 28)
            val extraLen = u16(cd, p + 30)
            val commentLen = u16(cd, p + 32)
            val localOffset = u32(cd, p + 42)
            val name = String(cd, p + 46, nameLen, Charsets.UTF_8)
            entries.add(Entry(name, method, compressedSize, localOffset))
            p += 46 + nameLen + extraLen + commentLen
        }
        return entries
    }

    fun skipLocalHeader(source: BufferedSource) {
        val header = source.readByteArray(LFH_MIN_LEN.toLong())
        require(u32(header, 0) == LFH_SIG) { "Not a local file header" }
        source.skip((u16(header, 26) + u16(header, 28)).toLong())
    }

    fun inflate(source: BufferedSource): Buffer = InflaterSource(source, Inflater()).use {
        Buffer().apply { writeAll(it) }
    }
}
