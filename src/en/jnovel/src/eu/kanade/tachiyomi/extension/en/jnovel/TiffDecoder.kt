package eu.kanade.tachiyomi.extension.en.jnovel

import okio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

// https://cs.opensource.google/go/x/image
// https://pkg.go.dev/golang.org/x/image/tiff/lzw
object TiffDecoder {
    class RgbaImage(val width: Int, val height: Int, val rgba: ByteArray)

    // TIFF tags we care about
    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_LENGTH = 257
    private const val TAG_BITS_PER_SAMPLE = 258
    private const val TAG_COMPRESSION = 259
    private const val TAG_PHOTOMETRIC = 262
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_SAMPLES_PER_PIXEL = 277
    private const val TAG_STRIP_BYTE_COUNTS = 279
    private const val TAG_PLANAR_CONFIG = 284
    private const val TAG_COLORMAP = 320
    private const val TAG_PREDICTOR = 317

    // TIFF field types
    private const val TYPE_BYTE = 1
    private const val TYPE_ASCII = 2
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val TYPE_RATIONAL = 5

    fun decode(tiff: ByteArray): RgbaImage {
        require(tiff.size >= 8) { "TIFF too small: ${tiff.size}" }
        require(tiff[0] == 0x49.toByte() && tiff[1] == 0x49.toByte()) { "TIFF must be little-endian" }
        require(tiff[2] == 0x2A.toByte() && tiff[3] == 0x00.toByte()) { "TIFF magic mismatch" }

        val bb = ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN)
        val ifdOffset = bb.getInt(4)

        val numEntries = bb.getShort(ifdOffset).toInt() and 0xFFFF

        var width = 0
        var height = 0
        var bitsPerSample = intArrayOf(8)
        var compression = 1
        var photometric = 2
        var stripOffsets = intArrayOf()
        var samplesPerPixel = 1
        var stripByteCounts = intArrayOf()
        var planarConfig = 1
        var colorMap: IntArray? = null
        var predictor = 1

        for (i in 0 until numEntries) {
            val entryOff = ifdOffset + 2 + i * 12
            val tag = bb.getShort(entryOff).toInt() and 0xFFFF
            val type = bb.getShort(entryOff + 2).toInt() and 0xFFFF
            val count = bb.getInt(entryOff + 4)
            val valueOff = entryOff + 8

            val readValue = { readValues(bb, type, count, valueOff) }

            when (tag) {
                TAG_IMAGE_WIDTH -> width = readValue().first()
                TAG_IMAGE_LENGTH -> height = readValue().first()
                TAG_BITS_PER_SAMPLE -> bitsPerSample = readValue()
                TAG_COMPRESSION -> compression = readValue().first()
                TAG_PHOTOMETRIC -> photometric = readValue().first()
                TAG_STRIP_OFFSETS -> stripOffsets = readValue()
                TAG_SAMPLES_PER_PIXEL -> samplesPerPixel = readValue().first()
                TAG_STRIP_BYTE_COUNTS -> stripByteCounts = readValue()
                TAG_PLANAR_CONFIG -> planarConfig = readValue().first()
                TAG_COLORMAP -> colorMap = readValue()
                TAG_PREDICTOR -> predictor = readValue().first()
            }
        }

        require(compression == 1 || compression == 5) {
            "TIFF compression=$compression (only uncompressed/LZW supported)"
        }
        require(predictor == 1 || predictor == 2) {
            "TIFF predictor=$predictor (only none/horizontal supported)"
        }
        require(planarConfig == 1) { "TIFF planar=$planarConfig (only chunky supported)" }
        require(width > 0 && height > 0) { "TIFF empty" }

        val w = width
        val h = height
        val spp = samplesPerPixel

