package eu.kanade.tachiyomi.extension.zh.baozimanhua

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

internal object BannerChecker {

    private const val BANNER_W = BANNER_WIDTH
    private const val BANNER_H = BANNER_HEIGHT
    private const val OLD_BANNER_W = OLDBANNER_WIDTH
    private const val OLD_BANNER_H = OLDBANNER_HEIGHT
    private const val NARROW_BANNER_W = NARROWBANNER_WIDTH
    private const val NARROW_BANNER_H = NARROWBANNER_HEIGHT

    // When the narrow banner appears at the bottom it is cropped: only the
    // top 160 rows are visible.
    private const val NARROW_BANNER_BOTTOM_BASE_H = 160

    private const val SIGNATURE_COLS = 16
    private const val MAX_AVG_ERROR_PER_COMPONENT = 6

    private class BannerSignature(
        val width: Int,
        val height: Int,
        val rowAverages: IntArray, // size = height * 3
        val sampleXPositions: IntArray,
    )

    private fun sampleXPositions(width: Int): IntArray {
        val startCol = (width * 0.2).toInt()
        val endCol = (width * 0.8).toInt()
        val step = ((endCol - startCol).toFloat() / SIGNATURE_COLS).coerceAtLeast(1f)
        return IntArray(SIGNATURE_COLS) { index ->
            (startCol + (index * step).toInt()).coerceIn(0, width - 1)
        }
    }

    private val bannerSig by lazy {
        buildSignature(BANNER_BASE64, BANNER_W, BANNER_H)
    }
    private val oldBannerSig by lazy {
        buildSignature(OLDBANNER_BASE64, OLD_BANNER_W, OLD_BANNER_H)
    }
    private val narrowBannerSig by lazy {
        buildSignature(NARROWBANNER_BASE64, NARROW_BANNER_W, NARROW_BANNER_H)
    }

    /**
     * Sliced version of [narrowBannerSig] containing only the first
     * [NARROW_BANNER_BOTTOM_BASE_H] rows.  Built lazily from the full signature
     * so we decode the Base64 only once.
     */
    private val narrowBannerBottomSig by lazy {
        val full = narrowBannerSig
        val slicedAvgs = full.rowAverages.copyOf(NARROW_BANNER_BOTTOM_BASE_H * 3)
        BannerSignature(
            full.width,
            NARROW_BANNER_BOTTOM_BASE_H,
            slicedAvgs,
            full.sampleXPositions,
        )
    }

    // ---------------------------------------------------------------------------
    // Scaling Helper
    // ---------------------------------------------------------------------------

    private fun scaledHeight(baseH: Int, targetW: Int): Int {
        // scale proportional to width
        return (baseH * (targetW.toFloat() / NARROW_BANNER_W)).roundToInt().coerceAtLeast(1)
    }

    private val narrowSigCache = ConcurrentHashMap<Int, BannerSignature>()
    private val narrowBottomSigCache = ConcurrentHashMap<Int, BannerSignature>()

    private fun scaleSignature(source: BannerSignature, targetW: Int, targetH: Int): BannerSignature {
        val averages = IntArray(targetH * 3)
        for (row in 0 until targetH) {
            val sourceY = ((row + 0.5) * source.height / targetH - 0.5)
                .coerceIn(0.0, (source.height - 1).toDouble())
            val rowBefore = sourceY.toInt()
            val rowAfter = (rowBefore + 1).coerceAtMost(source.height - 1)
            val fraction = sourceY - rowBefore

            for (component in 0 until 3) {
                val before = source.rowAverages[rowBefore * 3 + component]
                val after = source.rowAverages[rowAfter * 3 + component]
                averages[row * 3 + component] = (before + (after - before) * fraction).roundToInt()
            }
        }
        return BannerSignature(targetW, targetH, averages, sampleXPositions(targetW))
    }

    private fun narrowSigForWidth(targetW: Int): BannerSignature {
        if (targetW == NARROW_BANNER_W) return narrowBannerSig
        return narrowSigCache.getOrPut(targetW) {
            scaleSignature(narrowBannerSig, targetW, scaledHeight(NARROW_BANNER_H, targetW))
        }
    }

