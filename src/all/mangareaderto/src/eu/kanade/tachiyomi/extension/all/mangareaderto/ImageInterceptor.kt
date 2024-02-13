package eu.kanade.tachiyomi.extension.all.mangareaderto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

object ImageInterceptor : Interceptor {

    private val memo = hashMapOf<Int, IntArray>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.fragment != SCRAMBLED) return response

        val image = response.body.byteStream().use(::descramble)
        val body = image.toResponseBody("image/jpeg".toMediaType())
        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun descramble(image: InputStream): ByteArray {
        // obfuscated code (imgReverser function): https://mangareader.to/js/read.min.js
        // essentially, it shuffles arrays of the image slices using the key 'stay'

        val bitmap = BitmapFactory.decodeStream(image)
        val width = bitmap.width
        val height = bitmap.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val pieces = ArrayList<Piece>()
        for (y in 0 until height step PIECE_SIZE) {
            for (x in 0 until width step PIECE_SIZE) {
                val w = min(PIECE_SIZE, width - x)
                val h = min(PIECE_SIZE, height - y)
                pieces.add(Piece(x, y, w, h))
            }
        }

        val groups = pieces.groupBy { it.w shl 16 or it.h }

        for (group in groups.values) {
            val size = group.size

            val permutation = memo.getOrPut(size) {
                // The key is actually "stay", but it's padded here in case the code is run in
                // Oracle's JDK, where RC4 key is required to be at least 5 bytes
                val random = SeedRandom("staystay")

                // https://github.com/webcaetano/shuffle-seed
                val indices = (0 until size).toMutableList()
                IntArray(size) { indices.removeAt((random.nextDouble() * indices.size).toInt()) }
            }

            for ((i, original) in permutation.withIndex()) {
                val src = group[i]
                val dst = group[original]

                val srcRect = Rect(src.x, src.y, src.x + src.w, src.y + src.h)
                val dstRect = Rect(dst.x, dst.y, dst.x + dst.w, dst.y + dst.h)

                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            }
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output)
        return output.toByteArray()
    }

    private class Piece(val x: Int, val y: Int, val w: Int, val h: Int)

    // https://github.com/davidbau/seedrandom
    private class SeedRandom(key: String) {
        private val input = ByteArray(RC4_WIDTH)
        private val buffer = ByteArray(RC4_WIDTH)
        private var pos = RC4_WIDTH

        private val rc4 = Cipher.getInstance("RC4").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(), "RC4"))
            update(input, 0, RC4_WIDTH, buffer) // RC4-drop[256]
        }

        fun nextDouble(): Double {
            var num = nextByte()
            var exp = 8
            while (num < 1L shl 52) {
                num = num shl 8 or nextByte()
                exp += 8
            }
            while (num >= 1L shl 53) {
                num = num ushr 1
                exp--
            }
            return Math.scalb(num.toDouble(), -exp)
        }

        private fun nextByte(): Long {
            if (pos == RC4_WIDTH) {
                rc4.update(input, 0, RC4_WIDTH, buffer)
                pos = 0
            }
            return buffer[pos++].toLong() and 0xFF
        }
    }

    private const val RC4_WIDTH = 256
    private const val PIECE_SIZE = 200
    const val SCRAMBLED = "scrambled"
}
