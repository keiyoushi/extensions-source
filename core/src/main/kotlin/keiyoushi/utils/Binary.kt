package keiyoushi.utils

/**
 * Reads four bytes starting at [offset] as a little-endian signed 32-bit [Int].
 *
 * The byte at [offset] is the least significant; the byte at `offset + 3` is the most significant.
 *
 * @param offset the index of the first (least significant) byte to read
 * @return the decoded 32-bit value
 * @throws IndexOutOfBoundsException if fewer than four bytes are available at [offset]
 */
fun ByteArray.readIntLittleEndian(offset: Int): Int = (this[offset].toInt() and 0xFF) or
    ((this[offset + 1].toInt() and 0xFF) shl 8) or
    ((this[offset + 2].toInt() and 0xFF) shl 16) or
    ((this[offset + 3].toInt() and 0xFF) shl 24)

/**
 * Reads four bytes starting at [offset] as a big-endian signed 32-bit [Int].
 *
 * The byte at [offset] is the most significant; the byte at `offset + 3` is the least significant.
 *
 * @param offset the index of the first (most significant) byte to read
 * @return the decoded 32-bit value
 * @throws IndexOutOfBoundsException if fewer than four bytes are available at [offset]
 */
fun ByteArray.readIntBigEndian(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 24) or
    ((this[offset + 1].toInt() and 0xFF) shl 16) or
    ((this[offset + 2].toInt() and 0xFF) shl 8) or
    (this[offset + 3].toInt() and 0xFF)

/**
 * Writes the signed 32-bit [value] as four little-endian bytes into this array, starting at [offset].
 *
 * The byte at [offset] is the least significant; the byte at `offset + 3` is the most significant.
 *
 * @param offset the index of the first (least significant) byte to write
 * @param value the 32-bit value to encode
 * @throws IndexOutOfBoundsException if fewer than four bytes are available at [offset]
 */
fun ByteArray.writeIntLittleEndian(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
    this[offset + 2] = (value ushr 16).toByte()
    this[offset + 3] = (value ushr 24).toByte()
}

/**
 * Writes the signed 32-bit [value] as four big-endian bytes into this array, starting at [offset].
 *
 * The byte at [offset] is the most significant; the byte at `offset + 3` is the least significant.
 *
 * @param offset the index of the first (most significant) byte to write
 * @param value the 32-bit value to encode
 * @throws IndexOutOfBoundsException if fewer than four bytes are available at [offset]
 */
fun ByteArray.writeIntBigEndian(offset: Int, value: Int) {
    this[offset] = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}

/**
 * Reads two bytes starting at [offset] as a little-endian unsigned 16-bit value, returned as an [Int].
 *
 * The byte at [offset] is the least significant; the byte at `offset + 1` is the most significant.
 *
 * @param offset the index of the first (least significant) byte to read
 * @return the decoded value, in `0..65535`
 * @throws IndexOutOfBoundsException if fewer than two bytes are available at [offset]
 */
fun ByteArray.readUShortLittleEndian(offset: Int): Int = (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

/**
 * Reads two bytes starting at [offset] as a big-endian unsigned 16-bit value, returned as an [Int].
 *
 * The byte at [offset] is the most significant; the byte at `offset + 1` is the least significant.
 *
 * @param offset the index of the first (most significant) byte to read
 * @return the decoded value, in `0..65535`
 * @throws IndexOutOfBoundsException if fewer than two bytes are available at [offset]
 */
fun ByteArray.readUShortBigEndian(offset: Int): Int = ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

/**
 * Reads four bytes starting at [offset] as a little-endian unsigned 32-bit value, returned as a [Long].
 *
 * The byte at [offset] is the least significant; the byte at `offset + 3` is the most significant.
 *
 * @param offset the index of the first (least significant) byte to read
 * @return the decoded value, in `0..4294967295`
 * @throws IndexOutOfBoundsException if fewer than four bytes are available at [offset]
 */
fun ByteArray.readUIntLittleEndian(offset: Int): Long = (this[offset].toLong() and 0xFF) or
    ((this[offset + 1].toLong() and 0xFF) shl 8) or
    ((this[offset + 2].toLong() and 0xFF) shl 16) or
    ((this[offset + 3].toLong() and 0xFF) shl 24)

/**
 * Reads eight bytes starting at [offset] as a little-endian signed 64-bit [Long].
 *
 * The byte at [offset] is the least significant; the byte at `offset + 7` is the most significant.
 *
 * @param offset the index of the first (least significant) byte to read
 * @return the decoded 64-bit value
 * @throws IndexOutOfBoundsException if fewer than eight bytes are available at [offset]
 */
fun ByteArray.readLongLittleEndian(offset: Int): Long = (this[offset].toLong() and 0xFF) or
    ((this[offset + 1].toLong() and 0xFF) shl 8) or
    ((this[offset + 2].toLong() and 0xFF) shl 16) or
    ((this[offset + 3].toLong() and 0xFF) shl 24) or
    ((this[offset + 4].toLong() and 0xFF) shl 32) or
    ((this[offset + 5].toLong() and 0xFF) shl 40) or
    ((this[offset + 6].toLong() and 0xFF) shl 48) or
    ((this[offset + 7].toLong() and 0xFF) shl 56)
