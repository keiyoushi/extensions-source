package eu.kanade.tachiyomi.extension.ja.mangasaison

import okio.Buffer
import okio.BufferedSource
import okio.InflaterSource
import java.util.zip.Inflater

object Utils {
    private const val EOCD_SIG = 0x06054b50L
    private const val CDH_SIG = 0x02014b50L
    private const val LFH_SIG = 0x04034b50L

    private const val EOCD_MIN_LEN = 22
    private const val CDH_MIN_LEN = 46
    private const val LFH_MIN_LEN = 30

    private const val METHOD_DEFLATE = 8

    const val MAX_EOCD_SEARCH = EOCD_MIN_LEN + 0xFFFF + 1 // 65558

    class Entry(
        val name: String,
        val method: Int,
        val compressedSize: Long,
        val localHeaderOffset: Long,
    )

    class Eocd(
        val cdOffset: Long,
        val cdSize: Long,
    )

    private fun ByteArray.u16(off: Int): Int = (this[off].toInt() and 0xFF) or ((this[off + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.u32(off: Int): Long = (this[off].toLong() and 0xFF) or
        ((this[off + 1].toLong() and 0xFF) shl 8) or
        ((this[off + 2].toLong() and 0xFF) shl 16) or
        ((this[off + 3].toLong() and 0xFF) shl 24)

    fun findEocd(tail: ByteArray): Eocd {
        for (i in tail.size - EOCD_MIN_LEN downTo 0) {
            if (tail.u32(i) == EOCD_SIG) {
                return Eocd(cdOffset = tail.u32(i + 16), cdSize = tail.u32(i + 12))
            }
        }
        throw Exception("EOCD record not found")
    }

    fun parseCentralDirectory(cd: ByteArray): List<Entry> {
        val entries = ArrayList<Entry>()
        var p = 0
        while (p + CDH_MIN_LEN <= cd.size && cd.u32(p) == CDH_SIG) {
            val nameLen = cd.u16(p + 28)
            val extraLen = cd.u16(p + 30)
            val commentLen = cd.u16(p + 32)
            entries += Entry(
                name = String(cd, p + CDH_MIN_LEN, nameLen, Charsets.UTF_8),
                method = cd.u16(p + 10),
                compressedSize = cd.u32(p + 20),
                localHeaderOffset = cd.u32(p + 42),
            )
            p += CDH_MIN_LEN + nameLen + extraLen + commentLen
        }
        return entries
    }

    fun readEntry(
        source: BufferedSource,
        compressedSize: Long,
        method: Int,
        decode: (Buffer) -> BufferedSource = { it },
    ): Buffer {
        skipLocalHeader(source)
        val payload = Buffer().also { source.readFully(it, compressedSize) }
        val decoded = decode(payload)
        return if (method == METHOD_DEFLATE) inflate(decoded) else decoded.materialize()
    }

    private fun skipLocalHeader(source: BufferedSource) {
        val header = source.readByteArray(LFH_MIN_LEN.toLong())
        require(header.u32(0) == LFH_SIG) { "Not a local file header" }
        source.skip((header.u16(26) + header.u16(28)).toLong())
    }

    private fun inflate(source: BufferedSource): Buffer = InflaterSource(source, Inflater(true)).use {
        Buffer().apply { writeAll(it) }
    }

    private fun BufferedSource.materialize(): Buffer = this as? Buffer ?: Buffer().also { it.writeAll(this) }
}
