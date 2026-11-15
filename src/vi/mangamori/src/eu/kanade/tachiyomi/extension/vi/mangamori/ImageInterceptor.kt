package eu.kanade.tachiyomi.extension.vi.mangamori

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import keiyoushi.utils.readUShortBigEndian
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        if (!fragment.startsWith(fragmentPrefix)) return chain.proceed(request)

        val parts = fragment.removePrefix(fragmentPrefix).split(":", limit = 3)
        val rows = parts.getOrNull(0)?.toIntOrNull() ?: return chain.proceed(request)
        val cols = parts.getOrNull(1)?.toIntOrNull() ?: return chain.proceed(request)
        val map = parts.getOrNull(2)?.split(',')?.mapNotNull(String::toIntOrNull)
            ?: return chain.proceed(request)

        val cleanUrl = request.url.newBuilder().fragment(null).build()
        val response = chain.proceed(request.newBuilder().url(cleanUrl).build())
        if (!response.isSuccessful) return response

        val bitmap = response.body.byteStream().use(BitmapFactory::decodeStream) ?: run {
            response.close()
            throw IOException("Failed to decode scrambled MangaMori image")
        }
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val tileWidth = bitmap.width / cols
        val tileHeight = bitmap.height / rows

        map.forEachIndexed { destination, source ->
            val sourceX = source % cols * tileWidth
            val sourceY = source / cols * tileHeight
            val destinationX = destination % cols * tileWidth
            val destinationY = destination / cols * tileHeight
            canvas.drawBitmap(
                bitmap,
                Rect(sourceX, sourceY, sourceX + tileWidth, sourceY + tileHeight),
                Rect(destinationX, destinationY, destinationX + tileWidth, destinationY + tileHeight),
                null,
            )
        }

        val buffer = Buffer()
        output.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
        bitmap.recycle()
        output.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/png".toMediaType()))
            .build()
    }

    private val fragmentPrefix = "mm:"
}

fun decodeMap(encrypted: String, nonce: String, count: Int): List<Int> {
    val encryptedBytes = Base64.decode(encrypted, Base64.DEFAULT)
    val bytes = encryptedBytes.copyOfRange(6, encryptedBytes.size)
    var seed = 0x811c9dc5.toInt()
    val salt = intArrayOf(88, 107, 74, 118, 82, 109, 100, 81)
    salt.forEachIndexed { index, value ->
        seed = seed xor (value xor ((index shl 2) + 31))
        seed *= 0x01000193
    }
    nonce.forEach { char ->
        seed = seed xor char.code
        seed *= 0x01000193
    }

    val decoded = ByteArray(bytes.size)
    var state = seed
    bytes.forEachIndexed { index, byte ->
        state = state * 0x41c64e6d + 12345
        decoded[index] = (byte.toInt() xor ((state ushr 16) and 0xff)).toByte()
    }

    return List(count) { index -> decoded.readUShortBigEndian(index * 2) }
}
