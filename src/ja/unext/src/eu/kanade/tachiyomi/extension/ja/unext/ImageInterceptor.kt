package eu.kanade.tachiyomi.extension.ja.unext

import android.util.Base64
import keiyoushi.utils.parseAs
import keiyoushi.zip.dataRange
import keiyoushi.zip.fixedLength
import keiyoushi.zip.range
import keiyoushi.zip.readEntry
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment

        if (fragment == null || !fragment.startsWith("{")) {
            return chain.proceed(request)
        }

        val data = fragment.parseAs<ImageRequestData>()
        val newRequest = request.newBuilder()
            .range(dataRange(data.localHeaderOffset, data.compressedSize))
            .build()

        val response = chain.proceed(newRequest)
        if (!response.isSuccessful) return response

        val key = SecretKeySpec(Base64.decode(data.key, Base64.DEFAULT), "AES")
        val iv = IvParameterSpec(Base64.decode(data.iv, Base64.DEFAULT))
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)

        val ciphertext = readEntry(response.body.source(), data.compressedSize, data.method)
        val image = ciphertext.cipherSource(cipher).fixedLength(data.originalFileSize).buffer()

        return response.newBuilder()
            .body(image.asResponseBody("image/webp".toMediaType(), data.originalFileSize))
            .build()
    }
}
