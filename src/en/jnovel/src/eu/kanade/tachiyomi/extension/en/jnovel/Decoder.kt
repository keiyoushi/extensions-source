package eu.kanade.tachiyomi.extension.en.jnovel

import android.util.Base64
import keiyoushi.utils.decodeProto
import keiyoushi.utils.rc4
import okio.Buffer
import okio.InflaterSource
import okio.buffer
import org.kotlincrypto.hash.blake2.BLAKE2b
import java.security.MessageDigest
import java.util.zip.Inflater

class DecodedManifest(val pub: ProtoPub, val pbexSeed: ByteArray?)

class Decoder {
    fun decodeManifestFull(ticket: E4PQSTicket): DecodedManifest {
        val wrapper = when (ticket.type) {
            TicketType.TDRM_V1 -> unwrapTdrmV1(ticket)
            TicketType.PLAIN_UNSPECIFIED -> ticket.child
            else -> throw Exception("Unsupported ticket type: ${ticket.type}")
        }

        val decrypted = when (wrapper.type) {
            WrapperType.PLAIN_UNSPECIFIED -> wrapper.data
            WrapperType.CDRM_V1 -> decryptCdrmV1(ticket.contentId, wrapper.iv, wrapper.data)
            else -> throw Exception("Unsupported wrapper type: ${wrapper.type}")
        }

        val hasPbex = decrypted.size >= 52 &&
            decrypted[0] == 'P'.code.toByte() && decrypted[1] == 'B'.code.toByte() &&
            decrypted[2] == 'E'.code.toByte() && decrypted[3] == 'X'.code.toByte()
        val pbexSeed = if (hasPbex) decrypted.copyOfRange(4, 52) else null
        val payload = if (hasPbex) decrypted.copyOfRange(52, decrypted.size) else decrypted

        val inflated = when (wrapper.dataType) {
            DataType.PROTOPUB -> payload
            DataType.PROTOPUB_ZLIB -> zlibInflate(payload)
            else -> throw Exception("Unsupported dataType: ${wrapper.dataType}")
        }

        return DecodedManifest(pub = inflated.decodeProto<ProtoPub>(), pbexSeed = pbexSeed)
    }

    // func 'f127' in drm_worker.js
    private fun unwrapTdrmV1(ticket: E4PQSTicket): E4PQSWrapper {
        val wrapper = ticket.child
        require(wrapper.iv.size == 32) { "CDRM iv must be 32 bytes, got ${wrapper.iv.size}" }
        require(ticket.contentId.length >= 3) { "contentId too short" }
        require(ticket.consumer.length >= 3) { "consumer too short" }

        // Blowfish IV derivation
        val expires = ticket.expires.seconds
        val tweak = ByteArray(8).apply {
            this[0] = (expires % 100).toByte()
            this[1] = (expires / 100 % 100).toByte()
            this[2] = (expires / 10_000 % 100).toByte()
            this[3] = (expires / 1_000_000 % 100).toByte()
            this[4] = (expires / 100_000_000 % 100).toByte()
            this[5] = ticket.contentId[ticket.contentId.length - 1].code.toByte()
            this[6] = ticket.contentId[ticket.contentId.length - 2].code.toByte()
            this[7] = ticket.contentId[ticket.contentId.length - 3].code.toByte()
        }
        xorMaskA6(tweak, 0)

        val sha1 = MessageDigest.getInstance("SHA-1")
            .apply {
                update(VF124)
                update(tweak)
            }
            .digest()
        val blowfishIv = sha1.copyOfRange(7, 15) // 8 bytes
        xorMaskA6(blowfishIv, 3)

        // RC4 key: consumer || contentId || VF124, truncated to 256 bytes
        val consumerBytes = ticket.consumer.encodeToByteArray()
        val contentIdBytes = ticket.contentId.encodeToByteArray()
        var rc4Key = consumerBytes + contentIdBytes + VF124
        if (rc4Key.size > 256) rc4Key = rc4Key.copyOf(256)

        // RC4-decrypt (iv || data[0...v261]) with 769-byte discard
        val v261 = minOf(wrapper.data.size, 123 + (contentIdBytes[2].toInt() and 0xFF))
        val rc4Input = ByteArray(wrapper.iv.size + v261).apply {
            System.arraycopy(wrapper.iv, 0, this, 0, wrapper.iv.size)
            System.arraycopy(wrapper.data, 0, this, wrapper.iv.size, v261)
        }
        val rc4Out = rc4(rc4Key, rc4Input, 769)

        // Blowfish-CBC-decrypt the first 32 bytes of the RC4 output
        val newIv = blowfishCbcDecrypt(rc4Out.copyOfRange(0, wrapper.iv.size), blowfishIv)

        // RC4-decrypted prefix || original tail
        val newData = ByteArray(wrapper.data.size).apply {
            System.arraycopy(rc4Out, wrapper.iv.size, this, 0, v261)
            System.arraycopy(wrapper.data, v261, this, v261, wrapper.data.size - v261)
        }

        return E4PQSWrapper(
            type = wrapper.type,
            iv = newIv,
            checksum = wrapper.checksum,
            data = newData,
            dataType = wrapper.dataType,
            dictChecksum = wrapper.dictChecksum,
        )
    }

