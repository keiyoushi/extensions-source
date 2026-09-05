package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import eu.kanade.tachiyomi.extension.all.manhuarm.Dialog
import eu.kanade.tachiyomi.extension.all.manhuarm.Language
import org.jsoup.Jsoup
import java.util.zip.GZIPInputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Renders dialogue using only `java.base`, for hosts that provide no graphics stack at all
 * (`android.graphics` stubbed, no `java.awt`, no `javax.imageio`). Glyphs come from a
 * pre-rasterised 96 px atlas bundled as a resource (rendered from the bundled Comic Neue Bold,
 * OFL licensed — see assets/fonts), scaled down with area-averaged resampling to the size the
 * user asked for (the font-size preference, in points, like the Android renderer) or smaller
 * when the dialogue must shrink to fit its box.
 */
internal object TextPageRenderer {

    private const val LINE_GAP = 2

    /** Pixel size the atlas was rasterised at (see genfont.py). */
    private const val NOMINAL_PX = 96f

    /** Preference points to pixels; mirrors ComposedImageInterceptor.SCALED_DENSITY. */
    private const val PT_TO_PX = 1 / 0.75f

    /** Don't shrink text below this pixel size; better to overflow slightly than be unreadable. */
    private const val MIN_TEXT_PX = 13f

    /**
     * An antialiased greyscale text mask positioned on the page: per pixel a grey level and an
     * alpha, both quantised to multiples of 17 (16 levels), straight (non-premultiplied) alpha.
     * Fully transparent pixels carry grey 255 so the encoder's grey histogram stays skewed.
     */
    class OverlayMask(val x: Int, val y: Int, val width: Int, val height: Int, val grey: ByteArray, val alpha: ByteArray)

    /**
     * Rasterises every dialogue into an antialiased mask covering exactly the region the
     * laid-out text occupies (text may overflow its OCR box, e.g. a single word wider than the
     * box, so the extents come from the placed lines, not the boxes). Black ink is composited
     * over the white halo, keeping the glyphs' full coverage as alpha so edges stay smooth.
     * The origin is even-aligned, as required by ANMF frame offsets. Returns null when nothing
     * was drawn.
     */
    fun renderMask(
        canvasW: Int,
        canvasH: Int,
        dialogues: List<Dialog>,
        language: Language,
    ): OverlayMask? {
        val font = atlas ?: return null

        class PlacedLine(val raster: Atlas.Raster, val x: Int, val y: Int)

        val targetScale = minOf(1f, language.fontSize * PT_TO_PX / NOMINAL_PX)
        val floorScale = minOf(targetScale, MIN_TEXT_PX / NOMINAL_PX)

        // The OCR box bounds the original (compact) text and is usually the binding constraint —
        // bubbles have plenty of padding around it. Asking for a font size above the default is
        // taken as consent to overfill the box proportionally (up to +50% at 88 pt), so the
        // preference has a visible effect instead of being eaten by the shrink-to-fit loop.
        val slack = 1f + (language.fontSize - 28).coerceIn(0, 60) / 120f

        val placed = mutableListOf<PlacedLine>()
        dialogues.forEach { dialog ->
            val text = dialog.getTextBy(language).stripHtml().normalise()
            if (text.isBlank()) return@forEach

            val boxW = (dialog.width * slack).toInt()
            val boxH = (dialog.height * slack).toInt()
            if (boxW < 14 || boxH < 14) return@forEach

            // Start at the preferred size and shrink until the block fits the box, like the
            // Android renderer does with its StaticLayout loop. Lines longer than the box (an
            // unwrappable word) may overhang, but no further than the box width again on each
            // side, so large font settings can't push a word across the whole page.
            var scale = targetScale
            var lines = font.wrap(text, boxW, scale)
            var lineHeight = font.cellH * scale + LINE_GAP
            fun widestLine() = lines.maxOf { font.measure(it) } * scale
            while ((lines.size * lineHeight > boxH || widestLine() > boxW * 2f) && scale > floorScale) {
                scale = maxOf(floorScale, scale * 0.85f)
                lines = font.wrap(text, boxW, scale)
                lineHeight = font.cellH * scale + LINE_GAP
            }

            val blockH = (lines.size * lineHeight).roundToInt()
            var y = (dialog.centerY - blockH / 2f)
                .coerceIn(3f, maxOf(3f, canvasH - blockH - 3f))
            lines.forEach { line ->
                val raster = font.rasteriseLine(line, scale)
                if (raster != null) {
                    // keep every line fully on the page
                    val x = (dialog.centerX.toInt() - raster.width / 2)
                        .coerceIn(3, maxOf(3, canvasW - raster.width - 3))
                    placed += PlacedLine(raster, x, y.roundToInt())
                }
                y += lineHeight
            }
        }
        if (placed.isEmpty()) return null

        val margin = 3 // room for the halo ring
        var left = canvasW
        var top = canvasH
        var right = 0
        var bottom = 0
        placed.forEach { line ->
            left = minOf(left, line.x - margin)
            top = minOf(top, line.y - margin)
            right = maxOf(right, line.x + line.raster.width + margin)
            bottom = maxOf(bottom, line.y + line.raster.height + margin)
        }
        left = left.coerceAtLeast(0) and 1.inv()
        top = top.coerceAtLeast(0) and 1.inv()
        right = right.coerceAtMost(canvasW)
        bottom = bottom.coerceAtMost(canvasH)
        val width = right - left
        val height = bottom - top
        if (width < 4 || height < 4) return null

        val ink = ByteArray(width * height)
        val halo = ByteArray(width * height)
        placed.forEach { line ->
            val x = line.x - left
            val y = line.y - top
            HALO_OFFSETS.forEach { (dx, dy) ->
                blit(halo, width, height, line.raster, x + dx, y + dy)
            }
            blit(ink, width, height, line.raster, x, y)
        }

        // Composite black ink over white halo: alpha = i + h(1-i), grey = h(1-i) / alpha.
        val grey = ByteArray(width * height)
        val alpha = ByteArray(width * height)
        for (p in 0 until width * height) {
            val i = ink[p].toInt() and 0xFF
            val h = halo[p].toInt() and 0xFF
            val a = i + h * (255 - i) / 255
            grey[p] = when (a) {
                0 -> 255 // free choice; matches the halo so the histogram stays skewed
                else -> quantise(h * (255 - i) / a)
            }.toByte()
            alpha[p] = quantise(a).toByte()
        }
        return OverlayMask(left, top, width, height, grey, alpha)
    }

