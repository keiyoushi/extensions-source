package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import keiyoushi.utils.readIntLittleEndian

/**
 * The web reader's `ultra_sign` query parameter (wasm a5b84fd26eefaff32016).
 *
 * Three hashes (query, body, masked body) feed a 29-byte seed; two hashes of the seed give 48 characters,
 * and the 11 hex digits of the timestamp overwrite fixed positions in them.
 */
object UltraSign {

    fun sign(body: String, ts: Long): String {
        val queryHash = hash(SIGNED_QUERY + ts.toString(16))
        val bodyHash = hash(body + (ts ushr 7).toString(2))
        val maskedBodyHash = hash(maskBody(body.encodeToByteArray(), if (ts % 2 == 0L) ALPHABET_EVEN_TS else ALPHABET_ODD_TS))

        val seed = seed(ts, queryHash, bodyHash, maskedBodyHash)
        val first = hash(seed + queryHash.byteAt(7) + bodyHash.byteAt(29))
        val second = hash(seed + maskedBodyHash.byteAt(31))
        val sign = (first.take(24) + second.take(24)).toCharArray()

        val tsHex = ts.toString(16).padStart(TS_POSITIONS.size, '0')
        TS_POSITIONS.forEachIndexed { i, position -> sign[position] = tsHex[i] }
        return String(sign)
    }

    // The wasm's fixed layout: constants, single bytes of ts and single characters of the three hashes.
    private fun seed(ts: Long, queryHash: String, bodyHash: String, maskedBodyHash: String): ByteArray {
        fun tsByte(shift: Int) = (ts ushr shift).toByte()
        return byteArrayOf(
            1, tsByte(23), 0, 0, 0, 2, bodyHash.byteAt(11), 0, 0, 5, 0, 0, tsByte(24), 7, tsByte(15), 7, 2,
            maskedBodyHash.byteAt(19), queryHash.byteAt(23), tsByte(8), 1, 1, 0, 6, 5, 0, 4, bodyHash.byteAt(7), tsByte(31),
        )
    }

    private fun String.byteAt(index: Int) = this[index].code.toByte()

    internal fun hash(input: String) = hash(input.encodeToByteArray())

    /**
     * A 128-bit Merkle-Damgård hash: MD-style padding, then one ARX round per little-endian word.
     * The four state words come out as 32 characters, one per nibble.
     */
    internal fun hash(input: ByteArray): String {
        var a = 0xDEADBEEF.toInt()
        var b = 0x12345678
        var c = 0xABCDEF01.toInt()
        var d = 0xFEEDCAFE.toInt()
        val padded = pad(input)
        for (offset in padded.indices step 4) {
            val word = padded.readIntLittleEndian(offset)
            val t1 = choose(b, a, d) + c + word
            val t2 = word + b + (t1 xor a xor d)
            val t3 = a + word + choose(t2, t1, d)
            d = (word + d + (t2 xor t1 xor t3)).rotateLeft(11)
            a = t3.rotateLeft(3)
            c = t1.rotateLeft(5)
            b = t2.rotateLeft(7)
        }
        // The wasm's output order.
        return buildString {
            for (word in intArrayOf(c, b, a, d)) {
                for (shift in 28 downTo 0 step 4) append(HASH_ALPHABET[(word ushr shift) and 15])
            }
        }
    }

    // Bitwise "if selector then x else y".
    private fun choose(selector: Int, x: Int, y: Int) = (selector and x) or (selector.inv() and y)

    // 0x80, zeros up to a whole 64-byte block, then the bit length as 8 little-endian bytes.
    private fun pad(input: ByteArray): ByteArray {
        val padded = input.copyOf(((input.size + 9) / 64 + 1) * 64)
        padded[input.size] = 0x80.toByte()
        val bits = input.size.toLong() * 8
        for (i in 0 until 8) padded[padded.size - 8 + i] = (bits ushr (8 * i)).toByte()
        return padded
    }

    // The body XORed with the keystream, then base64 over the alphabet. Unlike standard base64 the last group is
    // encoded in full from zero bytes, and one '=' per padding byte is appended after it.
    private fun maskBody(body: ByteArray, alphabet: String): String {
        val padding = (3 - body.size % 3) % 3
        val stream = keystream(body.size)
        val bytes = ByteArray(body.size + padding)
        for (i in body.indices) bytes[i] = (body[i].toInt() xor stream[i].toInt()).toByte()
        return buildString {
            for (i in bytes.indices step 3) {
                val group = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8) or (bytes[i + 2].toInt() and 0xFF)
                for (shift in 18 downTo 0 step 6) append(alphabet[(group ushr shift) and 63])
            }
            repeat(padding) { append('=') }
        }
    }

    // RC4 with two changes: the key schedule XORs S[i] with i after each swap, and output reads S[S[i] + S[j] + 1].
    private fun keystream(length: Int): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + RC4_KEY[i % RC4_KEY.size] + s[i]) and 255
            // Swap, then XOR the new S[i]; when i == j the XOR still applies.
            val swapped = s[j]
            s[j] = s[i]
            s[i] = swapped xor i
        }
        var i = 0
        j = 0
        return ByteArray(length) {
            i = (i + 1) and 255
            j = (j + s[i]) and 255
            val swapped = s[i]
            s[i] = s[j]
            s[j] = swapped
            s[(s[i] + s[j] + 1) and 255].toByte()
        }
    }

    // The query parameters the reader sends, sorted by key and concatenated.
    private const val SIGNED_QUERY = "a810devicepcnov27platformweb"

    private val RC4_KEY = intArrayOf(1, 3, 7)
    private const val ALPHABET_EVEN_TS = "UalVqQk8T7+5oJxC6MzIniWDrc3XERGN/wdLyp90mf2tHuvsgeSjZbBF1KAOPhY4"
    private const val ALPHABET_ODD_TS = "zI3XZb8T7+5opF1KJxUaBqQk90mf6MlVCEceSjAOPhY42RGN/wdLytHuvsgniWDr"

    // Nibbles 0..15 as letters of the sign alphabet.
    private val HASH_ALPHABET = "0123456789abcdef".map { "br5NH9CxpvJzmEt3OqoF7jgWMIZ2UnASfc8K6XaLYylT1eVBGh4sdQkDwRP0ui"[it.code % 62] }

    // Where each hex digit of ts goes, most significant first.
    private val TS_POSITIONS = intArrayOf(41, 37, 23, 29, 13, 19, 5, 47, 7, 11, 17)
}
