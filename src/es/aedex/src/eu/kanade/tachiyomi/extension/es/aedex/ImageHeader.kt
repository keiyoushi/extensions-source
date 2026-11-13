package eu.kanade.tachiyomi.extension.es.aedex

// RIFF (WebP), PNG, JPEG, GIF or an ISO-BMFF ftyp box (AVIF/HEIF)
fun isImageHeader(bytes: ByteArray): Boolean {
    fun matches(offset: Int, vararg signature: Int) = signature.withIndex().all { (i, byte) ->
        bytes.getOrNull(offset + i)?.toInt()?.and(0xFF) == byte
    }
    return matches(0, 0x52, 0x49, 0x46, 0x46) ||
        matches(0, 0x89, 0x50, 0x4E, 0x47) ||
        matches(0, 0xFF, 0xD8, 0xFF) ||
        matches(0, 0x47, 0x49, 0x46) ||
        matches(4, 0x66, 0x74, 0x79, 0x70)
}
