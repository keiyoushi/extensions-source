package eu.kanade.tachiyomi.multisrc.initmanga.aes

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesDecrypt {

    private const val STATIC_AES_KEY =
        "3b16050a4d52ef1ccb28dc867b533abfc7fcb6bfaf6514b8676550b2f12454fa"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ALGORITHM = "AES"

    fun decryptLayered(html: String, ciphertext: String, ivHex: String, saltHex: String): String? {
        val staticAttempt = runCatching {
            decryptWithStaticKey(ciphertext, ivHex)
        }.getOrNull()

        return staticAttempt
    }

    private fun decryptWithStaticKey(ciphertextBase64: String, ivHex: String): String {
        val keyBytes = hexToBytes(STATIC_AES_KEY)
        val ivBytes = hexToBytes(ivHex)
        val ciphertextBytes = Base64.decode(ciphertextBase64, Base64.DEFAULT)

        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        return String(cipher.doFinal(ciphertextBytes), StandardCharsets.UTF_8)
    }

    private fun hexToBytes(hexString: String): ByteArray {
        val len = hexString.length
        if (len % 2 != 0) return ByteArray(0)

        val byteArray = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            byteArray[i / 2] = (
                (Character.digit(hexString[i], 16) shl 4) + Character.digit(
                    hexString[i + 1],
                    16,
                )
                ).toByte()
        }
        return byteArray
    }
}