    private fun narrowBottomSigForWidth(targetW: Int): BannerSignature {
        if (targetW == NARROW_BANNER_W) return narrowBannerBottomSig

        return narrowBottomSigCache.getOrPut(targetW) {
            val full = narrowSigForWidth(targetW)
            val bottomHeight = scaledHeight(NARROW_BANNER_BOTTOM_BASE_H, targetW)
                .coerceAtMost(full.height)
            BannerSignature(
                full.width,
                bottomHeight,
                full.rowAverages.copyOf(bottomHeight * 3),
                full.sampleXPositions,
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Signature building
    // ---------------------------------------------------------------------------

    private fun buildSignature(
        base64: String,
        expectedW: Int,
        expectedH: Int,
    ): BannerSignature {
        val buffer = Base64.decode(base64, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(buffer, 0, buffer.size)
            ?: error("Unable to decode banner")
        if (bmp.width < expectedW || bmp.height < expectedH) {
            bmp.recycle()
            error("Bitmap ${bmp.width}x${bmp.height} is smaller than expected ${expectedW}x$expectedH")
        }
        return try {
            createSignature(bmp, 0, 0, expectedW, expectedH)
        } finally {
            bmp.recycle()
        }
    }

    private fun createSignature(
        bmp: Bitmap,
        srcX: Int,
        srcY: Int,
        regionW: Int,
        regionH: Int,
    ): BannerSignature {
        val avgs = IntArray(regionH * 3)

        val sampleXPositions = sampleXPositions(regionW)

        val rowPixels = IntArray(regionW)
        for (row in 0 until regionH) {
            bmp.getPixels(rowPixels, 0, regionW, srcX, srcY + row, regionW, 1)
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (col in sampleXPositions) {
                val px = rowPixels[col]
                sumR += (px shr 16) and 0xFF
                sumG += (px shr 8) and 0xFF
                sumB += px and 0xFF
            }
            val idx = row * 3
            avgs[idx] = (sumR / SIGNATURE_COLS).toInt()
            avgs[idx + 1] = (sumG / SIGNATURE_COLS).toInt()
            avgs[idx + 2] = (sumB / SIGNATURE_COLS).toInt()
        }
        return BannerSignature(regionW, regionH, avgs, sampleXPositions)
    }

    // ---------------------------------------------------------------------------
    // Matching
    // ---------------------------------------------------------------------------

    private fun matchesSignature(
        image: Bitmap,
        imgX: Int,
        imgY: Int,
        sig: BannerSignature,
        rowPixels: IntArray,
    ): Boolean {
        val regionW = sig.width
        val regionH = sig.height

        if (imgX < 0 || imgY < 0) return false
        if (imgX + regionW > image.width || imgY + regionH > image.height) {
            return false
        }

        val refAvg = sig.rowAverages
        val totalThreshold = regionH * 3 * MAX_AVG_ERROR_PER_COMPONENT
        val sampleXPositions = sig.sampleXPositions

        // Phase 1: Quick scan — every 4th row for fast rejection
        val quickStride = 4
        var quickDiff = 0
        val quickThreshold = totalThreshold / (quickStride / 2)
        var quickRow = 0
        while (quickRow < regionH) {
            image.getPixels(rowPixels, 0, regionW, imgX, imgY + quickRow, regionW, 1)
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (col in sampleXPositions) {
                val px = rowPixels[col]
                sumR += (px shr 16) and 0xFF
                sumG += (px shr 8) and 0xFF
                sumB += px and 0xFF
            }
            val idx = quickRow * 3
            quickDiff += abs((sumR / SIGNATURE_COLS).toInt() - refAvg[idx])
            quickDiff += abs((sumG / SIGNATURE_COLS).toInt() - refAvg[idx + 1])
            quickDiff += abs((sumB / SIGNATURE_COLS).toInt() - refAvg[idx + 2])
            if (quickDiff > quickThreshold) {
                return false
            }
            quickRow += quickStride
        }

        // Phase 2: Full scan
        var totalDiff = quickDiff
        for (row in 0 until regionH) {
            if (row % quickStride == 0) continue
            image.getPixels(rowPixels, 0, regionW, imgX, imgY + row, regionW, 1)
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (col in sampleXPositions) {
                val px = rowPixels[col]
                sumR += (px shr 16) and 0xFF
                sumG += (px shr 8) and 0xFF
                sumB += px and 0xFF
            }
            val idx = row * 3
            totalDiff += abs((sumR / SIGNATURE_COLS).toInt() - refAvg[idx])
            totalDiff += abs((sumG / SIGNATURE_COLS).toInt() - refAvg[idx + 1])
            totalDiff += abs((sumB / SIGNATURE_COLS).toInt() - refAvg[idx + 2])
            if (totalDiff > totalThreshold) {
                return false
            }
        }
        return true
    }

    // ---------------------------------------------------------------------------
    // Public entry point
    // ---------------------------------------------------------------------------

    fun check(image: Bitmap, level: Int): Pair<Int, Int> {
        val imgW = image.width
        val imgH = image.height

        val useScaledNarrow = imgW > NARROW_BANNER_W && imgW < BANNER_W
        val narrowW = if (useScaledNarrow) imgW else NARROW_BANNER_W

        val narrowTopSigToUse = narrowSigForWidth(narrowW)
        val narrowBottomSigToUse = narrowBottomSigForWidth(narrowW)

        val canCheckNew = imgW >= BANNER_W && imgH >= BANNER_H
        val canCheckOld = imgW >= OLD_BANNER_W && imgH >= OLD_BANNER_H
        // Top: full narrow banner height must fit
        val canCheckNarrowTop = imgW >= narrowTopSigToUse.width && imgH >= narrowTopSigToUse.height
        // Bottom: only the cropped height needs to fit
        val canCheckNarrowBottom = imgW >= narrowBottomSigToUse.width && imgH >= narrowBottomSigToUse.height

        if (!canCheckNew && !canCheckOld && !canCheckNarrowTop && !canCheckNarrowBottom) {
            return Pair(0, 0)
        }

        val padNew = if (canCheckNew) (imgW - BANNER_W) / 2 else 0
        val padOld = if (canCheckOld) (imgW - OLD_BANNER_W) / 2 else 0
        // Both narrow variants share the same width and therefore the same X pad
        val padNarrow = if (useScaledNarrow) 0 else (imgW - NARROW_BANNER_W) / 2
        val rowPixels = IntArray(minOf(imgW, BANNER_W))

        val isNormal = level != 2

        // Top scan: try all signatures at the current top offset.
        // Narrow top uses the full-height signature.
        fun matchAtTop(y: Int): Int {
            if (canCheckNew && y + BANNER_H <= imgH &&
                matchesSignature(image, padNew, y, bannerSig, rowPixels)
            ) {
                return BANNER_H
            }
            if (canCheckOld && y + OLD_BANNER_H <= imgH &&
                matchesSignature(image, padOld, y, oldBannerSig, rowPixels)
            ) {
                return OLD_BANNER_H
            }
            if (canCheckNarrowTop && y + narrowTopSigToUse.height <= imgH &&
                matchesSignature(image, padNarrow, y, narrowTopSigToUse, rowPixels)
            ) {
                return narrowTopSigToUse.height
            }
            return 0
        }

        var top = 0
        while (top < imgH) {
            val consumed = matchAtTop(top)
            if (consumed == 0) {
                break
            }
            top += consumed
            if (isNormal) break
        }
        if (isNormal && top > 0) {
            return Pair(top, 0)
        }

        // Bottom scan: try all signatures.
        // Narrow bottom uses the 160-row cropped signature.
        // Check tallest first (old=282, new=200, narrow-bottom=160) so a shorter
        // match doesn't shadow a taller one.
        var bottom = 0
        while (top + bottom < imgH) {
            val yOld = imgH - OLD_BANNER_H - bottom
            val yNew = imgH - BANNER_H - bottom
            val yNarrowBottom = imgH - narrowBottomSigToUse.height - bottom

            if (canCheckOld && yOld >= top &&
                matchesSignature(image, padOld, yOld, oldBannerSig, rowPixels)
            ) {
                bottom += OLD_BANNER_H
            } else if (canCheckNew && yNew >= top &&
                matchesSignature(image, padNew, yNew, bannerSig, rowPixels)
            ) {
                bottom += BANNER_H
            } else if (canCheckNarrowBottom && yNarrowBottom >= top &&
                matchesSignature(image, padNarrow, yNarrowBottom, narrowBottomSigToUse, rowPixels)
            ) {
                bottom += narrowBottomSigToUse.height
            } else {
                break
            }
            if (isNormal) break
        }

        return Pair(top, bottom)
    }
}
