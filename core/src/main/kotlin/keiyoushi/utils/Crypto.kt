package keiyoushi.utils

import okio.Buffer
import okio.ForwardingSource
import okio.Source

/**
 * Decodes this hexadecimal string into a [ByteArray].
 *
 * The input string must contain only valid hexadecimal characters (`0-9`, `a-f`, `A-F`)
 * and must have an even length.
 *
 * @return the decoded bytes represented by this hex string
 * @throws IllegalArgumentException if the string has an odd length or contains invalid hex characters
 */
fun String.decodeHex(): ByteArray {
    require(length % 2 == 0) { "Unexpected hex string: $this" }

    val result = ByteArray(length / 2)
    for (i in result.indices) {
        val d1 = decodeHexDigit(this[i * 2]) shl 4
        val d2 = decodeHexDigit(this[i * 2 + 1])
        result[i] = (d1 + d2).toByte()
    }
    return result
}

private fun decodeHexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> throw IllegalArgumentException("Unexpected hex digit: $c")
}

/**
 * Encrypts or decrypts this [ByteArray] using the RC4 stream cipher with [key].
 *
 * Since RC4 is symmetric, the same function serves as both encryption and decryption.
 *
 * An optional [skip] count discards that many initial keystream bytes before processing this [ByteArray].
 *
 * @param key the RC4 key; must be between 1 and 256 bytes
 * @param skip number of leading keystream bytes to discard before processing; defaults to 0
 * @return the encrypted or decrypted [ByteArray], same length as this [ByteArray]
 * @throws IllegalArgumentException if [key] is empty, exceeds 256 bytes, or [skip] is negative
 */
fun ByteArray.rc4(key: ByteArray, skip: Int = 0): ByteArray = copyOf().also { Rc4(key, skip).apply(it, 0, it.size) }

/**
 * Encrypts or decrypts this [Source] using the RC4 stream cipher with [key].
 *
 * Since RC4 is symmetric, the same function serves as both encryption and decryption.
 *
 * An optional [skip] count discards that many initial keystream bytes before processing this [Source].
 *x
 * @param key the RC4 key; must be between 1 and 256 bytes
 * @param skip number of leading keystream bytes to discard before processing; defaults to 0
 * @return the encrypted or decrypted [Source], same length as this [Source]
 * @throws IllegalArgumentException if [key] is empty, exceeds 256 bytes, or [skip] is negative
 */
fun Source.rc4(key: ByteArray, skip: Int = 0): Source = Rc4Source(this, Rc4(key, skip))

private class Rc4Source(
    delegate: Source,
    private val rc4: Rc4,
) : ForwardingSource(delegate) {
    private val cursor = Buffer.UnsafeCursor()

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read <= 0L) return read

        sink.readAndWriteUnsafe(cursor).use {
            var length = it.seek(sink.size - read)
            while (length != -1) {
                rc4.apply(it.data!!, it.start, length)
                length = it.next()
            }
        }
        return read
    }
}

private class Rc4(key: ByteArray, skip: Int) {
    private val s = IntArray(256) { it }
    private var a = 0
    private var b = 0

    init {
        require(key.isNotEmpty()) { "RC4 key must not be empty" }
        require(key.size <= 256) { "RC4 key must not exceed 256 bytes, got ${key.size}" }
        require(skip >= 0) { "RC4 skip must be non-negative, got $skip" }
        var j = 0
        for (i in 0..255) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val t = s[i]
            s[i] = s[j]
            s[j] = t
        }
        repeat(skip) {
            a = (a + 1) and 0xFF
            b = (b + s[a]) and 0xFF
            val t = s[a]
            s[a] = s[b]
            s[b] = t
        }
    }

    fun apply(data: ByteArray, offset: Int, length: Int) {
        val s = s
        var a = a
        var b = b
        for (i in offset until offset + length) {
            a = (a + 1) and 0xFF
            val sa = s[a]
            b = (b + sa) and 0xFF
            val sb = s[b]
            s[a] = sb
            s[b] = sa
            data[i] = (data[i].toInt() xor s[(sa + sb) and 0xFF]).toByte()
        }
        this.a = a
        this.b = b
    }
}
