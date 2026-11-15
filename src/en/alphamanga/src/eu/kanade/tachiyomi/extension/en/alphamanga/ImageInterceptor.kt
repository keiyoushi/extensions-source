package eu.kanade.tachiyomi.extension.en.alphamanga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import keiyoushi.utils.readIntLittleEndian
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import kotlin.math.ceil

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !fragment.startsWith("key=") || !response.isSuccessful) return response

        val key = fragment.substringAfter("key=")
        val bitmap = BitmapFactory.decodeStream(response.body.byteStream())
        val result = unscramble(bitmap, key)

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(Bitmap.CompressFormat.WEBP, 100, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(MEDIA_TYPE, buffer.size))
            .build()
    }

    // "createData" wasm export
    private fun unscramble(image: Bitmap, keyBase64: String): Bitmap {
        val key = Base64.decode(keyBase64, Base64.DEFAULT)
        if (key.size < 8) return image

        val v0 = key.readIntLittleEndian(0)
        val ha0 = key.readIntLittleEndian(4)
        val tileSize = (ha0 ushr 24) and 0xFF
        val w = (v0 ushr 27) and 7
        if (tileSize == 0) return image

        val srcWidth = image.width
        val srcHeight = image.height
        val cols = ceil(srcWidth.toDouble() / tileSize).toInt()
        val rows = ceil(srcHeight.toDouble() / tileSize).toInt()
        val r = w * 2
        val t = tileSize - r
        val outW = srcWidth - cols * r
        val outH = srcHeight - rows * r
        val lastCol = cols - 1
        val lastRow = rows - 1
        if (outW <= 0 || outH <= 0) return image

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect()
        val dstRect = RectF()

        val tileCount = key.size / 8
        for (idx in 0 until tileCount) {
            val off = idx * 8
            val v = key.readIntLittleEndian(off)
            val ha = key.readIntLittleEndian(off + 4)

            val flip = v and 1 // mirror flag
            val ca = (v ushr 1) and 3 // rotation, 0..3 -> 0/-90/-180/-270deg
            val ba = (v ushr 3) and 4095 // dest top (minus w)
            val aa = (v ushr 15) and 4095 // dest left (minus w)
            val srcRow = (ha ushr 8) and 0xFF
            val srcCol = (ha ushr 16) and 0xFF

            val b = (if (t != 0 && aa / t == lastCol) outW - aa else t) + r
            val s = (if (t != 0 && ba / t == lastRow) outH - ba else t) + r
            val dw: Int
            val dh: Int
            if (ca % 2 == 1) { // 90/270deg rotation swaps width/height
                dw = s
                dh = b
            } else {
                dw = b
                dh = s
            }

            val dx = aa - w
            val dy = ba - w

            val sx = (srcCol * tileSize).coerceIn(0, srcWidth)
            val sy = (srcRow * tileSize).coerceIn(0, srcHeight)
            val cropW = dw.coerceAtMost(srcWidth - sx).coerceAtLeast(0)
            val cropH = dh.coerceAtMost(srcHeight - sy).coerceAtLeast(0)
            if (cropW <= 0 || cropH <= 0) continue

            srcRect.set(sx, sy, sx + cropW, sy + cropH)
            dstRect.set(-cropW / 2f, -cropH / 2f, cropW / 2f, cropH / 2f)

            canvas.save()
            canvas.translate(dx + dw / 2f, dy + dh / 2f)
            canvas.rotate(-90f * ca)
            if (flip != 0) canvas.scale(-1f, 1f)
            canvas.drawBitmap(image, srcRect, dstRect, null)
            canvas.restore()
        }

        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/webp".toMediaType()
    }
}
