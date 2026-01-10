package eu.kanade.tachiyomi.lib.clipstudioreader

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class Deobfuscator : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val keyStr = request.url.queryParameter("obfuscateKey")

        if (keyStr.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val key = keyStr.toInt()
        val newUrl = request.url.newBuilder().removeAllQueryParameters("obfuscateKey").build()
        val newRequest = request.newBuilder().url(newUrl).build()

        val response = chain.proceed(newRequest)
        if (!response.isSuccessful) {
            return response
        }

        val obfuscatedBytes = response.body.bytes()
        val deobfuscatedBytes = deobfuscate(obfuscatedBytes, key)
        val body = deobfuscatedBytes.toResponseBody("image/jpeg".toMediaType())

        return response.newBuilder().body(body).build()
    }

    private fun deobfuscate(bytes: ByteArray, key: Int): ByteArray {
        val limit = minOf(bytes.size, 1024)
        for (i in 0 until limit) {
            bytes[i] = (bytes[i].toInt() xor key).toByte()
        }
        return bytes
    }
}
