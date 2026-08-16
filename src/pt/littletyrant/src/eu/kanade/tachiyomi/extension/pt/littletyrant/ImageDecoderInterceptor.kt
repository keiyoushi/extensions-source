package eu.kanade.tachiyomi.extension.pt.littletyrant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class ImageDecoderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment.isNullOrBlank() || !fragment.contains("token")) {
            return chain.proceed(request)
        }

        val token = fragment.parseAs<Map<String, String>>().getValue("token")

        val response = chain.proceed(imageRequest(request, token))
        val body = response.body.bytes()
        val bitmap = decode(body, getKey(token))

        val output = ByteArrayOutputStream()

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)

        val responseBody = output.toByteArray().toResponseBody("image/jpg".toMediaType())

        return response.newBuilder()
            .body(responseBody)
            .build()
    }

    private fun imageRequest(request: Request, token: String): Request {
        val url = request.url.newBuilder()
            .fragment(null)
            .build()
        return request.newBuilder()
            .url(url)
            .header("Cookie", "lt_sec_val=$token")
            .build()
    }

    private fun getKey(token: String): String = token.split(".").last().substring(4, 20)

    private fun decode(buf: ByteArray, key: String): Bitmap {
        val v = buf.copyOf()
        val xLen = minOf(1024, v.size)

        for (i in 0 until xLen) {
            v[i] = (v[i].toInt() xor key[i % key.length].code).toByte()
        }

        return BitmapFactory.decodeByteArray(v, 0, v.size)
    }
}
