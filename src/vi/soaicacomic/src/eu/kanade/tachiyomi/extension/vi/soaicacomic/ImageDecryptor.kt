package eu.kanade.tachiyomi.extension.vi.soaicacomic

import android.util.Base64
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ImageDecryptor {

    private const val KEY_PART_1 = "p3Cr24"
    private const val KEY_PART_2 = "4zAFC2"
    private const val KEY_PART_3 = "GJ6m5e"

    private const val PBKDF2_ITERATIONS = 999
    private const val KEY_SIZE_BITS = 256

    private val ENCRYPTED_CONTENT_REGEX = Regex(
        """var\s+htmlContent\s*=\s*"(.*?)"\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )

    @Serializable
    class EncryptedData(
        val ciphertext: String,
        val iv: String,
        val salt: String,
    )

    fun extractImageUrls(html: String, baseUrl: String): List<String> {
        val match = ENCRYPTED_CONTENT_REGEX.find(html)
        if (match == null) {
            return extractFallbackImages(html, baseUrl)
        }

        val encryptedJson = match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\/", "/")

        return try {
            val decryptedHtml = decryptContent(encryptedJson)
            extractImagesFromDecryptedHtml(decryptedHtml, baseUrl)
        } catch (_: Exception) {
            extractFallbackImages(html, baseUrl)
        }
    }

    private fun decryptContent(encryptedJsonString: String): String {
        val encryptedData = encryptedJsonString.parseAs<EncryptedData>()
        val passphrase = KEY_PART_1 + KEY_PART_2 + KEY_PART_3

        val ciphertext = Base64.decode(encryptedData.ciphertext, Base64.DEFAULT)
        val ivBytes = hexToByteArray(encryptedData.iv)
        val saltBytes = hexToByteArray(encryptedData.salt)

        val keySpec = PBEKeySpec(
            passphrase.toCharArray(),
            saltBytes,
            PBKDF2_ITERATIONS,
            KEY_SIZE_BITS,
        )
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            IvParameterSpec(ivBytes),
        )

        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun extractImagesFromDecryptedHtml(html: String, baseUrl: String): List<String> {
        val document = Jsoup.parse(html, baseUrl)

        return document.select("img").mapNotNull { img ->
            val obfuscated = img.attr("data-p3cr24")
            if (obfuscated.isNotEmpty() && obfuscated != "loaded" && obfuscated != "stored") {
                return@mapNotNull deobfuscateUrl(obfuscated)
            }

            img.absUrl("src").ifEmpty { img.absUrl("data-src") }
                .takeIf { it.isNotEmpty() && !it.startsWith("data:") }
        }
    }

    private fun deobfuscateUrl(url: String): String = url
        .replace(KEY_PART_1, ".")
        .replace(KEY_PART_2, ":")
        .replace(KEY_PART_3, "/")

    private fun extractFallbackImages(html: String, baseUrl: String): List<String> {
        val document = Jsoup.parse(html, baseUrl)
        val images = document.select("#view-chapter img")
            .ifEmpty { document.select(".chapter-content img, .reading-content img, .content-chapter img") }

        return images.mapNotNull { element ->
            element.absUrl("data-src")
                .ifEmpty { element.absUrl("src") }
                .takeIf { it.isNotEmpty() }
        }
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        var index = 0
        while (index < hex.length) {
            result[index / 2] = ((Character.digit(hex[index], 16) shl 4) + Character.digit(hex[index + 1], 16)).toByte()
            index += 2
        }
        return result
    }
}
