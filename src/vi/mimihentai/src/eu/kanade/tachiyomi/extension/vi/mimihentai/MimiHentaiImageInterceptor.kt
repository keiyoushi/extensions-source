package eu.kanade.tachiyomi.extension.vi.mimihentai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class MimiHentaiImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val fragment = response.request.url.fragment

        if (fragment == null || !fragment.contains(GT)) {
            return response
        }

        val gt = fragment.substringAfter(GT)
        val image = extractImage(response.body.byteStream(), gt)
        val body = image.toResponseBody("image/jpeg".toMediaTypeOrNull())
        
        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun extractImage(imageStream: InputStream, gt: String): ByteArray {
        val bitmap = BitmapFactory.decodeStream(imageStream)
        
        var sw = 0
        var sh = 0
        val pos = mutableMapOf<String, String>()
        val dims = mutableMapOf<String, IntArray>()

        for (t in gt.split("|")) {
            when {
                t.startsWith("sw:") -> sw = t.substring(3).toInt()
                t.startsWith("sh:") -> sh = t.substring(3).toInt()
                t.contains("@") && t.contains(">") -> {
                    val (left, right) = t.split(">")
                    val (n, rectStr) = left.split("@")
                    val (x, y, w, h) = rectStr.split(",").map { it.toInt() }
                    dims[n] = intArrayOf(x, y, w, h)
                    pos[n] = right
                }
            }
        }

        if (sw <= 0 || sh <= 0) return bitmap.toByteArray()

        val fullW = bitmap.width
        val fullH = bitmap.height

        val working = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).also { k ->
            Canvas(k).drawBitmap(bitmap, Rect(0, 0, sw, sh), Rect(0, 0, sw, sh), null)
        }

        val keys = arrayOf("00","01","02","10","11","12","20","21","22")
        val baseW = sw / 3
        val baseH = sh / 3
        val rw = sw % 3
        val rh = sh % 3
        val defaultDims = mutableMapOf<String, IntArray>().apply {
            for (k in keys) {
                val i = k[0].digitToInt()
                val j = k[1].digitToInt()
                val w = baseW + if (j == 2) rw else 0
                val h = baseH + if (i == 2) rh else 0
                put(k, intArrayOf(j * baseW, i * baseH, w, h))
            }
        }

        val finalDims = dims.ifEmpty { defaultDims }
        
        val inv = mutableMapOf<String, String>().apply {
            pos.forEach { (a, b) -> put(b, a) }
        }

        val result = Bitmap.createBitmap(fullW, fullH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        for (k in keys) {
            val srcKey = inv[k] ?: continue
            val s = finalDims.getValue(k)
            val d = finalDims.getValue(srcKey)
            canvas.drawBitmap(
                working,
                Rect(s[0], s[1], s[0] + s[2], s[1] + s[3]),
                Rect(d[0], d[1], d[0] + d[2], d[1] + d[3]),
                null
            )
        }

        if (sh < fullH) {
            canvas.drawBitmap(
                bitmap,
                Rect(0, sh, fullW, fullH),
                Rect(0, sh, fullW, fullH),
                null
            )
        }
        if (sw < fullW) {
            canvas.drawBitmap(
                bitmap,
                Rect(sw, 0, fullW, sh),
                Rect(sw, 0, fullW, sh),
                null
            )
        }

        return result.toByteArray()
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }

    companion object {
        const val GT = "gt="
    }
}