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