    private fun xorMaskA6(buf: ByteArray, shift: Int) {
        for (i in buf.indices) {
            buf[i] = (buf[i].toInt() xor A6[(A6[i] + shift) % A6.size]).toByte()
        }
    }

    // 'f_on' func in wasm
    private fun decryptCdrmV1(contentId: String, iv: ByteArray, data: ByteArray): ByteArray {
        require(iv.size == 32) { "CDRM iv must be 32 bytes, got ${iv.size}" }
        val key = deriveCdrmKey(contentId, iv)
        val nonce = iv.copyOfRange(16, 24)
        val initialCounter = iv[24].toLong() and 0xFFL
        return XebpDecoder.chaCha8Decrypt(data, key, nonce, initialCounter)
    }

    private fun deriveCdrmKey(contentId: String, iv: ByteArray): ByteArray {
        val contentIdBytes = contentId.encodeToByteArray()
        val e = minOf(contentIdBytes.size, 24)
        val material = ByteArray(64).apply {
            System.arraycopy(contentIdBytes, 0, this, 0, e)
            System.arraycopy(ALPHABET, 0, this, e, 64 - e)
        }

        val key = BLAKE2b(256).apply { update(material) }.digest()

        // Stride-2 XOR: iv[0..16] into key[0,2,...,30]
        for (c in 0 until 16) {
            key[2 * c] = (key[2 * c].toInt() xor (iv[c].toInt() and 0xFF)).toByte()
        }

        // Scatter-XOR using low nibbles of iv[25..31]
        val ivk = iv[25].toInt() and 0xFF
        val ivl = iv[26].toInt() and 0xFF
        val ivm = iv[27].toInt() and 0xFF
        val ivn = iv[28].toInt() and 0xFF
        val ivo = iv[29].toInt() and 0xFF
        val ivp = iv[30].toInt() and 0xFF
        val ivq = iv[31].toInt() and 0xFF

        fun xorAt(index: Int, v: Int) {
            key[index] = (key[index].toInt() xor v).toByte()
        }
        xorAt((ivq and 15) + 2, ivk)
        xorAt((ivp and 15) + 5, ivl)
        xorAt((ivo and 15) + 5, ivm)
        xorAt((ivn and 15) + 6, ivn)
        xorAt((ivm and 15) + 5, ivo)
        xorAt((ivl and 15) + 0, ivp)
        xorAt((ivk and 15) + 0, ivq)

        // Final per-byte mask XOR
        for (c in 0 until 32) {
            val m = FINAL_MASK[((c + 3) and 0xFF) % 7].toInt() and 0xFF
            key[c] = (key[c].toInt() xor m).toByte()
        }
        return key
    }

