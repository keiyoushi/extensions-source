package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import eu.kanade.tachiyomi.extension.all.manhuarm.Dialog
import eu.kanade.tachiyomi.extension.all.manhuarm.Language
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Paints translations over a WebP page without decoding it: the original VP8 bitstream is wrapped
 * unmodified as frame 1 of an animated WebP, and the dialogue text is emitted as frame 2 — a tiny
 * lossless (VP8L) image holding only black ink, white halo and transparency, alpha-blended over
 * frame 1 by the host's own WebP decoder. Frame 1 lasts 1 ms, frame 2 lasts ~4.6 h with loop
 * count 1, so the reader settles on the composited page. Only `java.base` is touched, and the
 * cost scales with the text area, not the page area.
 *
 * Degrades gracefully by construction: a viewer that ignores animation still shows frame 1 — the
 * clean, untranslated art — never a broken page.
 */
internal object AnimatedWebpOverlay {

    /**
     * Returns the animated WebP, or null when the input is not a plain lossy WebP or nothing
     * could be drawn (callers then serve the original bytes untouched).
     */
    fun compose(original: ByteArray, dialogues: List<Dialog>, language: Language): ByteArray? = try {
        composeOrNull(original, dialogues, language)
    } catch (_: Throwable) {
        null
    }

    private fun composeOrNull(original: ByteArray, dialogues: List<Dialog>, language: Language): ByteArray? {
        val base = vp8Chunk(original) ?: return null

        val mask = TextPageRenderer.renderMask(base.width, base.height, dialogues, language)
            ?: return null

        val vp8l = encodeVp8l(mask)

        // ---- mux: RIFF / WEBP / VP8X + ANIM + ANMF(base VP8) + ANMF(overlay VP8L) ----
        val body = ByteArrayOutputStream(original.size + vp8l.size + 128)

        // VP8X: Alpha + Animation flags, canvas size
        body.chunk("VP8X") {
            write(0x12) // ..IL EXAR -> Alpha (0x10) | Animation (0x02)
            writeUInt24(0)
            writeUInt24(base.width - 1)
            writeUInt24(base.height - 1)
        }

        // ANIM: transparent background, loop once
        body.chunk("ANIM") {
            writeUInt32(0) // background color BGRA
            write(1) // loop count, uint16 LE
            write(0)
        }

        // Frame 1: the original VP8 bitstream, untouched. 1 ms (decoders clamp tiny durations
        // to ~100 ms; either way the flash of untranslated art is brief).
        body.chunk("ANMF") {
            writeUInt24(0) // x/2
            writeUInt24(0) // y/2
            writeUInt24(base.width - 1)
            writeUInt24(base.height - 1)
            writeUInt24(1) // duration ms
            write(0x02) // ......BD -> no blend, do not dispose
            chunk("VP8 ") { write(base.payload) }
        }

        // Frame 2: the text overlay, alpha-blended on top, held for the uint24 maximum (~4.6 h).
        body.chunk("ANMF") {
            writeUInt24(mask.x / 2)
            writeUInt24(mask.y / 2)
            writeUInt24(mask.width - 1)
            writeUInt24(mask.height - 1)
            writeUInt24(0xFFFFFF)
            write(0x00) // alpha-blend, do not dispose
            chunk("VP8L") { write(vp8l) }
        }

        val payload = body.toByteArray()
        val out = ByteArrayOutputStream(payload.size + 12)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeUInt32(payload.size + 4)
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        out.write(payload)
        return out.toByteArray()
    }

    // ============================ source container ============================

    private class Vp8Frame(val width: Int, val height: Int, val payload: ByteArray)

    /**
     * Extracts the `VP8 ` (lossy) chunk and its keyframe dimensions from a simple RIFF/WEBP
     * container. Returns null for VP8L, already-extended (VP8X) files and anything malformed —
     * the site only serves simple lossy stills.
     */
    private fun vp8Chunk(bytes: ByteArray): Vp8Frame? {
        if (bytes.size < 30) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (b.int != RIFF) return null
        b.int // RIFF payload size, unused
        if (b.int != WEBP) return null
        while (b.remaining() >= 8) {
            val fourcc = b.int
            val size = b.int
            if (size < 0 || size > b.remaining()) return null
            if (fourcc == VP8_) {
                if (size < 10) return null
                val at = b.position()
                // Keyframe dimensions live in the uncompressed header: bytes 6..9 after the tag.
                val w = (bytes[at + 7].toInt() and 0x3f shl 8) or (bytes[at + 6].toInt() and 0xff)
                val h = (bytes[at + 9].toInt() and 0x3f shl 8) or (bytes[at + 8].toInt() and 0xff)
                if (w <= 0 || h <= 0 || w > 8192 || h > 16383) return null
                return Vp8Frame(w, h, bytes.copyOfRange(at, at + size))
            }
            b.position(b.position() + size + (size and 1))
        }
        return null
    }

