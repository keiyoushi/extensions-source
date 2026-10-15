package eu.kanade.tachiyomi.extension.tr.mangadenizi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.InputStream

class UnscramblerInterceptor : Interceptor {
    companion object {
        const val PARAM_GRID = "scramble_grid"
        const val PARAM_SEED = "scramble_seed"

        private const val TA = 2463534242L
        private const val VO = 2654435769L
        private const val BO = 2246822507L

        private fun ft(s: Long): Long = s and 0xFFFFFFFFL

        private class XorShift(seed: Long) {
            private var e = ft(seed).takeIf { it != 0L } ?: TA

            fun next(): Long {
                e = ft(e xor ft(e shl 13))
                e = ft(e xor (e ushr 17))
                e = ft(e xor ft(e shl 5))
                return e
            }
        }

        private fun shuffle(length: Int, seed: Long): IntArray {
            val t = IntArray(maxOf(1, length)) { it }
            val rng = XorShift(seed)
            for (n in t.size - 1 downTo 1) {
                val i = (rng.next() % (n + 1)).toInt()
                val temp = t[n]
                t[n] = t[i]
                t[i] = temp
            }
            return t
        }

        private class Slice(val offset: Int, val length: Int)

        private fun createSlices(total: Int, pieces: Int): List<Slice> {
            val t = maxOf(1, total)
            val r = maxOf(1, minOf(pieces, t))
            return (0 until r).map { i ->
                val o = (i * t) / r
                val a = ((i + 1) * t) / r
                Slice(o, maxOf(1, a - o))
            }
        }

        private fun mapSlices(slices: List<Slice>, indices: IntArray): List<Slice> {
            var t = 0
            return indices.map { r ->
                val n = if (r in slices.indices) slices[r].length else 1
                val slice = Slice(t, n)
                t += n
                slice
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val grid = request.url.queryParameter(PARAM_GRID)?.toIntOrNull()
        val seed = request.url.queryParameter(PARAM_SEED)?.toLongOrNull()

        if (grid == null || seed == null) {
            return chain.proceed(request)
        }

        val cleanUrl = request.url.newBuilder()
            .removeAllQueryParameters(PARAM_GRID)
            .removeAllQueryParameters(PARAM_SEED)
            .build()
        val cleanRequest = request.newBuilder().url(cleanUrl).build()
        val response = chain.proceed(cleanRequest)

        val output = response.body.byteStream().use { descramble(it, grid, seed) }
        val newBody = output.asResponseBody("image/jpeg".toMediaType(), output.size)
        return response.newBuilder().body(newBody).build()
    }

    private fun descramble(image: InputStream, grid: Int, seed: Long): Buffer {
        val src = BitmapFactory.decodeStream(image)
        val output = Buffer()
        if (src == null) return output

        val w = src.width
        val h = src.height
        val l = maxOf(1, minOf(grid, minOf(w, h)))

        val u = createSlices(w, l)
        val c = createSlices(h, l)
        val m = shuffle(l, (ft(seed) xor ft(BO)).takeIf { it != 0L } ?: TA)
        val f = shuffle(l, (ft(seed) xor ft(VO)).takeIf { it != 0L } ?: TA)
        val p = mapSlices(u, m)
        val g = mapSlices(c, f)

        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)

        for (hIdx in 0 until l) {
            val d = f[hIdx]
            val v = c[d]
            val y = g[hIdx]
            for (bIdx in 0 until l) {
                val wIdx = m[bIdx]
                val k = u[wIdx]
                val t = p[bIdx]

                val srcRect = Rect(t.offset, y.offset, t.offset + t.length, y.offset + y.length)
                val dstRect = Rect(k.offset, v.offset, k.offset + k.length, v.offset + v.length)
                canvas.drawBitmap(src, srcRect, dstRect, null)
            }
        }

        src.recycle()

        dst.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
        dst.recycle()
        return output
    }
}
