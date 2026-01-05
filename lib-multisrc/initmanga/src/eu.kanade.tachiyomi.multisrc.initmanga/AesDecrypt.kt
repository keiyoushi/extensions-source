package eu.kanade.tachiyomi.multisrc.initmanga

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AesDecrypt {

    private const val STATIC_AES_KEY =
        "3b16050a4d52ef1ccb28dc867b533abfc7fcb6bfaf6514b8676550b2f12454fa"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ALGORITHM = "AES"

    val REGEX_ENCRYPTED_DATA =
        Regex("""var\s+InitMangaEncryptedChapter\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
    private val REGEX_DECRYPTION_KEY = Regex(""""decryption_key"\s*:\s*"([^"]+)"""")

    fun decryptLayered(html: String, ciphertext: String, ivHex: String, saltHex: String): String? {
        val staticAttempt = runCatching {
            decryptWithStaticKey(ciphertext, ivHex)
        }.getOrNull()

        if (!staticAttempt.isNullOrBlank() && isValidContent(staticAttempt)) {
            return staticAttempt
        }

        val keyMatch = REGEX_DECRYPTION_KEY.find(html) ?: return null
        return runCatching {
            val passphrase = String(
                Base64.decode(keyMatch.groupValues[1], Base64.DEFAULT),
                StandardCharsets.UTF_8,
            )
            decryptWithPassphrase(ciphertext, passphrase, saltHex, ivHex)
        }.getOrNull()
    }

    private fun isValidContent(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.startsWith("<") || trimmed.startsWith("[")
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

    private fun decryptWithPassphrase(
        ciphertextBase64: String,
        passphrase: String,
        saltHex: String,
        ivHex: String,
    ): String {
        val salt = hexToBytes(saltHex)
        val iv = hexToBytes(ivHex)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.DEFAULT)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 999, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
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
