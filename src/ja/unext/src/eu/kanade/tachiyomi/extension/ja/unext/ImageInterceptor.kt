package eu.kanade.tachiyomi.extension.ja.unext

import keiyoushi.utils.parseAs
import keiyoushi.zip.dataRange
import keiyoushi.zip.fixedLength
import keiyoushi.zip.range
import keiyoushi.zip.readEntry
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import okio.cipherSource
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.host != "127.0.0.1") {
            return chain.proceed(request)
        }

        val data = url.fragment?.parseAs<ImageRequestData>() ?: return chain.proceed(request)
        val range = dataRange(data.localFileHeaderOffset, data.compressedSize)
        val newRequest = request.newBuilder()
            .url(data.zipUrl)
            .range(range)
            .build()

        val response = chain.proceed(newRequest)
        if (!response.isSuccessful) return response

        val keyBytes = data.key.decodeBase64()?.toByteArray()
            ?: throw IOException("Invalid Key Base64")
        val ivBytes = data.iv.decodeBase64()?.toByteArray()
            ?: throw IOException("Invalid IV Base64")

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))

        val ciphertext = readEntry(response.body.source(), data.compressedSize, data.method)
        val image = ciphertext.cipherSource(cipher).fixedLength(data.originalFileSize).buffer()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(image.asResponseBody("image/webp".toMediaType()))
            .build()
    }
}
