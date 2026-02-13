package eu.kanade.tachiyomi.extension.ja.cycomi

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.fragment != "decrypt") {
            return response
        }

        val key = request.url.pathSegments[3]
        if (key.contains("end_page")) {
            return response
        }

        val encrypted = response.body.bytes()
        val decrypted = decrypt(encrypted, key)
        val buffer = Buffer().write(decrypted)

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size))
            .build()
    }

    private fun decrypt(encrypted: ByteArray, passphrase: String): ByteArray {
        val key = IntArray(256) { it }
        var indexSwap = 0
        for (index in 0 until 256) {
            indexSwap = (indexSwap + key[index] + passphrase[index % passphrase.length].code) % 256
            val temp = key[index]
            key[index] = key[indexSwap]
            key[indexSwap] = temp
        }

        val decrypted = ByteArray(encrypted.size)
        var i = 0
        var j = 0
        for (index in encrypted.indices) {
            i = (i + 1) % 256
            j = (j + key[i]) % 256
            val temp = key[i]
            key[i] = key[j]
            key[j] = temp
            val xor = key[(key[i] + key[j]) % 256]
            decrypted[index] = (encrypted[index].toInt() xor xor).toByte()
        }
        return decrypted
    }
}
