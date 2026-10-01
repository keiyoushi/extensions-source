package eu.kanade.tachiyomi.extension.zh.hikarinagi

import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Decrypts the reader API's answers: `iv || AES-256-GCM(ciphertext)` keyed by the request token. */
internal object ReaderCrypto {

    private const val TOKEN_SIZE = 64
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    private const val DECRYPT_FAILED = "内容解密失败，请刷新后重试"

    private val random = SecureRandom()

    /** The token a page request carries; the site encrypts its answer with it. */
    fun newToken(): ByteArray = ByteArray(TOKEN_SIZE).also(random::nextBytes)

    /** Decrypts a page the site bound to [associatedData] with the [token] that requested it. */
    fun decrypt(token: ByteArray, content: ByteArray, associatedData: String): ByteArray = try {
        // The key is the token's two halves XORed together.
        val key = ByteArray(TOKEN_SIZE / 2) { (token[it].toInt() xor token[it + TOKEN_SIZE / 2].toInt()).toByte() }
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, content, 0, IV_SIZE))
            updateAAD(associatedData.toByteArray(Charsets.UTF_8))
            doFinal(content, IV_SIZE, content.size - IV_SIZE)
        }
    } catch (e: Exception) {
        throw IOException(DECRYPT_FAILED, e)
    }
}
