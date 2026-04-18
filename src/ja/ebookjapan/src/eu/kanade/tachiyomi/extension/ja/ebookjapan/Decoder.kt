package eu.kanade.tachiyomi.extension.ja.ebookjapan

import android.util.Base64
import okio.Buffer
import okio.InflaterSource
import okio.buffer
import okio.source
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Decoder {
    class PageRecord(val pageNumber: Int, val width: Int, val height: Int)

    class DecodedBook(
        val fileId: String,
        val prefix: String,
        val pages: List<PageRecord>,
        val margin: Int,
        val gridDim: Int,
        val tables: List<ByteArray>,
    ) {
        fun pageCount(): Int = pages.size

        fun pageSize(index: Int): Pair<Int, Int> = pages.getOrNull(index)?.let { it.width to it.height } ?: (0 to 0)

        // WASM func 181
        fun getPageName(pageIndex: Int): String {
            if (pageIndex !in pages.indices) return ""
            val input = "nf:$fileId/${pageIndex}_ebj"
            val hash = sha256(input.toByteArray()).joinToString("") { "%02x".format(it) }
            return "${fileId.take(2)}/$fileId/$prefix/$hash.webp"
        }

        fun encodeFragment(pageIndex: Int): String? {
            if (gridDim == 0 || tables.isEmpty()) return null
            val (w, h) = pageSize(pageIndex)
            if (w == 0 || h == 0) return null

            val tileBytes = gridDim * gridDim
            val buf = ByteBuffer.allocate(7 + tables.size * tileBytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(w.toShort())
            buf.putShort(h.toShort())
            buf.put(margin.toByte())
            buf.put(gridDim.toByte())
            buf.put(tables.size.toByte())
            tables.forEach { buf.put(it) }

            return Base64.encodeToString(buf.array(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }

    // WASM func 161
    // based on sum(sha256(sessionId) || sha256(code)) & 7
    // 0 -> stride 61 (default)
    // 1..7 -> [211, 29, 197, 43, 179, 89, 79] (%64 equivalent)
    // 'd = (d + stride) % 64' 48 times over 64-byte concatenated hash to pick the 48 key||iv bytes
    fun decryptSession(
        sessionId: String,
        code: String,
        openPayload: String,
        drmPayload: String,
        fileId: String,
    ): DecodedBook {
        val hSid = sha256(sessionId.toByteArray())
        val hCode = sha256(code.toByteArray())
        val all64 = hSid + hCode
        val openRaw = Base64.decode(openPayload, Base64.DEFAULT)
        val drmRaw = Base64.decode(drmPayload, Base64.DEFAULT)

        val byteSum = all64.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
        val stride = STRIDES[byteSum and 7]
        val derived = ByteArray(48).also {
            var d = 0
            for (i in 0 until 48) {
                it[i] = all64[d % 64]
                d += stride
            }
        }

        val key1 = ByteArray(32)
        val iv1 = ByteArray(16)

        System.arraycopy(derived, 0, key1, 0, 32)
        System.arraycopy(derived, 32, iv1, 0, 16)

        val stage1 = gcmDecrypt(key1, iv1, openRaw)
        if (stage1.size < 48) throw Exception("Output too short (${stage1.size} bytes)")

        val key2 = ByteArray(32)
        val iv2 = ByteArray(16)

        System.arraycopy(stage1, 0, key2, 0, 32)
        System.arraycopy(stage1, 32, iv2, 0, 16)

        val stage2 = gcmDecrypt(key2, iv2, drmRaw)
        val binary = zlibDecompress(stage2)
        return parseBinary(binary, fileId)
    }

    // used by WASM func 161
    //  u8 direction flags
    //  u16 pageCount
    //  pageCount x variable-length page records:
    //      u16 pageNumber
    //      u16 width
    //      u16 height
    //      4 bytes of metadata (view, position, padding)
    //      u8 jumpsCount
    //      jumpsCount x 10-byte jump entry { u16 left, u16 right, u16 top, u16 bottom, u16 page }
    //  u16 chapterCount
    //  chapterCount x { null-terminated UTF-8 name, u16 pageIndex }
    //  null-terminated ASCII prefix
    //  u8 margin, u8 gridDim, u8 numTables
    //  numTables x gridDim^2 permutation bytes
    //  u8 trailing byte (unused)
    private fun parseBinary(data: ByteArray, fileId: String): DecodedBook {
        val buf = ByteBuffer.wrap(data)
        buf.get() // direction flags byte

        val pageCount = buf.short.toInt() and 0xFFFF
        if (pageCount == 0 || pageCount > MAX_PAGES) {
            throw Exception("Page count: $pageCount")
        }

        val pages = List(pageCount) {
            val pn = buf.short.toInt() and 0xFFFF
            val w = buf.short.toInt() and 0xFFFF
            val h = buf.short.toInt() and 0xFFFF
            buf.position(buf.position() + 4) // view, position, padding
            val jumpsCount = buf.get().toInt() and 0xFF
            buf.position(buf.position() + jumpsCount * 10) // 10 bytes per jump entry
            PageRecord(pn, w, h)
        }

        val chapterCount = buf.short.toInt() and 0xFFFF
        if (chapterCount > MAX_CHAPTERS) {
            throw Exception("Chapter count: $chapterCount")
        }
        repeat(chapterCount) {
            while (buf.get() != 0.toByte()) { /* skip name */ }
            buf.short // skip page index
        }

        val prefixEnd = (buf.position() until buf.limit()).firstOrNull { data[it] == 0.toByte() }
            ?: throw Exception("Missing null terminator for prefix")
        val prefix = String(data, buf.position(), prefixEnd - buf.position(), Charsets.US_ASCII)
        buf.position(prefixEnd + 1)

        var margin = 0
        var gridDim = 0
        var tables = emptyList<ByteArray>()
        if (buf.remaining() >= 3) {
            margin = buf.get().toInt() and 0xFF
            gridDim = buf.get().toInt() and 0xFF
            val numTables = buf.get().toInt() and 0xFF
            val tileBytes = gridDim * gridDim
            if (gridDim in 1..32 && numTables in 1..16 && buf.remaining() >= numTables * tileBytes) {
                tables = List(numTables) {
                    ByteArray(tileBytes).also(buf::get)
                }
            }
        }

        return DecodedBook(fileId, prefix, pages, margin, gridDim, tables)
    }

    private fun gcmDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, iv),
            )
        }

        val inputBuffer = Buffer().write(data)
        val cipherSource = CipherInputStream(
            inputBuffer.inputStream(),
            cipher,
        ).source()

        return cipherSource.buffer().readByteArray()
    }

    private fun zlibDecompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        return try {
            val buffer = Buffer().write(data)
            val inflated = InflaterSource(buffer, inflater).buffer()
            inflated.readByteArray()
        } finally {
            inflater.end()
        }
    }

    companion object {
        private const val MAX_PAGES = 10000
        private const val MAX_CHAPTERS = 1000

        private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

        // STRIDES[0] = 61 (default), indexes 1-7 from u32-LE table
        // WASM data offset 1051460
        private val STRIDES = intArrayOf(61, 211, 29, 197, 43, 179, 89, 79)
    }
}