    /** Snaps a 0..255 value to the nearest multiple of 17 (16 levels). */
    private fun quantise(value: Int): Int = (value + 8) / 17 * 17

    /** Max-blends a rasterised line into a single-channel buffer at (x, y). */
    private fun blit(dst: ByteArray, dstW: Int, dstH: Int, src: Atlas.Raster, x: Int, y: Int) {
        for (row in 0 until src.height) {
            val py = y + row
            if (py < 0 || py >= dstH) continue
            val srcBase = row * src.width
            val dstBase = py * dstW
            for (col in 0 until src.width) {
                val px = x + col
                if (px < 0 || px >= dstW) continue
                val v = src.data[srcBase + col].toInt() and 0xFF
                if (v > (dst[dstBase + px].toInt() and 0xFF)) dst[dstBase + px] = v.toByte()
            }
        }
    }

    private fun String.stripHtml(): String = when {
        contains('<') -> Jsoup.parse(this).text()
        else -> this
    }

    private val HALO_OFFSETS = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0, 1 to 0,
        -1 to 1, 0 to 1, 1 to 1,
        -2 to 0, 2 to 0, 0 to -2, 0 to 2,
    )

    /** Fold characters the ASCII atlas cannot represent onto ones it can. */
    private fun String.normalise(): String {
        val out = StringBuilder(length)
        for (ch in this) {
            when (ch) {
                '‘', '’', 'ʼ' -> out.append('\'')
                '“', '”' -> out.append('"')
                '–', '—' -> out.append('-')
                '…' -> out.append("...")
                ' ' -> out.append(' ')
                '\n', '\r', '\t' -> out.append(' ')
                else -> if (ch.code in 32..126) out.append(ch) else out.append('?')
            }
        }
        return out.toString().replace(MULTI_SPACE, " ").trim()
    }

    // ============================ font atlas ============================

    private class Atlas(blob: ByteArray) {
        val cellW = blob[5].toInt() and 0xFF
        val cellH = blob[6].toInt() and 0xFF
        private val first = blob[8].toInt() and 0xFF
        private val count = blob[9].toInt() and 0xFF
        private val advances = IntArray(count) { blob[HEADER + it].toInt() and 0xFF }
        private val pixels = blob
        private val glyphBase = HEADER + count
        private val glyphSize = cellW * cellH

        class Raster(val width: Int, val height: Int, val data: ByteArray)

        private fun index(ch: Char): Int = (ch.code - first).takeIf { it in 0 until count } ?: -1

        /** Advance in atlas units (pixels at the atlas' nominal size). */
        fun advance(ch: Char): Int = index(ch).let { if (it < 0) cellW / 2 else advances[it] }

        fun measure(text: String): Int = text.sumOf { advance(it) }

        /** Word-wraps so each line fits maxWidth pixels when drawn at [scale]. */
        fun wrap(text: String, maxWidth: Int, scale: Float): List<String> {
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            var width = 0
            for (word in text.split(' ')) {
                if (word.isEmpty()) continue
                val wordWidth = measure(word)
                val spaceWidth = if (line.isEmpty()) 0 else advance(' ')
                if (line.isNotEmpty() && (width + spaceWidth + wordWidth) * scale > maxWidth) {
                    lines += line.toString()
                    line = StringBuilder(word)
                    width = wordWidth
                } else {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(word)
                    width += spaceWidth + wordWidth
                }
            }
            if (line.isNotEmpty()) lines += line.toString()
            return lines.ifEmpty { listOf("") }
        }

        /**
         * Rasterises one line of text at [scale] (downscale only) into a coverage buffer,
         * area-averaging the atlas glyphs so edges stay antialiased at any size.
         */
        fun rasteriseLine(text: String, scale: Float): Raster? {
            val width = ceil(measure(text) * scale).toInt()
            val height = ceil(cellH * scale).toInt()
            if (width <= 0 || height <= 0) return null
            val out = ByteArray(width * height)
            var pen = 0
            for (ch in text) {
                val gi = index(ch)
                if (gi >= 0) drawGlyph(out, width, height, pen * scale, gi, scale)
                pen += advance(ch)
            }
            return Raster(width, height, out)
        }

        private fun drawGlyph(out: ByteArray, outW: Int, outH: Int, xOffPx: Float, glyph: Int, scale: Float) {
            val src = glyphBase + glyph * glyphSize
            val x0 = xOffPx.roundToInt()
            val gw = ceil(cellW * scale).toInt()
            for (row in 0 until outH) {
                val sy0 = row / scale
                if (sy0 >= cellH) break
                val sy1 = minOf(cellH.toFloat(), (row + 1) / scale)
                val rowBase = row * outW
                for (col in 0 until gw) {
                    val px = x0 + col
                    if (px < 0 || px >= outW) continue
                    val sx0 = col / scale
                    if (sx0 >= cellW) break
                    val sx1 = minOf(cellW.toFloat(), (col + 1) / scale)

                    var total = 0f
                    var area = 0f
                    var iy = floor(sy0).toInt()
                    while (iy < sy1) {
                        val wy = minOf(sy1, iy + 1f) - maxOf(sy0, iy.toFloat())
                        val srcRow = src + iy * cellW
                        var ix = floor(sx0).toInt()
                        while (ix < sx1) {
                            val wx = minOf(sx1, ix + 1f) - maxOf(sx0, ix.toFloat())
                            total += (pixels[srcRow + ix].toInt() and 0xFF) * wy * wx
                            area += wy * wx
                            ix++
                        }
                        iy++
                    }
                    if (area <= 0f) continue
                    val alpha = (total / area).toInt().coerceIn(0, 255)
                    if (alpha > (out[rowBase + px].toInt() and 0xFF)) out[rowBase + px] = alpha.toByte()
                }
            }
        }

        companion object {
            const val HEADER = 10
        }
    }

    private val atlas: Atlas? by lazy {
        try {
            // Gzipped, but deliberately not named .gz: AGP's asset merger decompresses
            // and renames *.gz assets during packaging.
            val blob = TextPageRenderer::class.java.classLoader!!
                .getResourceAsStream("assets/fonts/atlas_96.bin")!!
                .use { GZIPInputStream(it).readBytes() }
            require(blob.size > Atlas.HEADER && String(blob, 0, 4) == "MRMF") { "bad atlas" }
            Atlas(blob)
        } catch (_: Throwable) {
            null
        }
    }

    private val MULTI_SPACE = Regex(" {2,}")
}
