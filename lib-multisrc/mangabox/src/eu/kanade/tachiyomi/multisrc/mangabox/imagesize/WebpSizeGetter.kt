package eu.kanade.tachiyomi.multisrc.mangabox.imagesize

import java.io.InputStream

class WebpSizeGetter(stream: InputStream) : ImageSizeGetter(stream) {
    override fun validate(): Boolean = compare(RIFF, 0) && compare(WEBP, 8) && compare(VP8, 12)

    override fun calculate(): ImageSize = when (readInt8(15)) {
        ' '.code.toByte() -> { // VP8 (lossy)
            ImageSize(readUint16LE(26).toInt() and 0x3fff, readUint16LE(28).toInt() and 0x3fff)
        }

        'L'.code.toByte() -> { // VP8L (lossless)
            val bits = readUint32LE(21)
            ImageSize((bits and 0x3fffu).toInt() + 1, ((bits shr 14) and 0x3fffu).toInt() + 1)
        }

        'X'.code.toByte() -> { // VP8X (extended)
            ImageSize(readUint24LE(24).toInt() + 1, readUint24LE(27).toInt() + 1)
        }

        else -> {
            throw Exception("Invalid WebP")
        }
    }

    companion object {
        private val RIFF = "RIFF".toByteArray()
        private val WEBP = "WEBP".toByteArray()
        private val VP8 = "VP8".toByteArray()
        const val RANGE = "bytes=0-29"
    }
}
