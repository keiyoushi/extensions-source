package eu.kanade.tachiyomi.extension.en.jnovel

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.BitmapFactory.decodeByteArray
import android.graphics.Canvas
import keiyoushi.utils.decodeHex
import okio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

// XEBP watermark remover
// Watermark also contains a barcode to identify the user.
// WASM xebp_render = f_sn
// master key from pbexSeed WASM func f_pn
object XebpDecoder {
    // WASM data offset 128708
    private val XXTEA_KEY = "6ca87b0fa8513e36165347af5de51989".decodeHex()

    // TIFF little-endian magic "II*\x00", must appear after tfix decrypt
    private val TIFF_MAGIC_LE = byteArrayOf(0x49, 0x49, 0x2A, 0x00)

    fun decrypt(xebpBytes: ByteArray, ctx: XebpContext): ByteArray {
        val container = parseContainer(xebpBytes)
        val zstrMeta = parseZstr(container.zstr)
        val encryptedPayload = ctx.pbexSeed.copyOfRange(16, 48)
        val perImageKey = ByteArray(32) { i ->
            (encryptedPayload[i].toInt() xor ctx.iv[i].toInt()).toByte()
        }

        // WASM f_ln(c+80, ..., 1821454, ...)
        val workspace = container.tfix.copyOf()
        val xorLen = minOf(zstrMeta.xorLen, workspace.size)
        for (i in 0 until xorLen) workspace[i] = (workspace[i].toInt() xor 0x11).toByte()
        val tiffBytes = chaCha8Decrypt(workspace, perImageKey, zstrMeta.nonce, zstrMeta.xorLen.toLong())

        if (!tiffBytes.copyOfRange(0, 4).contentEquals(TIFF_MAGIC_LE)) {
            throw IllegalStateException(
                "tfix did not decrypt to TIFF (expected II*\\0, got ${hex(tiffBytes.copyOfRange(0, 4))})",
            )
        }

        val patch = TiffDecoder.decode(tiffBytes)
        return compositePatch(container.vp8RiffAsWebp, patch)
    }

    private class Container(
        val vp8: ByteArray,
        val vp8RiffAsWebp: ByteArray,
        val zstr: ByteArray,
        val tfix: ByteArray,
    )

    private fun parseContainer(xebp: ByteArray): Container {
        require(xebp.size >= 20) { "XEBP too small: ${xebp.size}" }
        require(
            xebp[0] == 'R'.code.toByte() && xebp[1] == 'I'.code.toByte() &&
                xebp[2] == 'F'.code.toByte() && xebp[3] == 'F'.code.toByte(),
        ) { "XEBP missing RIFF magic" }

        val bb = ByteBuffer.wrap(xebp).order(ByteOrder.LITTLE_ENDIAN)
        // Skip RIFF + size; next 4 bytes are WEBP
        // Then chunks: 4 fourcc + 4 size + payload (padded to even)
        var off = 12
        var vp8Start = -1
        var vp8Len = -1
        var zstrStart = -1
        var zstrLen = -1
        var tfixStart = -1
        var tfixLen = -1
        while (off + 8 <= xebp.size) {
            val fourCC = String(xebp, off, 4, Charsets.ISO_8859_1)
            val size = bb.getInt(off + 4)
            val payloadStart = off + 8
            when (fourCC) {
                "VP8 ", "VP8L", "VP8X" -> {
                    vp8Start = off
                    vp8Len = size
                }
                "ZSTR" -> {
                    zstrStart = payloadStart
                    zstrLen = size
                }
                "tfix" -> {
                    tfixStart = payloadStart
                    tfixLen = size
                }
            }
            off = payloadStart + size + (size and 1) // padded to even
        }

        require(zstrStart >= 0 && tfixStart >= 0) {
            "XEBP missing ZSTR or tfix chunk"
        }

        val vp8RiffAsWebp = if (vp8Start >= 0) {
            val vp8ChunkEnd = vp8Start + 8 + vp8Len + (vp8Len and 1)
            val out = ByteArray(vp8ChunkEnd)
            System.arraycopy(xebp, 0, out, 0, vp8ChunkEnd)
            val newSize = vp8ChunkEnd - 8
            out[4] = (newSize and 0xFF).toByte()
            out[5] = ((newSize ushr 8) and 0xFF).toByte()
            out[6] = ((newSize ushr 16) and 0xFF).toByte()
            out[7] = ((newSize ushr 24) and 0xFF).toByte()
            out[8] = 'W'.code.toByte()
            out[9] = 'E'.code.toByte()
            out[10] = 'B'.code.toByte()
            out[11] = 'P'.code.toByte()
            out
        } else {
            ByteArray(0)
        }

        return Container(
            vp8 = if (vp8Start >= 0) xebp.copyOfRange(vp8Start + 8, vp8Start + 8 + vp8Len) else ByteArray(0),
            vp8RiffAsWebp = vp8RiffAsWebp,
            zstr = xebp.copyOfRange(zstrStart, zstrStart + zstrLen),
            tfix = xebp.copyOfRange(tfixStart, tfixStart + tfixLen),
        )
    }

