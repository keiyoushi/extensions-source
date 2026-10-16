package eu.kanade.tachiyomi.extension.all.toonlook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import keiyoushi.lib.imagedecoder.ImageDecoder
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.security.MessageDigest

/**
 * Reverses the per-strip shuffle the reader applies to every page image.
 *
 * The permutation is derived from the image URL and the chapter's language, and is passed in
 * through the URL fragment (never sent to the server) as `#scramble=<lang>,<stripHeight>,<salt>`.
 */
class ScrambleInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment == null || !fragment.startsWith(SCRAMBLE_PREFIX)) {
            return chain.proceed(request)
        }

        val params = fragment.removePrefix(SCRAMBLE_PREFIX).split(SEPARATOR, limit = 3)
        if (params.size < 3) {
            return chain.proceed(request)
        }
        val (lang, stripHeightValue, salt) = params
        val stripHeight = stripHeightValue.toIntOrNull() ?: DEFAULT_STRIP_HEIGHT

        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            return response
        }

        response.use {
            val scrambled = ImageDecoder.decode(it.body.bytes())
            val unscrambled = descramble(scrambled, request.url, lang, stripHeight, salt)
            if (scrambled !== unscrambled) {
                scrambled.recycle()
            }

            val buffer = Buffer()
            unscrambled.compress(WEBP_FORMAT, 90, buffer.outputStream())
            unscrambled.recycle()

            return it.newBuilder()
                .body(buffer.asResponseBody(WEBP_MEDIA_TYPE, buffer.size))
                .build()
        }
    }

    private fun descramble(image: Bitmap, url: HttpUrl, lang: String, stripHeight: Int, salt: String): Bitmap {
        val width = image.width
        val height = image.height
        val strips = height / stripHeight
        if (strips <= 1) {
            return image
        }

        val result = Bitmap.createBitmap(width, height, image.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val order = shuffledOrder(strips, "$salt|$stripHeight|${seedKeyFromUrl(url, lang)}")
        val srcRect = Rect()
        val dstRect = Rect()
        for (i in 0 until strips) {
            srcRect.set(0, i * stripHeight, width, (i + 1) * stripHeight)
            dstRect.set(0, order[i] * stripHeight, width, (order[i] + 1) * stripHeight)
            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        val processedHeight = strips * stripHeight
        if (processedHeight < height) {
            srcRect.set(0, processedHeight, width, height)
            dstRect.set(0, processedHeight, width, height)
            canvas.drawBitmap(image, srcRect, dstRect, null)
        }

        return result
    }

    private fun seedKeyFromUrl(url: HttpUrl, lang: String): String {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val index = segments.indexOfFirst { it.equals(lang, ignoreCase = true) }
        return if (index >= 0 && segments.size >= index + 4) {
            segments.subList(index, index + 4).joinToString("/").lowercase()
        } else {
            segments.takeLast(4).joinToString("/").lowercase()
        }
    }

    private fun shuffledOrder(size: Int, key: String): IntArray {
        val order = IntArray(size) { it }
        var state = seedFromKey(key)
        for (i in size - 1 downTo 1) {
            val (nextState, random) = mulberry32Next(state)
            state = nextState
            val j = (random * (i + 1)).toInt()
            val swap = order[i]
            order[i] = order[j]
            order[j] = swap
        }
        return order
    }

    private fun seedFromKey(key: String): UInt {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return ((digest[0].toUInt() and 0xFFu) shl 24) or
            ((digest[1].toUInt() and 0xFFu) shl 16) or
            ((digest[2].toUInt() and 0xFFu) shl 8) or
            (digest[3].toUInt() and 0xFFu)
    }

    private fun mulberry32Next(value: UInt): Pair<UInt, Double> {
        val state = value + 0x6d2b79f5u
        var t = state
        t = (t xor (t shr 15)) * (t or 1u)
        t = t xor (t + ((t xor (t shr 7)) * (t or 61u)))
        t = t xor (t shr 14)
        return state to (t.toDouble() / 4294967296.0)
    }

    companion object {
        private const val SCRAMBLE_PREFIX = "scramble="
        private const val SEPARATOR = ","
        private const val DEFAULT_STRIP_HEIGHT = 500

        @Suppress("DEPRECATION")
        private val WEBP_FORMAT = Bitmap.CompressFormat.WEBP
        private val WEBP_MEDIA_TYPE = "image/webp".toMediaType()
    }
}
