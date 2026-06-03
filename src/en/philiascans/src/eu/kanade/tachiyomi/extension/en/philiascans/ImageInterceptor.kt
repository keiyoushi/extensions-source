package eu.kanade.tachiyomi.extension.en.philiascans

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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !response.isSuccessful || !SCRAMBLED.matches(request.url.pathSegments.last())) {
            return response
        }

        val parts = fragment.split(";", limit = 7)
        val (isScrambled, mimeType, chapterKeyB64, gridSize, payloadA) = parts
        val payloadB = parts[5]
        val pageIndex = parts[6].toInt()

        val chapterKey = if (payloadA != "null" && payloadA.isNotBlank() && payloadB != "null" && payloadB.isNotBlank()) {
            val a = payloadA.decodeBase64()!!.toByteArray()
            val b = payloadB.decodeBase64()!!.toByteArray()
            ByteArray(32) { i -> a[i] xor b[i] }
        } else {
            chapterKeyB64.decodeBase64()!!.toByteArray()
        }

        val encryptedBytes = response.body.source().readByteArray()
        if (encryptedBytes.size < 4) return response

        val header = ByteBuffer.wrap(encryptedBytes).order(ByteOrder.BIG_ENDIAN)
        val originalWidth = header.short.toInt() and 0xFFFF
        val originalHeight = header.short.toInt() and 0xFFFF
        val imageBytes = encryptedBytes.copyOfRange(4, encryptedBytes.size)

        val mac = initMac(chapterKey)
        val numBlocks = (imageBytes.size + 31) / 32
        for (i in 0 until numBlocks) {
            val hash = mac.doFinal("page:$pageIndex:$i".toByteArray(Charsets.UTF_8))
            val base = i * 32
            for (j in 0 until minOf(32, imageBytes.size - base)) {
                imageBytes[base + j] = imageBytes[base + j] xor hash[j]
            }
        }

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val (format, quality) = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> Bitmap.CompressFormat.JPEG to 90
            "image/png" -> Bitmap.CompressFormat.PNG to 100
            else -> Bitmap.CompressFormat.WEBP to 100
        }

        val result = if (isScrambled == "1") {
            unscramble(bitmap, chapterKey, pageIndex, gridSize.toInt(), originalWidth, originalHeight)
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, originalWidth, originalHeight)
        }

        bitmap.recycle()
        val buffer = Buffer()
        result.compress(format, quality, buffer.outputStream())
        result.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody(mimeType.toMediaType(), buffer.size))
            .build()
    }

    private fun unscramble(
        bitmap: Bitmap,
        chapterKey: ByteArray,
        pageIndex: Int,
        gridSize: Int,
        originalWidth: Int,
        originalHeight: Int,
    ): Bitmap {
        val tileWidth = bitmap.width / gridSize
        val tileHeight = bitmap.height / gridSize
        val gridSizeSq = gridSize * gridSize
        val c = IntArray(gridSizeSq) { it }

        if (gridSizeSq >= 2) {
            val tilesSig = initMac(chapterKey).doFinal("tiles:$pageIndex".toByteArray(Charsets.UTF_8))
            val mac = initMac(tilesSig)
            var nCounter = 0
            var rBuf = ByteBuffer.allocate(0)
            var aIndex = 8

            fun nextRandom(): Long {
                if (aIndex >= 8) {
                    rBuf = ByteBuffer.wrap(mac.doFinal("perm:${nCounter++}".toByteArray(Charsets.UTF_8)))
                        .order(ByteOrder.LITTLE_ENDIAN)
                    aIndex = 0
                }
                return rBuf.getInt(aIndex++ * 4).toLong() and 0xFFFFFFFFL
            }

            for (idx in gridSizeSq - 1 downTo 1) {
                val swapIdx = (nextRandom() % (idx + 1)).toInt()
                val temp = c[idx]
                c[idx] = c[swapIdx]
                c[swapIdx] = temp
            }
        }

        val w = IntArray(gridSizeSq)
        for (i in 0 until gridSizeSq) {
            w[c[i]] = i
        }

        val result = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val srcRect = Rect()
        val dstRect = Rect()

        for (t in 0 until gridSizeSq) {
            val srcIdx = w[t]
            val srcX = (srcIdx % gridSize) * tileWidth
            val srcY = (srcIdx / gridSize) * tileHeight
            val dstX = (t % gridSize) * tileWidth
            val dstY = (t / gridSize) * tileHeight

            srcRect.set(srcX, srcY, srcX + tileWidth, srcY + tileHeight)
            dstRect.set(dstX, dstY, dstX + tileWidth, dstY + tileHeight)
            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        }

        return result
    }

    private fun initMac(key: ByteArray): Mac = Mac.getInstance("HmacSHA256").also { it.init(SecretKeySpec(key, "HmacSHA256")) }

    companion object {
        private val SCRAMBLED = Regex(""".*_s\.[^.]+$""")
    }
}