    // WASM f_sn XXTEA decrypt + nonce extraction
    private class ZstrMeta(val version: Int, val nonce: ByteArray, val xorLen: Int)

    // "1\n77\nae1e76f1a91981cab70e07c2"  -> xorLen = 77^65 = 12, 3 u32s
    // "1\n112\n9126b48ac09943af1286dc18" -> xorLen = 112^65 = 49, 3 u32s
    private fun parseZstr(zstr: ByteArray): ZstrMeta {
        val text = String(zstr, Charsets.ISO_8859_1).trimEnd('\u0000')
        val lines = text.split('\n')
        require(lines.size >= 3) { "ZSTR too few lines: ${lines.size}" }

        val version = lines[0].trim().toInt()
        require(version == 1) { "Unsupported ZSTR version: $version" }

        val rawCount = lines[1].trim().toInt()
        val xorLen = (rawCount xor 65) and 0xFFFF
        require(xorLen >= 0) { "ZSTR xorLen negative: $xorLen" }

        val encryptedHex = lines[2].trim()
        require(encryptedHex.length % 8 == 0 && encryptedHex.isNotEmpty()) {
            "ZSTR encrypted hex must encode whole uint32s, got length ${encryptedHex.length}"
        }
        val countU32 = encryptedHex.length / 8
        require(countU32 >= 2) { "ZSTR needs at least 2 uint32s, got $countU32" }

        val encrypted = encryptedHex.decodeHex()
        val u32s = IntArray(countU32)
        for (i in 0 until countU32) u32s[i] = le32(encrypted, i * 4)

        xxteaDecrypt(u32s, XXTEA_KEY)

        // Last uint32 is the real byte length; first N bytes are the nonce
        val realLen = u32s[countU32 - 1]
        require(realLen in 1..((countU32 - 1) * 4)) { "ZSTR length-tag out of range: $realLen" }
        val decrypted = ByteArray(realLen)
        for (i in 0 until realLen) {
            decrypted[i] = ((u32s[i ushr 2] ushr ((i and 3) shl 3)) and 0xFF).toByte()
        }

        // For ChaCha8, 8 bytes of nonce
        val nonce = decrypted.copyOf(8)
        return ZstrMeta(version, nonce, xorLen)
    }

