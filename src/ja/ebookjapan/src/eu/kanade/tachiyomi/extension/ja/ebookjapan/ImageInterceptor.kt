package eu.kanade.tachiyomi.extension.ja.ebookjapan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !fragment.startsWith("data=") || !response.isSuccessful) {
            return response
        }

        val params = runCatching { decodeFragment(fragment.substringAfter("data=")) }
            .getOrNull() ?: return response

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscrambleImage(bitmap, params)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    // WASM func 208
    private fun unscrambleImage(src: Bitmap, p: Params): Bitmap {
        val cellW = src.width / p.gridDim
        val cellH = src.height / p.gridDim
        val tileW = cellW - 2 * p.margin
        val tileH = cellH - 2 * p.margin
        if (tileW <= 0 || tileH <= 0) return src

        val visCols = (p.pageWidth + tileW - 1) / tileW
        val visRows = (p.pageHeight + tileH - 1) / tileH
        val table = selectTable(src, p, cellW, cellH, tileW, tileH, visCols, visRows)

        val result = Bitmap.createBitmap(p.pageWidth, p.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect()
        val dstRect = Rect()

        for (visIdx in 0 until visCols * visRows) {
            if (visIdx >= table.size) break
            val srcIdx = table[visIdx].toInt() and 0xFF
            val sx = p.margin + (srcIdx % p.gridDim) * cellW
            val sy = p.margin + (srcIdx / p.gridDim) * cellH
            val dx = (visIdx % visCols) * tileW
            val dy = (visIdx / visCols) * tileH
            val pw = minOf(tileW, p.pageWidth - dx, src.width - sx)
            val ph = minOf(tileH, p.pageHeight - dy, src.height - sy)
            if (pw <= 0 || ph <= 0) continue

            srcRect.set(sx, sy, sx + pw, sy + ph)
            dstRect.set(dx, dy, dx + pw, dy + ph)
            canvas.drawBitmap(src, srcRect, dstRect, null)
        }
        return result
    }

    private fun selectTable(
        src: Bitmap,
        p: Params,
        cellW: Int,
        cellH: Int,
        tileW: Int,
        tileH: Int,
        visCols: Int,
        visRows: Int,
    ): ByteArray {
        if (p.tables.size <= 1) return p.tables.first()

        val rowStride = (visRows / 4).coerceAtLeast(1)
        val colStride = (visCols / 4).coerceAtLeast(1)
        val ySampleStep = (tileH / 8).coerceAtLeast(1)
        val xSampleStep = (tileW / 8).coerceAtLeast(1)

        var bestTable = p.tables.first()
        var bestScore = Long.MAX_VALUE

        for (table in p.tables) {
            if (table.size < visCols * visRows) continue
            var score = 0L

            // Horizontal: right edge of tile[r,c] vs left edge of tile[r,c+1]
            var r = 0
            while (r < visRows) {
                var c = 0
                while (c < visCols - 1) {
                    score += seamDiff(
                        src, table, p, cellW, cellH, tileW, tileH,
                        destA = r * visCols + c,
                        destB = r * visCols + c + 1,
                        horizontal = true,
                        step = ySampleStep,
                    )
                    c += colStride
                }
                r += rowStride
            }

            // Vertical: bottom edge of tile[r,c] vs top edge of tile[r+1,c]
            r = 0
            while (r < visRows - 1) {
                var c = 0
                while (c < visCols) {
                    score += seamDiff(
                        src, table, p, cellW, cellH, tileW, tileH,
                        destA = r * visCols + c,
                        destB = (r + 1) * visCols + c,
                        horizontal = false,
                        step = xSampleStep,
                    )
                    c += colStride
                }
                r += rowStride
            }

            if (score < bestScore) {
                bestScore = score
                bestTable = table
            }
        }
        return bestTable
    }

    private fun seamDiff(
        src: Bitmap,
        table: ByteArray,
        p: Params,
        cellW: Int,
        cellH: Int,
        tileW: Int,
        tileH: Int,
        destA: Int,
        destB: Int,
        horizontal: Boolean,
        step: Int,
    ): Long {
        val sA = table[destA].toInt() and 0xFF
        val sB = table[destB].toInt() and 0xFF
        val axBase = p.margin + (sA % p.gridDim) * cellW
        val ayBase = p.margin + (sA / p.gridDim) * cellH
        val bxBase = p.margin + (sB % p.gridDim) * cellW
        val byBase = p.margin + (sB / p.gridDim) * cellH

        var diff = 0L
        if (horizontal) {
            val ax = axBase + tileW - 1
            if (ax >= src.width || bxBase >= src.width) return 0L
            var y = 0
            while (y < tileH) {
                val ay = ayBase + y
                val by = byBase + y
                if (ay >= src.height || by >= src.height) break
                diff += colorDiff(src.getPixel(ax, ay), src.getPixel(bxBase, by))
                y += step
            }
        } else {
            val ay = ayBase + tileH - 1
            if (ay >= src.height || byBase >= src.height) return 0L
            var x = 0
            while (x < tileW) {
                val ax = axBase + x
                val bx = bxBase + x
                if (ax >= src.width || bx >= src.width) break
                diff += colorDiff(src.getPixel(ax, ay), src.getPixel(bx, byBase))
                x += step
            }
        }
        return diff
    }

    private fun colorDiff(c1: Int, c2: Int): Long {
        val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
        val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
        val db = (c1 and 0xFF) - (c2 and 0xFF)
        return (dr * dr + dg * dg + db * db).toLong()
    }

    private class Params(
        val pageWidth: Int,
        val pageHeight: Int,
        val margin: Int,
        val gridDim: Int,
        val tables: List<ByteArray>,
    )

    private fun decodeFragment(encoded: String): Params {
        val raw = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val w = buf.short.toInt() and 0xFFFF
        val h = buf.short.toInt() and 0xFFFF
        val margin = buf.get().toInt() and 0xFF
        val gridDim = buf.get().toInt() and 0xFF
        val numTables = buf.get().toInt() and 0xFF
        val tileBytes = gridDim * gridDim
        val tables = List(numTables) { ByteArray(tileBytes).also(buf::get) }
        return Params(w, h, margin, gridDim, tables)
    }

    companion object {
        private val MEDIA_TYPE = "image/webp".toMediaType()
    }
}
