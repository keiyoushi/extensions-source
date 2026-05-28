package eu.kanade.tachiyomi.extension.ja.amebamanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !response.isSuccessful) return response

        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, fragment)
        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    private fun unscramble(bitmap: Bitmap, key: String): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val blockWidth = 96
        val blockHeight = 128
        val cols = width / blockWidth
        val rows = height / blockHeight
        val totalBlocks = cols * rows

        val l = IntArray(totalBlocks) { it }
        val randomizer = Randomizer(key)

        for (i in l.indices) {
            val n = randomizer.rand(l.size - 1)
            val temp = l[n]
            l[n] = l[i]
            l[i] = temp
        }

        val srcRect = Rect()
        val dstRect = Rect()

        for (s in 0 until totalBlocks) {
            val c = l[s]

            val srcX = (s % cols) * blockWidth
            val srcY = (s / cols) * blockHeight
            val dstX = (c % cols) * blockWidth
            val dstY = (c / cols) * blockHeight

            srcRect.set(srcX, srcY, srcX + blockWidth, srcY + blockHeight)
            dstRect.set(dstX, dstY, dstX + blockWidth, dstY + blockHeight)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        if (width % blockWidth > 0) {
            val remX = cols * blockWidth
            srcRect.set(remX, 0, width, height)
            dstRect.set(remX, 0, width, height)
            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }
        if (height % blockHeight > 0) {
            val remY = rows * blockHeight
            val drawWidth = cols * blockWidth
            srcRect.set(0, remY, drawWidth, height)
            dstRect.set(0, remY, drawWidth, height)
            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        return result
    }

    private class Randomizer(key: String) {
        private var next: Long = 0

        init {
            var a = 0
            val chars = key.toCharArray()
            var i = 0
            while (i < chars.size) {
                val o = chars[i].code
                i++
                val r = if (i < chars.size) chars[i].code else 0
                i++
                a += (o shl 8) or r
            }
            next = a.toLong()
        }

        private fun nextInt(): Int {
            next = (next * 1103515245L + 12345L) % 32768L
            return next.toInt()
        }

        fun rand(max: Int): Int {
            val n = max + 1
            return nextInt() / ((32767 / n) + 1)
        }
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
