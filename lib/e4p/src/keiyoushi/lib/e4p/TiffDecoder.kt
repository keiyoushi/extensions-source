package keiyoushi.lib.e4p

import keiyoushi.utils.readIntLittleEndian
import keiyoushi.utils.readUShortLittleEndian
import okio.Buffer
import okio.BufferedSink

// https://cs.opensource.google/go/x/image
// https://pkg.go.dev/golang.org/x/image/tiff/lzw
object TiffDecoder {
    class Argb8888(val width: Int, val height: Int, val pixels: IntArray)

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

    fun decode(tiff: ByteArray): Argb8888 {
        require(tiff.size >= 8) { "TIFF too small: ${tiff.size}" }
        require(tiff[0] == 0x49.toByte() && tiff[1] == 0x49.toByte()) { "TIFF must be little-endian" }
        require(tiff[2] == 0x2A.toByte() && tiff[3] == 0x00.toByte()) { "TIFF magic mismatch" }

        val ifdOffset = tiff.readIntLittleEndian(4)
        val numEntries = tiff.readUShortLittleEndian(ifdOffset)

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
            val tag = tiff.readUShortLittleEndian(entryOff)
            val type = tiff.readUShortLittleEndian(entryOff + 2)
            val count = tiff.readIntLittleEndian(entryOff + 4)
            val valueOff = entryOff + 8

            val readValue = { readValues(tiff, type, count, valueOff) }

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

        val rawBytes: ByteArray = if (stripOffsets.size == 1 && compression == 1) {
            val off = stripOffsets[0]
            tiff.copyOfRange(off, off + stripByteCounts[0])
        } else {
            val out = Buffer()
            for (i in stripOffsets.indices) {
                val start = stripOffsets[i]
                val end = start + stripByteCounts[i]
                if (compression == 5) {
                    decompressLzw(out, tiff, start, end)
                } else {
                    out.write(tiff, start, end - start)
                }
            }
            out.readByteArray()
        }

        if (predictor == 2) {
            val rowBytes = w * spp
            for (row in 0 until h) {
                val rowStart = row * rowBytes
                for (i in rowStart + spp until rowStart + rowBytes) {
                    rawBytes[i] = (rawBytes[i] + rawBytes[i - spp]).toByte()
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
    ): Argb8888 {
        require(bps.all { it == 8 }) { "RGB TIFF: only 8bps supported, got ${bps.toList()}" }
        require(spp == 3 || spp == 4) { "RGB TIFF: spp must be 3 or 4, got $spp" }

        val pixels = IntArray(width * height)
        var src = 0
        for (p in 0 until width * height) {
            val r = raw[src].toInt() and 0xFF
            val g = raw[src + 1].toInt() and 0xFF
            val b = raw[src + 2].toInt() and 0xFF
            pixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            src += spp
        }
        return Argb8888(width, height, pixels)
    }

    private fun decodeGrayscale(
        width: Int,
        height: Int,
        bps: IntArray,
        spp: Int,
        raw: ByteArray,
        invert: Boolean,
    ): Argb8888 {
        require(bps.size == 1 && bps[0] == 8) { "Grayscale: only 8bps supported" }
        require(spp == 1 || spp == 2) { "Grayscale: spp must be 1 or 2, got $spp" }

        val pixels = IntArray(width * height)
        var src = 0
        for (p in 0 until width * height) {
            var v = raw[src].toInt() and 0xFF
            if (invert) v = v.inv() and 0xFF
            pixels[p] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            src += spp
        }
        return Argb8888(width, height, pixels)
    }

    private fun decodePalette(
        width: Int,
        height: Int,
        bps: IntArray,
        spp: Int,
        raw: ByteArray,
        colorMap: IntArray,
    ): Argb8888 {
        require(bps.size == 1 && bps[0] == 8) { "Palette: only 8bps supported" }
        require(spp == 1) { "Palette: spp must be 1, got $spp" }
        // Palette: 3 * 2^bits entries, each uint16. Order: all reds, then all greens, then all blues
        val nColors = 1 shl bps[0]
        require(colorMap.size == 3 * nColors) {
            "ColorMap size mismatch: ${colorMap.size} vs ${3 * nColors}"
        }

        val pixels = IntArray(width * height)
        for (p in 0 until width * height) {
            val idx = raw[p].toInt() and 0xFF
            val r = (colorMap[idx] ushr 8) and 0xFF
            val g = (colorMap[nColors + idx] ushr 8) and 0xFF
            val b = (colorMap[2 * nColors + idx] ushr 8) and 0xFF
            pixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Argb8888(width, height, pixels)
    }

    private fun readValues(tiff: ByteArray, type: Int, count: Int, valueOff: Int): IntArray {
        val elemSize = when (type) {
            TYPE_BYTE, TYPE_ASCII -> 1
            TYPE_SHORT -> 2
            TYPE_LONG -> 4
            TYPE_RATIONAL -> 8
            else -> throw Exception("Unsupported TIFF type: $type")
        }
        val totalSize = elemSize * count
        val base = if (totalSize <= 4) valueOff else tiff.readIntLittleEndian(valueOff)

        val out = IntArray(count)
        for (i in 0 until count) {
            out[i] = when (type) {
                TYPE_BYTE -> tiff[base + i].toInt() and 0xFF
                TYPE_SHORT -> tiff.readUShortLittleEndian(base + i * 2)
                TYPE_LONG -> tiff.readIntLittleEndian(base + i * 4)
                else -> throw Exception("Unexpected type in readValues: $type")
            }
        }
        return out
    }

    private fun decompressLzw(sink: BufferedSink, input: ByteArray, start: Int, end: Int) {
        val clearCode = 256
        val eoiCode = 257

        // Dictionary as prefix-link + suffix-byte per code; strings rebuilt via the stack
        val prefix = IntArray(4096)
        val suffix = ByteArray(4096)
        val stack = ByteArray(4096)
        for (i in 0 until 256) suffix[i] = i.toByte()

        var nextCode = 258
        var codeWidth = 9
        var prevCode = -1
        var firstByte = 0

        var bitBuf = 0L
        var bitCount = 0
        var inputPos = start

        while (true) {
            // Refill bit buffer until we have enough bits for one code
            while (bitCount < codeWidth) {
                if (inputPos >= end) {
                    // Stream ended without EOI, treat as clean end
                    return
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
                nextCode = 258
                codeWidth = 9
                prevCode = -1
                continue
            }

            var sp = 0
            when {
                code < nextCode -> {
                    var c = code
                    while (c >= 256) {
                        stack[sp++] = suffix[c]
                        c = prefix[c]
                    }
                    firstByte = c
                    stack[sp++] = c.toByte()
                }
                code == nextCode && prevCode >= 0 -> {
                    // KwKwK: string == previous string + its own first byte
                    stack[sp++] = firstByte.toByte()
                    var c = prevCode
                    while (c >= 256) {
                        stack[sp++] = suffix[c]
                        c = prefix[c]
                    }
                    firstByte = c
                    stack[sp++] = c.toByte()
                }
                else -> throw Exception("LZW invalid code $code (next $nextCode, prev $prevCode)")
            }

            // stack holds the string reversed
            var lo = 0
            var hi = sp - 1
            while (lo < hi) {
                val t = stack[lo]
                stack[lo] = stack[hi]
                stack[hi] = t
                lo++
                hi--
            }
            sink.write(stack, 0, sp)

            // New entry = prevString + firstByte(currentString); TIFF "early change" width bump
            if (prevCode >= 0 && nextCode < 4096) {
                prefix[nextCode] = prevCode
                suffix[nextCode] = firstByte.toByte()
                nextCode++
                if (nextCode == (1 shl codeWidth) - 1 && codeWidth < 12) {
                    codeWidth++
                }
            }
            prevCode = code
        }
    }
}
