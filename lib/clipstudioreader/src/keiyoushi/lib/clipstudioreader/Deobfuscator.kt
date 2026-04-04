package keiyoushi.lib.clipstudioreader

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class Deobfuscator : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (fragment.isNullOrEmpty() || !fragment.contains("key=") || !response.isSuccessful) {
            return response
        }

        val key = fragment.substringAfter("key=").toInt()
        val responseBody = response.body
        val source = responseBody.source()

        source.request(1024)
        val limit = minOf(source.buffer.size, 1024L)
        val buffer = Buffer()
        repeat(limit.toInt()) { buffer.writeByte(source.readByte().toInt() xor key) }
        buffer.writeAll(source)
        val body = buffer.asResponseBody(responseBody.contentType(), responseBody.contentLength())

        return response.newBuilder()
            .body(body)
            .build()
    }
}