    // ChaCha8 (8 rounds, 64-byte keystream block)
    fun chaCha8Decrypt(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        initialCounter: Long = 0L,
    ): ByteArray {
        require(key.size == 32) { "ChaCha8 key must be 32 bytes, got ${key.size}" }
        require(nonce.size == 8) { "ChaCha8 nonce must be 8 bytes, got ${nonce.size}" }

        val state = IntArray(16)
        state[0] = 0x61707865 // "expa"
        state[1] = 0x3320646E // "nd 3"
        state[2] = 0x79622D32 // "2-by"
        state[3] = 0x6B206574 // "te k"
        for (i in 0 until 8) state[4 + i] = le32(key, i * 4)
        state[14] = le32(nonce, 0)
        state[15] = le32(nonce, 4)

        val out = ByteArray(data.size)
        val work = IntArray(16)
        var counter = initialCounter
        var pos = 0
        while (pos < data.size) {
            state[12] = counter.toInt()
            state[13] = (counter ushr 32).toInt()
            System.arraycopy(state, 0, work, 0, 16)
            repeat(4) {
                // column rounds
                qround(work, 0, 4, 8, 12)
                qround(work, 1, 5, 9, 13)
                qround(work, 2, 6, 10, 14)
                qround(work, 3, 7, 11, 15)
                // diagonal rounds
                qround(work, 0, 5, 10, 15)
                qround(work, 1, 6, 11, 12)
                qround(work, 2, 7, 8, 13)
                qround(work, 3, 4, 9, 14)
            }
            for (i in 0..15) work[i] += state[i]

            val blockLen = minOf(64, data.size - pos)
            for (i in 0 until blockLen) {
                val ks = (work[i ushr 2] ushr ((i and 3) shl 3)) and 0xFF
                out[pos + i] = ((data[pos + i].toInt() and 0xFF) xor ks).toByte()
            }
            pos += blockLen
            counter++
        }
        return out
    }

    private fun qround(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]
        s[d] = Integer.rotateLeft(s[d] xor s[a], 16)
        s[c] += s[d]
        s[b] = Integer.rotateLeft(s[b] xor s[c], 12)
        s[a] += s[b]
        s[d] = Integer.rotateLeft(s[d] xor s[a], 8)
        s[c] += s[d]
        s[b] = Integer.rotateLeft(s[b] xor s[c], 7)
    }

    // WASM XXTEA loop f_sn
    private const val DELTA = 0x9E3779B9.toInt()

    private fun xxteaDecrypt(v: IntArray, keyBytes: ByteArray) {
        val n = v.size
        if (n < 2) return
        val k = IntArray(4) { le32(keyBytes, it * 4) }
        val rounds = 6 + 52 / n
        var sum = (rounds * DELTA.toLong()).toInt()
        var y = v[0]
        repeat(rounds) {
            val e = (sum ushr 2) and 3
            for (p in n - 1 downTo 1) {
                val z = v[p - 1]
                val mx = (((z ushr 5) xor (y shl 2)) + ((y ushr 3) xor (z shl 4))) xor
                    ((sum xor y) + (k[(p and 3) xor e] xor z))
                v[p] -= mx
                y = v[p]
            }
            val z = v[n - 1]
            val mx = (((z ushr 5) xor (y shl 2)) + ((y ushr 3) xor (z shl 4))) xor
                ((sum xor y) + (k[(0 and 3) xor e] xor z))
            v[0] -= mx
            y = v[0]
            sum -= DELTA
        }
    }

    private fun compositePatch(vp8Webp: ByteArray, patch: TiffDecoder.RgbaImage): ByteArray {
        val decoded = decodeByteArray(vp8Webp, 0, vp8Webp.size)
            ?: throw Exception("Failed to decode VP8 WebP (${vp8Webp.size} bytes)")

        val result = createBitmap(
            decoded.width,
            decoded.height,
            Bitmap.Config.ARGB_8888,
        )
        Canvas(result).drawBitmap(decoded, 0f, 0f, null)
        decoded.recycle()

        val pw = minOf(patch.width, result.width)
        val ph = minOf(patch.height, result.height)
        val px = IntArray(pw * ph)
        for (y in 0 until ph) {
            for (x in 0 until pw) {
                val srcOff = (y * patch.width + x) * 4
                val r = patch.rgba[srcOff].toInt() and 0xFF
                val g = patch.rgba[srcOff + 1].toInt() and 0xFF
                val b = patch.rgba[srcOff + 2].toInt() and 0xFF
                px[y * pw + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        result.setPixels(px, 0, pw, 0, 0, pw, ph)

        val out = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 100, out.outputStream())
        result.recycle()
        return out.readByteArray()
    }

    private fun le32(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

    internal fun hex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xFF))
        return sb.toString()
    }
}
