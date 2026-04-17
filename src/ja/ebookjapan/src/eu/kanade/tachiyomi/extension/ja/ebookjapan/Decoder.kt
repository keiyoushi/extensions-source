package eu.kanade.tachiyomi.extension.ja.ebookjapan

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class Decoder {
    private var fileId: String = ""
    private var pages: List<PageRecord> = emptyList()
    private var prefix: String = ""
    private var margin: Int = 0
    private var gridDim: Int = 0
    private var tables: List<ByteArray> = emptyList()

    class PageRecord(val pageNumber: Int, val width: Int, val height: Int)

    // WASM func 161
    fun decryptSession(
        sessionId: String,
        code: String,
        openPayload: String,
        drmPayload: String,
        fileId: String,
    ) {
        this.fileId = fileId
        this.pages = emptyList()
        this.prefix = ""
        this.margin = 0
        this.gridDim = 0
        this.tables = emptyList()

        val hSid = sha256(sessionId.toByteArray())
        val hCode = sha256(code.toByteArray())
        val all64 = hSid + hCode
        val openRaw = Base64.decode(openPayload, Base64.DEFAULT)
        val drmRaw = Base64.decode(drmPayload, Base64.DEFAULT)

        for (perm in PERMS) {
            val derived = ByteArray(48) { all64[perm[it]] }
            val stage1 = gcmDecrypt(derived.copyOfRange(0, 32), derived.copyOfRange(32, 48), openRaw)
            if (stage1.size < 48) continue
            val stage2 = gcmDecrypt(stage1.copyOfRange(0, 32), stage1.copyOfRange(32, 48), drmRaw)
            val binary = runCatching { zlibDecompress(stage2) }.getOrNull() ?: continue
            parseBinary(binary)
            return
        }
        throw Exception("Failed")
    }

    fun pageCount(): Int = pages.size

    fun pageSize(index: Int): Pair<Int, Int> = pages.getOrNull(index)?.let { it.width to it.height } ?: (0 to 0)

    // WASM func 181
    fun getPageName(pageIndex: Int): String {
        val page = pages.getOrNull(pageIndex) ?: return ""
        val input = "nf:$fileId/${page.pageNumber - 1}_ebj"
        val hash = sha256(input.toByteArray()).joinToString("") { "%02x".format(it) }
        return "${fileId.take(2)}/$fileId/$prefix/$hash.webp"
    }

    fun encodeScrambleFragment(pageIndex: Int): String? {
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

    // used by WASM func 161
    // u8 direction, u16 pageCount
    // pageCount × 11-byte page records: u16 pageNum, u16 w, u16 h, u8 view, u8 position, 3 bytes jumps
    // u16 chapterCount
    // chapterCount × { name, u16 pageIndex }
    // u8 margin, u8 gridDim, u8 numTables, numTables × gridDim^2 permutation bytes
    private fun parseBinary(data: ByteArray) {
        val buf = ByteBuffer.wrap(data)
        buf.get() // direction

        val pageCount = buf.short.toInt() and 0xFFFF
        pages = List(pageCount) {
            val pn = buf.short.toInt() and 0xFFFF
            val w = buf.short.toInt() and 0xFFFF
            val h = buf.short.toInt() and 0xFFFF
            buf.position(buf.position() + 5) // view + position + 3 bytes jumps
            PageRecord(pn, w, h)
        }

        val chapterCount = buf.short.toInt() and 0xFFFF
        repeat(chapterCount) {
            while (buf.get() != 0.toByte()) { /* skip name */ }
            buf.short // skip page index
        }

        val prefixEnd = (buf.position() until buf.limit()).first { data[it] == 0.toByte() }
        prefix = String(data, buf.position(), prefixEnd - buf.position(), Charsets.US_ASCII)
        buf.position(prefixEnd + 1)

        if (buf.hasRemaining()) {
            margin = buf.get().toInt() and 0xFF
            gridDim = buf.get().toInt() and 0xFF
            val numTables = buf.get().toInt() and 0xFF
            val tileBytes = gridDim * gridDim
            tables = List(numTables) {
                ByteArray(tileBytes).also(buf::get)
            }
        }
    }

    // AES-256-GCM(?)
    private fun gcmDecrypt(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val ct = data.copyOfRange(0, data.size - 16) // strip tag
        val ks = SecretKeySpec(key, "AES")
        val h = ecbEncrypt(ks, ByteArray(16))

        val j0 = if (iv.size == 12) {
            ByteArray(16).also {
                System.arraycopy(iv, 0, it, 0, 12)
                it[15] = 1
            }
        } else {
            val padLen = ((iv.size + 15) / 16) * 16
            val ghIn = ByteArray(padLen + 16)
            System.arraycopy(iv, 0, ghIn, 0, iv.size)
            putBE64(ghIn, ghIn.size - 8, iv.size.toLong() * 8)
            ghash(h, ghIn)
        }

        val numBlocks = (ct.size + 15) / 16
        val counters = ByteArray(numBlocks * 16)
        var ctr = inc32(j0)
        for (b in 0 until numBlocks) {
            System.arraycopy(ctr, 0, counters, b * 16, 16)
            ctr = inc32(ctr)
        }
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, ks)
        val keyStream = ecb.doFinal(counters)

        return ByteArray(ct.size) { (ct[it].toInt() xor keyStream[it].toInt()).toByte() }
    }

    private fun ecbEncrypt(key: SecretKeySpec, block: ByteArray): ByteArray = Cipher.getInstance("AES/ECB/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, key)
    }.doFinal(block)

    private fun ghash(h: ByteArray, data: ByteArray): ByteArray {
        var y = ByteArray(16)
        for (i in data.indices step 16) {
            y = gfMul(xor16(y, data.copyOfRange(i, i + 16)), h)
        }
        return y
    }

    // GF(2^128) multiplication with the GCM polynomial R = 0xe1 || 0^120
    private fun gfMul(x: ByteArray, y: ByteArray): ByteArray {
        val z = ByteArray(16)
        val v = y.copyOf()
        for (i in 0 until 128) {
            if (((x[i / 8].toInt() and 0xFF) ushr (7 - i % 8)) and 1 == 1) {
                for (j in 0..15) z[j] = (z[j].toInt() xor v[j].toInt()).toByte()
            }
            val lsb = v[15].toInt() and 1
            for (j in 15 downTo 1) {
                v[j] = (
                    ((v[j].toInt() and 0xFF) ushr 1) or
                        (((v[j - 1].toInt() and 0xFF) and 1) shl 7)
                    ).toByte()
            }
            v[0] = ((v[0].toInt() and 0xFF) ushr 1).toByte()
            if (lsb == 1) v[0] = (v[0].toInt() xor 0xe1).toByte()
        }
        return z
    }

    private fun inc32(block: ByteArray): ByteArray {
        val b = block.copyOf()
        for (i in 15 downTo 12) {
            val v = (b[i].toInt() and 0xFF) + 1
            b[i] = v.toByte()
            if (v and 0xFF != 0) break
        }
        return b
    }

    private fun xor16(a: ByteArray, b: ByteArray) = ByteArray(16) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private fun putBE64(buf: ByteArray, off: Int, v: Long) {
        for (i in 0..7) buf[off + 7 - i] = (v ushr (i * 8)).toByte()
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun zlibDecompress(data: ByteArray): ByteArray {
        val inf = Inflater()
        return try {
            inf.setInput(data)
            val out = ByteArrayOutputStream(data.size * 2)
            val buf = ByteArray(4096)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && inf.needsInput()) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            inf.end()
        }
    }

    companion object {
        private val PERMS = arrayOf(
            // WASM c2360863 (current)
            intArrayOf(
                0, 61, 58, 55, 52, 49, 46, 43, 40, 37, 34, 31, 28, 25, 22, 19,
                16, 13, 10, 7, 4, 1, 62, 59, 56, 53, 50, 47, 44, 41, 38, 35,
                32, 29, 26, 23, 20, 17, 14, 11, 8, 5, 2, 63, 60, 57, 54, 51,
            ),
            // WASM 889ca0be
            intArrayOf(
                0, 7, 58, 23, 52, 17, 46, 11, 40, 5, 34, 63, 28, 57, 22, 51,
                16, 45, 10, 39, 4, 33, 62, 27, 56, 21, 50, 15, 44, 9, 38, 3,
                32, 61, 26, 55, 20, 49, 14, 43, 8, 37, 2, 31, 60, 25, 54, 19,
            ),
        )
    }
}
