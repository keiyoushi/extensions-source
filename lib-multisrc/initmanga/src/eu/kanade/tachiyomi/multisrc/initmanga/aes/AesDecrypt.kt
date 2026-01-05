package eu.kanade.tachiyomi.multisrc.initmanga.aes

import android.util.Base64
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AesDecrypt {

    private const val STATIC_AES_KEY = "3b16050a4d52ef1ccb28dc867b533abfc7fcb6bfaf6514b8676550b2f12454fa"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val ALGORITHM = "AES"

    private val REGEX_DECRYPTION_KEY_INSIDE = Regex("""["']?decryption_key["']?\s*[:=]\s*["']([^"']+)["']""")

    private val REGEX_SMART_KEY_HTML = Regex("""InitMangaData[\s\S]*?decryption_key["']?\s*[:=]\s*["']([^"']+)["']""")

    val REGEX_ENCRYPTED_DATA = Regex("""var\s+InitMangaEncryptedChapter\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)

    fun decryptLayered(html: String, ciphertext: String, ivHex: String, saltHex: String): String? {
        runCatching {
            val result = decryptWithStaticKey(ciphertext, ivHex)
            if (isValidContent(result)) return result
        }

        var rawKeyFromScript: String? = null

        val scriptContent = Jsoup.parse(html).selectFirst("script#init-main-js-extra")?.attr("src")

        if (scriptContent != null && scriptContent.contains("base64,")) {
            runCatching {
                val base64Data = scriptContent.substringAfter("base64,").substringBeforeLast("\"")
                val decodedScript = String(Base64.decode(base64Data, Base64.DEFAULT), StandardCharsets.UTF_8)

                rawKeyFromScript = REGEX_DECRYPTION_KEY_INSIDE.find(decodedScript)?.groupValues?.get(1)
            }
        }

        val finalRawKey = rawKeyFromScript ?: REGEX_SMART_KEY_HTML.find(html)?.groupValues?.get(1)

        if (finalRawKey != null) {
            return runCatching {
                val passphrase = String(Base64.decode(finalRawKey, Base64.DEFAULT), StandardCharsets.UTF_8)
                val result = decryptWithPassphrase(ciphertext, passphrase, saltHex, ivHex)

                if (isValidContent(result)) result else null
            }.getOrNull()
        }

        return null
    }

    private fun isValidContent(content: String): Boolean {
        if (content.isBlank()) return false
        val trimmed = content.trim()
        return trimmed.startsWith("<") || trimmed.startsWith("[")
    }

    // RagnarScans
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

    // MerlinToons
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
                (Character.digit(hexString[i], 16) shl 4) + Character.digit(hexString[i + 1], 16)
                ).toByte()
        }
        return byteArray
    }
}