    // ============================ VP8L encoding ============================

    /**
     * Encodes an antialiased greyscale mask (see [TextPageRenderer.renderMask]) as a VP8L
     * lossless bitstream. The SUBTRACT_GREEN transform makes red and blue constant zero (their
     * single-symbol codes consume no bits), so each pixel costs only its Huffman-coded grey and
     * alpha symbols — 16 quantised levels each, dominated by short codes for transparent pixels.
     * Still no LZ77 and no color cache.
     */
    private fun encodeVp8l(mask: TextPageRenderer.OverlayMask): ByteArray {
        val n = mask.width * mask.height
        val bits = BitWriter(n / 2 + 256)
        bits.byte(0x2f) // signature
        bits.write(mask.width - 1, 14)
        bits.write(mask.height - 1, 14)
        bits.write(1, 1) // alpha_is_used
        bits.write(0, 3) // version
        bits.write(1, 1) // transform present
        bits.write(2, 2) // SUBTRACT_GREEN (no transform data)
        bits.write(0, 1) // no more transforms
        bits.write(0, 1) // no color cache
        bits.write(0, 1) // one prefix code group for the whole image

        val greyCode = PrefixCode.of(mask.grey)
        val alphaCode = PrefixCode.of(mask.alpha)

        greyCode.writeTo(bits) // green
        writeSingleSymbolCode(bits) // red: always 0 after subtract-green
        writeSingleSymbolCode(bits) // blue: always 0 after subtract-green
        alphaCode.writeTo(bits) // alpha
        writeSingleSymbolCode(bits) // distance: no backward references

        for (p in 0 until n) {
            greyCode.emit(bits, mask.grey[p].toInt() and 0xFF)
            alphaCode.emit(bits, mask.alpha[p].toInt() and 0xFF)
        }
        return bits.toByteArray()
    }

    /** A 1-symbol "simple" prefix code for symbol 0; reads from it consume no bits. */
    private fun writeSingleSymbolCode(bits: BitWriter) {
        bits.write(1, 1) // simple code length code
        bits.write(0, 1) // one symbol
        bits.write(0, 1) // coded in 1 bit
        bits.write(0, 1) // symbol0 = 0
    }

