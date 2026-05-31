package eu.kanade.tachiyomi.extension.ja.mangakingdom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import eu.kanade.tachiyomi.extension.ja.mangakingdom.MangaKingdom.Companion.stripJson
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment
        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.startsWith("scene=")) {
            return response
        }

        val sceneNo = fragment.substringAfter("scene=").toInt()
        val content = response.parseAs<ContentResponse> { stripJson(it) }
        val image = content.scenes.first { it.sceneNo == sceneNo }.images.first()
        val rawBytes = Base64.decode(image.imgBase64, Base64.DEFAULT)
        val buffer = Buffer()
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        val result = unscramble(bitmap, image.key, image.width, image.height)
        bitmap.recycle()
        result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
        result.recycle()
        val body = buffer.asResponseBody(JPEG_MEDIA_TYPE, buffer.size)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    private fun unscramble(src: Bitmap, key: Int, dstW: Int, dstH: Int): Bitmap {
        val srcW = src.width
        val srcH = src.height

        var blockW = 32
        var blockH = 32

        if (srcW > 1000 || srcH > 1000) {
            blockW *= 3
            blockH *= 3
        } else if (srcW > 300 || srcH > 300) {
            blockW *= 2
            blockH *= 2
        }

        val innerW = blockW - 2
        val innerH = blockH - 2

        val cols = srcW / blockW
        val rows = srcH / blockH
        val total = cols * rows
        if (total <= 0) return src

        var lcgValue = key.toLong()

        val available = ArrayList<Int>(total).apply { for (i in 0 until total) add(i) }
        val result = Bitmap.createBitmap(cols * innerW, rows * innerH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect()
        val dstRect = Rect()

        for (t in 0 until total) {
            lcgValue = (lcgValue * 8741 + 30873) % 131071
            val s = (lcgValue % available.size).toInt()
            val c = available.removeAt(s)

            val srcX = (c % cols) * blockW + 1
            val srcY = (c / cols) * blockH + 1
            val dstX = (t % cols) * innerW
            val dstY = (t / cols) * innerH

            srcRect.set(srcX, srcY, srcX + innerW, srcY + innerH)
            dstRect.set(dstX, dstY, dstX + innerW, dstY + innerH)
            canvas.drawBitmap(src, srcRect, dstRect, null)
        }

        if (result.width == dstW && result.height == dstH) return result
        return Bitmap.createBitmap(result, 0, 0, minOf(result.width, dstW), minOf(result.height, dstH)).also {
            if (it !== result) result.recycle()
        }
    }

    companion object {
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