        val decompressedStrips = stripOffsets.indices.map { i ->
            val raw = ByteArray(stripByteCounts[i])
            System.arraycopy(tiff, stripOffsets[i], raw, 0, stripByteCounts[i])
            if (compression == 5) decompressLzw(raw) else raw
        }
        val rawBytes = ByteArray(decompressedStrips.sumOf { it.size })
        var dst = 0
        for (strip in decompressedStrips) {
            System.arraycopy(strip, 0, rawBytes, dst, strip.size)
            dst += strip.size
        }

        if (predictor == 2) {
            val rowBytes = w * spp
            @Suppress("EmptyRange")
            for (row in 0 until h) {
                val rs = row * rowBytes
                for (x in 1 until w) {
                    for (c in 0 until spp) {
                        rawBytes[rs + x * spp + c] =
                            (
                                (rawBytes[rs + (x - 1) * spp + c].toInt() and 0xFF) +
                                    (rawBytes[rs + x * spp + c].toInt() and 0xFF)
                                ).toByte()
                    }
                }
            }
        }

        return when (photometric) {
            0, 1 -> decodeGrayscale(w, h, bitsPerSample, spp, rawBytes, photometric == 0)
            2 -> decodeRgb(w, h, bitsPerSample, spp, rawBytes)
            3 -> decodePalette(w, h, bitsPerSample, spp, rawBytes, requireNotNull(colorMap) { "Palette image missing ColorMap" })
            else -> throw Exception("TIFF photometric=$photometric not supported")
        }
    }

    private fun decodeRgb(
        width: Int,
        height: Int,
        bps: IntArray,
        spp: Int,
        raw: ByteArray,
    ): RgbaImage {
        require(bps.all { it == 8 }) { "RGB TIFF: only 8bps supported, got ${bps.toList()}" }
        require(spp == 3 || spp == 4) { "RGB TIFF: spp must be 3 or 4, got $spp" }

        val hasAlpha = spp == 4
        val rgba = ByteArray(width * height * 4)
        var src = 0
        var dst = 0
        (0 until width * height).forEach { _ ->
            rgba[dst] = raw[src] // R
            rgba[dst + 1] = raw[src + 1] // G
            rgba[dst + 2] = raw[src + 2] // B
            rgba[dst + 3] = if (hasAlpha) raw[src + 3] else 0xFF.toByte()
            src += spp
            dst += 4
        }
        return RgbaImage(width, height, rgba)
    }

    private fun decodeGrayscale(
        width: Int,
        height: Int,
        bps: IntArray,
        spp: Int,
        raw: ByteArray,
        invert: Boolean,
    ): RgbaImage {
        require(bps.size == 1 && bps[0] == 8) { "Grayscale: only 8bps supported" }
        require(spp == 1 || spp == 2) { "Grayscale: spp must be 1 or 2, got $spp" }

        val hasAlpha = spp == 2
        val rgba = ByteArray(width * height * 4)
        var src = 0
        var dst = 0
        (0 until width * height).forEach { _ ->
            val v = if (invert) (raw[src].toInt() and 0xFF).inv() and 0xFF else raw[src].toInt() and 0xFF
            val b = v.toByte()
            rgba[dst] = b
            rgba[dst + 1] = b
            rgba[dst + 2] = b
            rgba[dst + 3] = if (hasAlpha) raw[src + 1] else 0xFF.toByte()
            src += spp
            dst += 4
        }
        return RgbaImage(width, height, rgba)
    }

    private fun decodePalette(
        width: Int,
        height: Int,
        bps: IntArray,
        spp: Int,
        raw: ByteArray,
        colorMap: IntArray,
    ): RgbaImage {
        require(bps.size == 1 && bps[0] == 8) { "Palette: only 8bps supported" }
        require(spp == 1) { "Palette: spp must be 1, got $spp" }
        // Palette: 3 * 2^bits entries, each uint16. Order: all reds, then all greens, then all blues
        val nColors = 1 shl bps[0]
        require(colorMap.size == 3 * nColors) {
            "ColorMap size mismatch: ${colorMap.size} vs ${3 * nColors}"
        }

        val rgba = ByteArray(width * height * 4)
        var dst = 0
        for (src in 0 until width * height) {
            val idx = raw[src].toInt() and 0xFF
            rgba[dst] = (colorMap[idx] ushr 8).toByte()
            rgba[dst + 1] = (colorMap[nColors + idx] ushr 8).toByte()
            rgba[dst + 2] = (colorMap[2 * nColors + idx] ushr 8).toByte()
            rgba[dst + 3] = 0xFF.toByte()
            dst += 4
        }
        return RgbaImage(width, height, rgba)
    }

    private fun readValues(bb: ByteBuffer, type: Int, count: Int, valueOff: Int): IntArray {
        val elemSize = when (type) {
            TYPE_BYTE, TYPE_ASCII -> 1
            TYPE_SHORT -> 2
            TYPE_LONG -> 4
            TYPE_RATIONAL -> 8
            else -> throw Exception("Unsupported TIFF type: $type")
        }
        val totalSize = elemSize * count
        val base = if (totalSize <= 4) valueOff else bb.getInt(valueOff)

        val out = IntArray(count)
        for (i in 0 until count) {
            out[i] = when (type) {
                TYPE_BYTE -> bb.get(base + i).toInt() and 0xFF
                TYPE_SHORT -> bb.getShort(base + i * 2).toInt() and 0xFFFF
                TYPE_LONG -> bb.getInt(base + i * 4)
                else -> throw Exception("Unexpected type in readValues: $type")
            }
        }
        return out
    }

    private fun decompressLzw(input: ByteArray): ByteArray {
        val out = Buffer()

        val clearCode = 256
        val eoiCode = 257

        // Dictionary: each entry is a byte sequence
        // Pre-fill 0..255 with single-byte values; 256 and 257 are reserved
        val dict = ArrayList<ByteArray>(4096)
        for (i in 0 until 256) dict.add(byteArrayOf(i.toByte()))
        dict.add(ByteArray(0)) // 256 = clear
        dict.add(ByteArray(0)) // 257 = eoi

        var bitBuf = 0L
        var bitCount = 0
        var inputPos = 0
        var codeWidth = 9
        var prevCode = -1

        while (true) {
            // Refill bit buffer until we have enough bits for one code
            while (bitCount < codeWidth) {
                if (inputPos >= input.size) {
                    // Stream ended without EOI, treat as clean end
                    return out.readByteArray()
                }
                bitBuf = (bitBuf shl 8) or (input[inputPos].toLong() and 0xFF)
                inputPos++
                bitCount += 8
            }
            // Pull MSB-first
            val code = ((bitBuf ushr (bitCount - codeWidth)) and ((1L shl codeWidth) - 1)).toInt()
            bitCount -= codeWidth

            if (code == eoiCode) break
            if (code == clearCode) {
                while (dict.size > 258) dict.removeAt(dict.size - 1)
                codeWidth = 9
                prevCode = -1
                continue
            }

            val entry: ByteArray = when {
                code < dict.size -> dict[code]
                code == dict.size && prevCode >= 0 -> {
                    // KwKwK case: code == nextAvailable
                    val prev = dict[prevCode]
                    val ext = ByteArray(prev.size + 1)
                    System.arraycopy(prev, 0, ext, 0, prev.size)
                    ext[prev.size] = prev[0]
                    ext
                }
                else -> throw Exception(
                    "LZW invalid code $code (dict size ${dict.size}, prev $prevCode)",
                )
            }
            out.write(entry)

            // Add new dictionary entry: prevEntry + entry[0]
            if (prevCode >= 0 && dict.size < 4096) {
                val prev = dict[prevCode]
                val newEntry = ByteArray(prev.size + 1)
                System.arraycopy(prev, 0, newEntry, 0, prev.size)
                newEntry[prev.size] = entry[0]
                dict.add(newEntry)

                // TIFF "early change"
                if (dict.size == (1 shl codeWidth) - 1 && codeWidth < 12) {
                    codeWidth++
                }
            }
            prevCode = code
        }
        return out.readByteArray()
    }
}
