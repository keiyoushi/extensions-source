package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import keiyoushi.lib.cryptoaes.CryptoAES
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class AESDecryptInterceptor : Interceptor {
    private val salt = "salt"
    private val password = "mangotoons_encryption_key_2025"

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val isEncrypted = response.headers["x-encrypted"].toBoolean()
        if (!isEncrypted) return response

        val parts = response.body.string().trim().split(":")

        val iv = parts[0].hexToByteArray()
        val ciphertext = parts[1].hexToByteArray()

        val key = password.deriveKey()
        val decrypted = CryptoAES.decryptAES(ciphertext, key, iv)

        return response.newBuilder()
            .body(decrypted.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun String.deriveKey(): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val input = (this + salt).toByteArray(StandardCharsets.UTF_8)
        return md.digest(input)
    }
}
