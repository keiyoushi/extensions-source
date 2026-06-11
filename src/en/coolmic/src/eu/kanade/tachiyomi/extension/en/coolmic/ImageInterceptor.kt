package eu.kanade.tachiyomi.extension.en.coolmic

import android.util.Base64
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        val response = chain.proceed(request)

        if (fragment.isNullOrEmpty() || !fragment.startsWith("key=") || !response.isSuccessful) return response

        val key = fragment.substringAfter("key=")
        val page = response.parseAs<PageResponse>()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(key, page.salt.fromBase64(), page.iterations),
            IvParameterSpec(page.iv.fromBase64()),
        )
        val body = Buffer().write(page.encryptedImage.fromBase64()).cipherSource(cipher).buffer().asResponseBody(MEDIA_TYPE)

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_SIZE_BITS)
        val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.DEFAULT)

    companion object {
        private const val KEY_SIZE_BITS = 256
        private val MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
