package eu.kanade.tachiyomi.multisrc.initmanga

// Port of decryptV2 from the site's Init Manga Image Protector reader (imip-reader.js 2.2.0)
internal object ImageProtector {

    fun decrypt(data: ByteArray, key: String): ByteArray? {
        // 32-byte decoy, body, then the masked 32-byte header and a 4-byte checksum trailer
        if (data.size < 68) return null
        val totalLen = data.size - 36
        val scrambleLen = minOf(1024, totalLen - 32)

        var seed = 5381
        key.forEach { seed = seed * 33 + it.code }
        seed = (seed * 33 + (totalLen and 0xFFFF)).takeIf { it != 0 } ?: 1
        val random = generateSequence(seed) { x ->
            var next = x xor (x shl 13)
            next = next xor (next ushr 17)
            next xor (next shl 5)
        }.drop(1).iterator()
        val bodyMask = IntArray(scrambleLen) { random.next() and 0xFF }
        val headerMask = IntArray(32) { random.next() and 0xFF }

        val output = ByteArray(totalLen)
        var checksum = 0x5A
        for (i in 0 until 32) {
            val byte = (data[totalLen + i].toInt() and 0xFF) xor key[(i * 13 + 3) % key.length].code xor headerMask[i]
            output[i] = byte.toByte()
            checksum = checksum xor byte
        }
        if ((data[data.size - 4].toInt() and 0xFF) xor 0xA5 != checksum) return null

        data.copyInto(output, 32, 32, totalLen)
        for (i in 0 until scrambleLen) {
            val byte = (data[32 + i].toInt() and 0xFF) xor key[(i * 7 + 11) % key.length].code xor bodyMask[i]
            output[32 + i] = ((byte shl 4) or (byte ushr 4)).toByte()
        }
        return output
    }
}
