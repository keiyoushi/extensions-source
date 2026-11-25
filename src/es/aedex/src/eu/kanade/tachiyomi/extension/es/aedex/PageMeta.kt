package eu.kanade.tachiyomi.extension.es.aedex

import keiyoushi.utils.sha256

// The page's own scramble settings. The site appends them to the image bytes as a trailer:
// [image][trailer][2-byte big-endian trailer length], the trailer masked with two hashes of the visit's token and the page handle.
// Unmasked it reads: magic AE DE 58 01, XOR key, gutter, key length, key.
class PageMeta(val key: String, val xor: Int, val gutter: Int, val imageLength: Int) {
    companion object {
        private const val MASK_LENGTH = 64
        private const val MIN_TRAILER = 7
        private const val MAX_TRAILER = 512
        private val MAGIC = intArrayOf(0xAE, 0xDE, 0x58, 0x01)

        fun fromTrailer(data: ByteArray, token: String, handle: String): PageMeta? {
            val size = data.size
            if (size < MIN_TRAILER + 2) return null
            val length = (data[size - 2].toInt() and 0xff shl 8) or (data[size - 1].toInt() and 0xff)
            if (length !in MIN_TRAILER..MAX_TRAILER || size < length + 2) return null
            val mask = "$token:m0:$handle".sha256() + "$token:m1:$handle".sha256()
            val start = size - 2 - length
            val plain = IntArray(length) { i -> (data[start + i].toInt() xor mask[i % MASK_LENGTH].toInt()) and 0xff }
            if (MAGIC.indices.any { plain[it] != MAGIC[it] }) return null
            val keyLength = plain[6]
            if (keyLength == 0 || keyLength + MIN_TRAILER > length) return null
            val key = String(CharArray(keyLength) { plain[MIN_TRAILER + it].toChar() })
            return PageMeta(key, plain[4], plain[5], start)
        }
    }
}
