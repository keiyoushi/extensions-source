package eu.kanade.tachiyomi.extension.ja.comicgrast

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.lib.seedrandom.SeedRandom
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        val seed = url.queryParameter("seed")
        val sizeParam = url.queryParameter("size")

        if (seed.isNullOrEmpty() || sizeParam.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        val body = response.body.source()
        val bitmap = BitmapFactory.decodeStream(body.inputStream())

        val sliceSize = max(bitmap.width, bitmap.height) / sizeParam.toFloat()

        val result = unscrambleImg(bitmap, sliceSize, seed)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    private fun unscrambleImg(img: Bitmap, sliceSize: Float, seed: String): Bitmap {
        val width = img.width
        val height = img.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val slices = getSlices(width, height, sliceSize)

        for (group in slices.values) {
            val rng = SeedRandom(seed)

            val len = group.size
            val indices = MutableList(len) { it }
            val shuffleInd = rng.shuffle(indices)
            val cols = getColsInGroup(group)

            val groupX = group[0].x
            val groupY = group[0].y

            for (i in 0 until len) {
                val destSlice = group[i]
                val s = shuffleInd[i]

                val row = s / cols
                val col = s % cols

                val sliceW = destSlice.width
                val sliceH = destSlice.height

                val srcX = groupX + col * sliceW
                val srcY = groupY + row * sliceH

                val srcRect = Rect(srcX, srcY, srcX + sliceW, srcY + sliceH)
                val dstRect = Rect(destSlice.x, destSlice.y, destSlice.x + sliceW, destSlice.y + sliceH)

                canvas.drawBitmap(img, srcRect, dstRect, null)
            }
        }
        return result
    }

    private class Slice(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun getSlices(imgWidth: Int, imgHeight: Int, sliceSize: Float): Map<String, List<Slice>> {
        val totalParts = (ceil(imgWidth / sliceSize) * ceil(imgHeight / sliceSize)).toInt()
        val verticalSlices = ceil(imgWidth / sliceSize).toInt()
        val groups = LinkedHashMap<String, MutableList<Slice>>()

        for (i in 0 until totalParts) {
            val row = floor(i.toDouble() / verticalSlices).toInt()
            val col = i - row * verticalSlices

            val x = (col * sliceSize).toInt()
            val y = (row * sliceSize).toInt()

            val w = if (x + sliceSize <= imgWidth) sliceSize.toInt() else imgWidth - x
            val h = if (y + sliceSize <= imgHeight) sliceSize.toInt() else imgHeight - y

            val key = "$w-$h"
            val list = groups.getOrPut(key) { ArrayList() }
            list.add(Slice(x, y, w, h))
        }
        return groups
    }

    private fun getColsInGroup(slices: List<Slice>): Int {
        if (slices.size == 1) return 1
        val firstY = slices[0].y
        for (i in slices.indices) {
            if (slices[i].y != firstY) return i
        }
        return slices.size
    }
}
