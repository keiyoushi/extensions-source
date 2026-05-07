package keiyoushi.utils

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
 * Encrypts or decrypts [data] using the RC4 stream cipher with [key].
 *
 * Since RC4 is symmetric, the same function serves as both encryption and decryption.
 *
 * An optional [skip] count discards that many initial keystream bytes before processing [data]
 *
 * @param key the RC4 key; must be between 1 and 256 bytes
 * @param data the plaintext or ciphertext to process
 * @param skip number of leading keystream bytes to discard before processing [data]; defaults to 0
 * @return the encrypted or decrypted byte array, same length as [data]
 * @throws IllegalArgumentException if [key] is empty, exceeds 256 bytes, or [skip] is negative
 */
fun rc4(key: ByteArray, data: ByteArray, skip: Int = 0): ByteArray {
    require(key.isNotEmpty()) { "RC4 key must not be empty" }
    require(key.size <= 256) { "RC4 key must not exceed 256 bytes, got ${key.size}" }
    require(skip >= 0) { "RC4 skip must be non-negative, got $skip" }
    val s = IntArray(256) { it }
    var j = 0
    for (i in 0..255) {
        j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
        val t = s[i]
        s[i] = s[j]
        s[j] = t
    }
    var a = 0
    var b = 0
    repeat(skip) {
        a = (a + 1) and 0xFF
        b = (b + s[a]) and 0xFF
        val t = s[a]
        s[a] = s[b]
        s[b] = t
    }
    val out = ByteArray(data.size)
    for (i in data.indices) {
        a = (a + 1) and 0xFF
        b = (b + s[a]) and 0xFF
        val t = s[a]
        s[a] = s[b]
        s[b] = t
        out[i] = ((data[i].toInt() and 0xFF) xor s[(s[a] + s[b]) and 0xFF]).toByte()
    }
    return out
}