    /** A canonical prefix code over byte symbols, built from their frequencies. */
    private class PrefixCode private constructor(
        private val lengths: IntArray,
        private val codes: IntArray,
        private val used: IntArray,
    ) {
        fun emit(bits: BitWriter, symbol: Int) {
            if (used.size > 1) bits.code(codes[symbol], lengths[symbol])
        }

        fun writeTo(bits: BitWriter) {
            if (used.size <= 2) {
                bits.write(1, 1) // simple code length code
                bits.write(used.size - 1, 1)
                bits.write(1, 1) // first symbol coded in 8 bits
                bits.write(used[0], 8)
                if (used.size == 2) bits.write(used[1], 8)
                return
            }

            bits.write(0, 1) // normal code length code

            // Run-length encode the code lengths of symbols 0..maxUsed: literals for lengths,
            // codes 17 (3-10 zeros) and 18 (11-138 zeros) for the gaps between used symbols.
            class Token(val symbol: Int, val extra: Int, val extraBits: Int)

            val maxUsed = used.last()
            val tokens = mutableListOf<Token>()
            var i = 0
            while (i <= maxUsed) {
                if (lengths[i] != 0) {
                    tokens += Token(lengths[i], 0, 0)
                    i++
                    continue
                }
                var run = 0
                while (i + run <= maxUsed && lengths[i + run] == 0) run++
                i += run
                while (run > 0) {
                    when {
                        run >= 11 -> {
                            val take = minOf(run, 138)
                            tokens += Token(18, take - 11, 7)
                            run -= take
                        }
                        run >= 3 -> {
                            tokens += Token(17, run - 3, 3)
                            run = 0
                        }
                        else -> {
                            repeat(run) { tokens += Token(0, 0, 0) }
                            run = 0
                        }
                    }
                }
            }

            // The tokens themselves are prefix-coded: use a flat code over the distinct token
            // symbols, padded with unused ones to a power of two so the tree is complete.
            val distinct = tokens.map { it.symbol }.distinct().sorted().toMutableList()
            var flatBits = 1
            while ((1 shl flatBits) < distinct.size) flatBits++
            var filler = 0
            while (distinct.size < (1 shl flatBits)) {
                while (filler in distinct) filler++
                distinct += filler
            }
            distinct.sort()
            val clLengths = IntArray(19)
            distinct.forEach { clLengths[it] = flatBits }
            val clCodes = canonicalCodes(clLengths)

            bits.write(15, 4) // num_code_lengths = 4 + 15 = 19, i.e. all slots
            CODE_LENGTH_CODE_ORDER.forEach { bits.write(clLengths[it], 3) }
            // max_symbol caps the number of TOKENS the decoder reads (libwebp decrements it
            // once per token, run or literal alike), so it must be the token count — not the
            // covered symbol range.
            bits.write(1, 1)
            bits.write(3, 3) // 8-bit field
            bits.write(tokens.size - 2, 8) // max_symbol = 2 + (tokens.size - 2)

            tokens.forEach { token ->
                bits.code(clCodes[token.symbol], flatBits)
                if (token.extraBits > 0) bits.write(token.extra, token.extraBits)
            }
        }

        companion object {
            fun of(values: ByteArray): PrefixCode {
                val freq = IntArray(256)
                values.forEach { freq[it.toInt() and 0xFF]++ }
                val used = (0..255).filter { freq[it] > 0 }.toIntArray()

                val lengths = IntArray(256)
                if (used.size == 2) {
                    lengths[used[0]] = 1
                    lengths[used[1]] = 1
                } else if (used.size > 2) {
                    huffmanLengths(freq, used, lengths)
                }
                return PrefixCode(lengths, canonicalCodes(lengths), used)
            }

            /** Plain Huffman over the used symbols; depth is bounded by used.size - 1 <= 15. */
            private fun huffmanLengths(freq: IntArray, used: IntArray, lengths: IntArray) {
                class Node(var weight: Long, val symbols: MutableList<Int>)

                val nodes = used.mapTo(mutableListOf()) { Node(freq[it].toLong(), mutableListOf(it)) }
                while (nodes.size > 1) {
                    nodes.sortBy { it.weight }
                    val a = nodes.removeAt(0)
                    val b = nodes.removeAt(0)
                    (a.symbols + b.symbols).forEach { lengths[it]++ }
                    a.weight += b.weight
                    a.symbols += b.symbols
                    nodes += a
                }
            }

            /** DEFLATE-style canonical code assignment from code lengths. */
            private fun canonicalCodes(lengths: IntArray): IntArray {
                val codes = IntArray(lengths.size)
                val maxLen = lengths.max()
                if (maxLen == 0) return codes
                val countPerLength = IntArray(maxLen + 1)
                lengths.forEach { if (it > 0) countPerLength[it]++ }
                val nextCode = IntArray(maxLen + 1)
                var code = 0
                for (len in 1..maxLen) {
                    code = (code + countPerLength[len - 1]) shl 1
                    nextCode[len] = code
                }
                for (symbol in lengths.indices) {
                    if (lengths[symbol] > 0) codes[symbol] = nextCode[lengths[symbol]]++
                }
                return codes
            }
        }
    }

    /** LSB-first bit packer, per the VP8L bitstream convention. */
    private class BitWriter(expectedSize: Int) {
        private val out = ByteArrayOutputStream(expectedSize)
        private var acc = 0
        private var used = 0

        fun write(value: Int, count: Int) {
            acc = acc or (value shl used)
            used += count
            while (used >= 8) {
                out.write(acc and 0xFF)
                acc = acc ushr 8
                used -= 8
            }
        }

        fun byte(value: Int) = write(value, 8)

        /** Emits a canonical prefix code MSB first, as the decoder's tree walk consumes it. */
        fun code(code: Int, length: Int) {
            for (i in length - 1 downTo 0) write((code ushr i) and 1, 1)
        }

        fun toByteArray(): ByteArray {
            if (used > 0) {
                out.write(acc and 0xFF)
                acc = 0
                used = 0
            }
            return out.toByteArray()
        }
    }

    // ============================ RIFF helpers ============================

    private inline fun ByteArrayOutputStream.chunk(tag: String, content: ByteArrayOutputStream.() -> Unit) {
        val body = ByteArrayOutputStream().apply(content).toByteArray()
        write(tag.toByteArray(Charsets.US_ASCII))
        writeUInt32(body.size)
        write(body)
        if (body.size and 1 == 1) write(0) // RIFF padding
    }

    private fun ByteArrayOutputStream.writeUInt24(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeUInt32(value: Int) {
        writeUInt24(value)
        write((value ushr 24) and 0xFF)
    }

    private val CODE_LENGTH_CODE_ORDER = intArrayOf(17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

    private const val RIFF = 'R'.code or ('I'.code shl 8) or ('F'.code shl 16) or ('F'.code shl 24)
    private const val WEBP = 'W'.code or ('E'.code shl 8) or ('B'.code shl 16) or ('P'.code shl 24)
    private const val VP8_ = 'V'.code or ('P'.code shl 8) or ('8'.code shl 16) or (' '.code shl 24)
}