    private fun zlibInflate(data: ByteArray): ByteArray {
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
        // vA6 table in drm_worker.js, 19-byte XOR-mask table
        private val A6 = intArrayOf(
            11, 13, 17, 19, 23, 29, 31, 37, 41, 43,
            47, 53, 59, 67, 71, 73, 79, 83, 69,
        )

        // vF124 in drm_worker.js:
        // 16 bytes of RC4(key="error", data=[fixed 16 bytes], discard=771)
        private val VF124: ByteArray by lazy {
            rc4(
                key = byteArrayOf(101, 114, 114, 111, 114), // "error"
                data = byteArrayOf(
                    0x8F.toByte(), 0x08, 0xBE.toByte(), 0x6C, 0x0F, 0xDE.toByte(),
                    0x6A, 0xF8.toByte(), 0x20, 0xED.toByte(), 0x7E, 0xAF.toByte(),
                    0x0E, 0x52, 0xDD.toByte(), 0x9D.toByte(),
                ),
                skip = 771,
            )
        }

        // Alphabet, WASM data offset 106704
        private val ALPHABET = "R5zRO0qEKFDfaP3OrLIbbQkjrcwWdgb4f7k6LLJjehQtvTrNXuzLp2_NT-eRnHK1".encodeToByteArray()

        // 7-byte repeating mask, WASM data offset 106784 (first 7 bytes)
        private val FINAL_MASK = byteArrayOf(
            0xD9.toByte(),
            0xAD.toByte(),
            0xBE.toByte(),
            0xEF.toByte(),
            0xC0.toByte(),
            0xDE.toByte(),
            0xAD.toByte(),
        )

        private fun le32(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

        // Blowfish-CBC decrypt with custom P-array and S-boxes (drm_worker.js)
        private class BlowfishTables(val p: IntArray, val s: Array<IntArray>)

        private val BLOWFISH: BlowfishTables by lazy {
            val sBytes = rc4(UNDEFINED_KEY, Base64.decode(BLOWFISH_S_B64, Base64.NO_WRAP), 769)
            val pBytes = rc4(UNDEFINED_KEY, Base64.decode(BLOWFISH_P_B64, Base64.NO_WRAP), 769)
            val p = IntArray(18) { i -> le32(pBytes, i * 4) }
            val s = Array(4) { box -> IntArray(256) { i -> le32(sBytes, (box * 256 + i) * 4) } }
            BlowfishTables(p, s)
        }

        private val UNDEFINED_KEY = byteArrayOf(117, 110, 100, 101, 102, 105, 110, 101, 100) // "undefined"

        private fun blowfishCbcDecrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
            require(iv.size == 8) { "Blowfish IV must be 8 bytes" }
            require(ciphertext.size % 8 == 0) { "Blowfish ciphertext must be a multiple of 8 bytes" }
            val t = BLOWFISH

            // IV split into two big-endian uint32s
            var prevL = be32(iv, 0)
            var prevR = be32(iv, 4)

            val out = ByteArray(ciphertext.size)
            var off = 0
            while (off < ciphertext.size) {
                val cl = be32(ciphertext, off)
                val cr = be32(ciphertext, off + 4)
                val (dl, dr) = blowfishDecryptBlock(cl, cr, t)
                val pl = dl xor prevL
                val pr = dr xor prevR
                writeBe32(out, off, pl)
                writeBe32(out, off + 4, pr)
                prevL = cl
                prevR = cr
                off += 8
            }
            return out
        }

        // 64-bit Blowfish decrypt of one block (two 32-bit halves)
        private fun blowfishDecryptBlock(
            xlIn: Int,
            xrIn: Int,
            t: BlowfishTables,
        ): Pair<Int, Int> {
            // 'dr()' in drm_worker.js, custom Blowfish class
            var xl = xlIn xor t.p[17]
            var xr = xrIn xor t.p[16]
            // Initial swap
            run {
                val tmp = xl
                xl = xr
                xr = tmp
            }
            for (i in 15 downTo 0) {
                // Swap
                val tmp = xl
                xl = xr
                xr = tmp
                // F-function + Feistel XOR
                xr = xr xor bF(xl, t)
                // Round-key XOR
                xl = xl xor t.p[i]
            }
            return Pair(xl, xr)
        }

        // Blowfish F-function, 'yr()' in drm_worker.js:
        // F(x) = ((S0[a] + S1[b]) xor S2[c]) + S3[d]
        private fun bF(x: Int, t: BlowfishTables): Int {
            val a = (x ushr 24) and 0xFF
            val b = (x ushr 16) and 0xFF
            val c = (x ushr 8) and 0xFF
            val d = x and 0xFF
            return ((t.s[0][a] + t.s[1][b]) xor t.s[2][c]) + t.s[3][d]
        }

        private fun be32(buf: ByteArray, off: Int): Int = ((buf[off].toInt() and 0xFF) shl 24) or
            ((buf[off + 1].toInt() and 0xFF) shl 16) or
            ((buf[off + 2].toInt() and 0xFF) shl 8) or
            (buf[off + 3].toInt() and 0xFF)

        private fun writeBe32(buf: ByteArray, off: Int, v: Int) {
            buf[off] = (v ushr 24).toByte()
            buf[off + 1] = (v ushr 16).toByte()
            buf[off + 2] = (v ushr 8).toByte()
            buf[off + 3] = v.toByte()
        }

        // vA0_0x5cc756.Er
        private const val BLOWFISH_P_B64 =
            "DvkjivaXokwc2uOuQv7MCnIYSqteJh5cCoK/vx0BMyf1IvfsEnVw5wzMOTSwI7WW7s7+x71uM9VgSspOTfCxQS5knBlOncoD"

        // vA0_0x5cc756.hr
        private const val BLOWFISH_S_B64 =
            "X7F+glnL8tI7scW7/K4cUMz0q5N8doNqheI2bn9cYXY0pQoBXSGoKHe2X9OhHkByrituJIH6XKO0Dj6f" +
                "KoHoyJUHnnfZxRIlXCaT7g6OgDHOj78zabivGKqKJbVm5xvd5Pi/5/XosyrnBAJKz1O0Ovk6YBxuDAmY" +
                "bHGSMd8EeIrZPEb8LqLqU9G5MRwcpWuw7oTJvnsvibNtiut8UYmgBqZMdpMoVLgnLw14PevY3pV5ovqD" +
                "v4uIw1PZhvTjKL/KAok28lEab9IrgWctRk0v7ZEiz9yiyDELDHDauPmoJOUMVM1vHc5yltc/CcthxqEj" +
                "jhu5i9RsiyHOQfFypB5jIWe/w2Av0FSHMm3Eu4pCI/diaqivNnUyCb0rJ+zgay6Bot2EvQfbXX7Xi/r6" +
                "CNU6D21lW8BjQua+Rz5y4UJgY06RV4XHJ4FQnKCKPXjRZZ3AQyr75brqd99AXgrMFjkxx3Imv34LA8An" +
                "XzmbYGXItoMIK1Pxi0Gmw2bZnld/xf74tWsqPJsntFBp8DbXwUEAGhPBsHEpY0XohW5CmLUEEKrhIRJ6" +
                "7sD1P5dPAagvEx2Rxz23McjcGQrWFuv8Lk7MvzYSng5cBNzE7crHOpED021UR/umeqFQUs1nSIHVnP0c" +
                "BHu7kCqKLcdXBBVcrl5Q/z1HPFJGs8OvuiztgOBH/ieX6v4SHgnCkIkc3b+4TjAeolv08jAAoJnphqli" +
                "a6FEmpWS46yRq0ooLM2RawNSqG8VnnPZS5Z8Dx8j7EueiAInT60ApYznjyZnQ9i+nBMZz73RCwebPoTE" +
                "DRUzK1hsPgOiq9HlZSpWKYL+zUaMtdL6YsXUUfGZ8qYhozCoOa6fUJtWwm6m9yogaDQHnuD2NfP7ivHF" +
                "MaZeHpO8rBeWdE9tzy/ygnR96ggvQ+UnnywK+cW+HpOhZ9r6++x738PM0B5XrL30UqGJ/B/peSe929eV" +
                "Di6wAB69kfsF9NrlrOubfF7cCwVH1biLblC2ZRnaZqEGmEI++Iqb3nwwIvqUZb9o2z4WlWADocxvNPzm" +
                "MaXyGYeB8oRzCLbnIE1wbkCSugNyUAZAWXiBuCxSWwhTd6CUeM7wrgEI1Cm2XnjSiVjPfqEOgEm8XCxq" +
                "k3xGmCOSqz0l9QYyJ19tKsKjQmX+ZO7VpvxbZI3PmXkoYIfIYBpcyqGkHTFvsgTrpt48wnb3byfXK+mo" +
                "GisFNlYLnRUP6vz4EJqz4O8I3nTC9oZJnIMWifObQ6RU9azDKLBRIVJ+XjseoTfULAZPa8i9pSdMBUno" +
                "zJ/Liq9Z7sOyjUnHz0xKx81tVTZEYDWEdB+Mv7JE+a4wHw5SpuFWSyH5ntuiPrFqWyG2DjSAV6j+j7+4" +
                "3w263HIOSjH1DB3NP+EQOKNuQPWtJQrRcGGYX68z/UemzRrB99NLzZb0NwEqVEfY2SxdFNhcHyuSNHWF" +
                "Zhir3pdLALOJyUOpK9WYY8Ko+Kq0MHwtSXKrLWkv3parO8zDSFAMB9CPwYaKRbS4L7b6hDiecFrgazfv" +
                "TlcmwYfTBnyi3tUEOuUC+1Xh3DyprYEAo0/Vcbk4PdPt+S2dIlLSCPsE6ABswre7/9DihpdIGN9hb+oB" +
                "Y9v/nmdBWk/SoBA5GTjzxEErE/6KHI4KObNekZZwWXuxS1IjmYKdisn2+PZUVs1chGveyRrruUc9284e" +
                "uIIktIh1JaQHFv8m6kqUFTFb+08hvkZkSZh75DUwx7415anVjRoZhu6mW5t1mfxL+l29XeFFxj1hoznv" +
                "HHoy6Ui2jXP5lHT4XCj4xbgacc1gQORxguoTVnrISCJ4MjCfIY3PzdhtI2YuS+XFddfVNR5s+1HPHIIC" +
                "fe+K7UmozpPqTAZRm4J+8eqNoIQGbWeXpXsi3Su+5BSCjqVH0zYi8Cs5ok5H7rsiZwZM3D9+GfbnfKwF" +
                "kE3VuyXb42Fq2IaEeFSo4iDYi0yoDMvI615DFmiAcYwWUm7dXIyVLdUgi3eODXwQG1hB61/nJeOnzL+I" +
                "5Ekyp6fXTMkZCBrYeQ4ZfVYJI31Hqx+BOcgUViEbBMjS0xehqWvBhm6pzBIAZgeHPPeuXlr2ZnYqP84h" +
                "0iLtMT7MHKlJk2HiIISVMI8VJLSQgrVilEl5VErINFk2PzxFp5M74x2fJsYDP74Dt0iq9xqVAcMVmzlM" +
                "CXniiqMgnAawKggzxgRNUIYzixb7SX2xbYX8BQtR5kUd6v46mmwVZBLGF4VLDKtmxGBvX+fTUomnkAft" +
                "9wJfvN+fmCV78A+gWnv6YGQfNwIKr3Ype5aZYaJTiKPKRjP4NsXKHvdt4YauwqcqNQBVjX+LBoiYV1Lj" +
                "1q3l6jYLm5gZ6uW/YppdttGuvH0rc0C5qB7u3YTV7iZcX2dnvdtEnyhzAxrKcmzrwXTT9gI1lb1kuJmQ" +
                "3NptGCHo3P/uJ0+8TJMidxli9kB0b6ZdYjRvjaULBY//w1K0zfvObLqL2xTLtSljvNnv4Jdbbbnvt44L" +
                "2oW6E9+vTZOlGtrK2QvI995Txq7KQPloqjqaFZcwWf0kqxzqdM1PJVXI27agx5mQDec3pMfAaku+F5pv" +
                "T2EyaQrtlUcy1rgckkhyiEB4wzHZh1gkg1F42rroEKxFsYoHMOhio+wuafE9Anf1xfX9oTyHThbKEh2S" +
                "Aaihw/YP6bPcDDVhuX31sGgYneU1xd8vaWhbG2hfvtQ3rItNKcajsS3GqzkYeC3qWyq3XX8o8xxDwq7d" +
                "M7UU6KRPnKi6bj8x2if+4OVQGMTp8w8vjk3XNxxiwefwSDAGUWPcpz+Ct3GowI33R/UKwP+alSBKdH4W" +
                "7Y6AMvemKsyggilLSK6VBfEsvfYf6Qb6AfAy0ffndaDFQnCmX0m8nYfSnB/H/9ok7kx4W//FOPhhldri" +
                "r9oRY0CgW2S7t0HL30lAwiuqbxwEq6y99vwAmIlenXAKUrGp42lpgDxky+yITVVIowokUM2PGIZUcnKq" +
                "sLwJpmp13rGNCYSQ8TqafIodeWcWvMnkslHTHUl9yTQWKg11jkAO+0NxCjdth+cZX5SdLVLZcCJRtE4L" +
                "tviHgOWEP52QaEImClXvtlpU1Rd4DAHw3RtU+OfWdGArhta0SupzeuNh9QuxzebBbvWI2+iILhtfjtiF" +
                "H4vTv+0mmc0RENVTxI3ukJTfoZj8HtIqdf820Qri76JFtlFKUUVNWJnWgWs3Ha4MBGWyfHpQTNKfKkcx" +
                "CUgQ/L63U7vJwIHFzwNbNjG5BsoWASHeINPwgWP9czV2ZRIJ9BGUNzAUiCCB0/Nzbnfm0nFY+Qt0F53+" +
                "FDHWqWplsykslk78bVgDIghTavTFUrc7X295vZXeXhAePSnqlEObls9/FFkqfES4SVSI7H+IQqgBuKMy" +
                "MKC2CalUAyB907ovKaYDPUEFGS/k4ig5czIRbAJTCnjrNHpJBPODPlRVUde85v6WzoqWvdGr/SDD0dGl" +
                "YyASHL8xRR6aTbgMTlIpqP7n1/nSaAk7L4b18GQHxkngs1PPq5QbLp86PUKOT7dJjSUKyiZFx8Gvu9tX" +
                "uWQw+cYUAN/pUTZHMdsrz5j0xShJT9hXIyM0w6KKZnu6USe0zmH4ErJsfkjETpAjzxXR1vjtgC7vaGqK" +
                "Y5Y6X41yG3+SZGjmkIb6Pq2VwgyRCDZxW2B1g8mD/Cj9BmnO2YwBfa6Sfxqicqrk8hWExDAfX+OU0evO" +
                "29DFOt+sQAZrwXj0VVeyjTAliqH+R6a6MaUanvcxteXqAKqVniXWF61vIIQV0lqh0Q+6AXF7lVqahz/5" +
                "f3VSFy7ApiMszgsSvFwf9mN0QBhhpfjmKF2zdOcp/NQ+KRr48OPiAdYRVvTa0uUx0Pn0EeINbTc0QVgx" +
                "aCSj6O9P3UfSW4et81lV9t1MdxPcVBdkD0L0JR1bDK4CR+bx6vcwxBNJbtAJrhuAwn4o7ZXL01qJHrWU" +
                "MbiACWdiWhhvZ8IERrWUFqjPaedGV9hdgvrTOekPVn7L/CxoacE6IAYCGO8s6Xl8yPQEP/c8idTlP5g0" +
                "WMhhPnwbLLrcuCkyPaVA6dsQ6/uGzunJ8FA3dFRrby2KNglfGlsseJ13fau8UbZ2fclwDIYoWfATRd6k" +
                "fgc2nDOwdArUitCTPcPGRMohljxZVdrRFfwuJUf9mIG+RQON0qw9AWOK9lHQRmEohRlOb3yRKCyCnYMW" +
                "4MV8uoCS2whQHcC4MMtqDnQEgR9TVbjoKzs5iYIEwSuJxr8SJ1s6adk/EjiJeIPbe6Pu+53F2XIcXFLK" +
                "Iq5WVVfTMESuaSplKdqBBa5RCU5+qaM2ksCI/s5ioRFlN18mB95aUf1K8oxX/MAbUvK1IEyffON+9wd3" +
                "lJhauTNlaPenRUVT07t53aSNrEptbO1Qelk9ZywB/cS2Akkro6uiULpRwujRHtTbGtBsvt+fIa0bQJFr" +
                "hP85P3THJ1JSnjkCQvUlGe1EpQhrZjccgikiRVJdzNWb/TGo1N3JrWT4GTlVQKBJcXc+sam+ucL7G8W0" +
                "aU5NJagY/hxnyUrtg+cdVrQHan0+G+nO4scpgNt8DkxRTIohyJigUYeM5jChFQCx11AE8nlpSqhnbW9J" +
                "eAiupT2Yl6lmJJDMNry+MqNf57RCb1HlaJ5Uc2SJir7GMNpa2GNS6Rzw7NWOuHj4A23mxFcoXjD5DTdu" +
                "NmJSrZbe/1qbEHR3Ddm1PFTbvjB4CPrUBmfXtQD6DdEmE0l0crK0R7Fp47V408mD6bvm6WRjFiMYVD94" +
                "hZi3HY5Bunbk2kNU+PJKu03KvyaDkZUTuuEXII2GjC9DAmuCf3YlVEvRtWztigovpvGjWr3xvZh1BNl6" +
                "Fkju9trRXXmGXP7uzUyTRq6xf1dvfQSENVm3X/JpS9Y+4v+MAw0ZaAHCPmyMYza53/ucV8xWizd5PMKm" +
                "VjSalFw5noHr3AH8eA1t4y0ayFj3ZSGicUowEz3eOdw08l78GyIGyces2FadeRjFFq+Ak3bYTCh371Qm" +
                "RoqELg605COLtlJJeX/t3d95jbmYinxWj6McILHu1HmaCgHZzXqdSG7TgyagQ5ydz4oZSQPAxC5n9dgS" +
                "aONLVYA959qFn4rjSxBy4Q6s6d8As020kwMCasHHrYvmUV2xJmAHHdvPdx/s3FgXQgpjWbU44resghpz" +
                "XKD9tw6DP4t1V7IvP5qs5Cy4vwtGX0OAllqlmgOjwV8L9MP1zF85Xqt3iCglw1Q6nM+uQfmLqqM12qpB" +
                "V6OyZASP/3O9Lt3/dxSi/KzBObXVLKQTh85w+3rOAdiIf9xDCC7egcQhswgyIp1MpAT13lSfuXt8CcBN" +
                "0+R/f9FOdwijRySQLqQ8J/gdFSXhgFSbodOlb2tZuZtTsRmr2cAlyHXIzrfP1dI6vkkmkrZkwjFSkr1N" +
                "wm0kWXL4U3rHvF0Q1/ZDzN4Bv2g9GeR/0Aom2YsKENNxbyCB5TG/+0N9ZqENnVoPivXJUZV3awdyyZjo" +
                "q+BqevGcdUWK7bb2ClfkCg=="
    }
}
