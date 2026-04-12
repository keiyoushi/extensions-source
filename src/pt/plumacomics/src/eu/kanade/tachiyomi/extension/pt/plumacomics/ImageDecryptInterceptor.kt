package eu.kanade.tachiyomi.extension.pt.plumacomics

import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.math.min

class ImageDecryptInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isSuccessful && response.request.url.encodedPath.contains("/api/read/")) {
            val body = response.body
            val encryptedBytes = body.bytes()

            val dto = response.request.url.fragment!!.parseAs<ChapterDto>()
            val seed = dto.baseSeed.map { it.toByte() }.toByteArray()
            val decryptedBytes = decrypt(encryptedBytes, seed)

            val newBody = decryptedBytes.toResponseBody(body.contentType())
            return response.newBuilder()
                .body(newBody)
                .build()
        }

        return response
    }

    private fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        if (data.isEmpty() || key.isEmpty()) return data

        val limit = min(data.size, 1024)
        val keySize = key.size

        for (i in 0 until limit) {
            val keyIndex = i % keySize
            val mask = ((key[keyIndex].toInt() and 0xFF xor 75) - (keyIndex * 3)) and 0xFF
            val originalByte = data[i].toInt() and 0xFF
            data[i] = (originalByte xor mask).toByte()
        }

        return data
    }
}
