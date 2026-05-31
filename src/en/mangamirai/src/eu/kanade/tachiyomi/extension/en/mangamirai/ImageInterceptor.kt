package eu.kanade.tachiyomi.extension.en.mangamirai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import okio.cipherSource
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !response.isSuccessful) return response

        val contentsId = request.url.pathSegments[1]
        val seed = "manga${contentsId}mirai".toByteArray()
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(seed)

        val source = response.body.source()
        val iv = IvParameterSpec(source.readByteArray(16))
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)

        val bitmap = BitmapFactory.decodeStream(source.cipherSource(cipher).buffer().inputStream())
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

        // .split(',', '[', ']')
        val scrambleOrder = buildList {
            val bytes = key.decodeBase64()!!
            var current = -1
            for (i in 0 until bytes.size) {
                val b = bytes[i].toInt()
                if (b in 48..57) {
                    current = (if (current == -1) 0 else current) * 10 + (b - 48)
                } else if (current != -1) {
                    add(current)
                    current = -1
                }
            }
            if (current != -1) add(current)
        }

        val columns = (width + 95) / 96
        val srcRect = Rect()
        val dstRect = Rect()

        scrambleOrder.forEachIndexed { index, srcIndex ->
            val srcX = (srcIndex % columns) * 96
            val srcY = (srcIndex / columns) * 96
            srcRect.set(srcX, srcY, srcX + min(96, width - srcX), srcY + min(96, height - srcY))

            val dstX = (index % columns) * 96
            val dstY = (index / columns) * 96
            dstRect.set(dstX, dstY, dstX + srcRect.width(), dstY + srcRect.height())

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        return result
    }

    companion object {
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
