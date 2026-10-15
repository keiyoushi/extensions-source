package eu.kanade.tachiyomi.multisrc.initmanga

import android.util.Base64
import keiyoushi.utils.decodeHex
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AesDecrypt {
    private val REGEX_DECRYPTION_KEY_INSIDE = Regex("""["']?decryption_key["']?\s*[:=]\s*["']([^"']+)["']""")
    private val REGEX_SMART_KEY_HTML = Regex("""InitMangaData[\s\S]*?decryption_key["']?\s*[:=]\s*["']([^"']+)["']""")
    val REGEX_ENCRYPTED_DATA = Regex("""var\s+InitMangaEncryptedChapter\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)

    fun decryptLayered(document: org.jsoup.nodes.Document, ciphertext: String, ivHex: String, saltHex: String?): String? {
        if (saltHex.isNullOrBlank()) return null

        val rawKeyFromScript = document.select("script[src*=base64]").firstNotNullOfOrNull { script ->
            val src = script.attr("src")
            val base64Data = src.substringAfter("base64,").substringBeforeLast("\"").trimEnd('\'', '"')
            runCatching {
                val decodedScript = String(Base64.decode(base64Data, Base64.DEFAULT), Charsets.UTF_8)
                REGEX_DECRYPTION_KEY_INSIDE.find(decodedScript)?.groupValues?.get(1)
            }.getOrNull()
        }

        val finalRawKey = rawKeyFromScript
            ?: REGEX_DECRYPTION_KEY_INSIDE.find(document.html())?.groupValues?.get(1)
            ?: REGEX_SMART_KEY_HTML.find(document.html())?.groupValues?.get(1)

        if (finalRawKey != null) {
            return runCatching {
                val passphrase = String(Base64.decode(finalRawKey, Base64.DEFAULT), Charsets.UTF_8)
                val result = decryptWithPassphrase(ciphertext, passphrase, saltHex, ivHex)

                if (isValidContent(result)) result else null
            }.getOrNull()
        }

        return null
    }

    fun decryptWithKey(
        ciphertextBase64: String,
        keyHex: String,
        ivHex: String,
    ): String? = runCatching {
        val key = keyHex.decodeHex()
        val iv = ivHex.decodeHex()
        val ciphertext = Base64.decode(ciphertextBase64, Base64.DEFAULT)

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        val result = String(cipher.doFinal(ciphertext), Charsets.UTF_8)

        if (isValidContent(result)) result else null
    }.getOrNull()

    private fun isValidContent(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.isNotEmpty() && (trimmed.startsWith("<") || trimmed.startsWith("["))
    }

    private fun decryptWithPassphrase(
        ciphertextBase64: String,
        passphrase: String,
        saltHex: String,
        ivHex: String,
    ): String {
        val salt = saltHex.decodeHex()
        val iv = ivHex.decodeHex()
        val ciphertext = Base64.decode(ciphertextBase64, Base64.DEFAULT)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 999, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
