package eu.kanade.tachiyomi.extension.ja.mangasaison

import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import keiyoushi.zip.dataRange
import keiyoushi.zip.range
import keiyoushi.zip.readEntry
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okio.ByteString.Companion.decodeHex as decodeHexToByteString

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

        // wasm func 217 (LSUZR::getData) AES-CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(data.key.decodeHex(), "AES"), IvParameterSpec(IMAGE_IV))

        val ciphertext = readEntry(response.body.source(), data.compressedSize, data.method)
        val image = ciphertext.cipherSource(cipher).buffer()
        val body = image.asResponseBody(image.imageMediaType())

        return response.newBuilder()
            .removeHeader("Content-Range")
            .removeHeader("Content-Length")
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }

    private fun BufferedSource.imageMediaType(): MediaType? = when {
        rangeEquals(8, WEBP_SIGNATURE) -> WEBP_MEDIA_TYPE
        rangeEquals(0, JPEG_SIGNATURE) -> JPEG_MEDIA_TYPE
        rangeEquals(0, PNG_SIGNATURE) -> PNG_MEDIA_TYPE
        else -> OCTET_MEDIA_TYPE
    }

    companion object {
        // Inside WASM static IV: \06\9a\ba\f7heP\a1\a7\8c\9b\de`\b8\13\e4 -> \06\9a\ba\f7\68\65\50\a1\a7\8c\9b\de\60\b8\13\e4
        private val IMAGE_IV = "069abaf7686550a1a78c9bde60b813e4".decodeHex()

        private val WEBP_SIGNATURE = "WEBP".encodeUtf8()
        private val JPEG_SIGNATURE = "ffd8ff".decodeHexToByteString()
        private val PNG_SIGNATURE = "89504e470d0a1a0a".decodeHexToByteString()

        private val WEBP_MEDIA_TYPE = "image/webp".toMediaType()
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
        private val PNG_MEDIA_TYPE = "image/png".toMediaType()
        private val OCTET_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}
