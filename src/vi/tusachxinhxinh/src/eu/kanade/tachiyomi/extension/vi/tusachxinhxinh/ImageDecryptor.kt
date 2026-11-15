package eu.kanade.tachiyomi.extension.vi.tusachxinhxinh

import android.util.Base64
import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ImageDecryptor {
    private const val KEY_PART_1 = "qX3xRL"
    private const val KEY_PART_2 = "guhD2Z"
    private const val KEY_PART_3 = "9f7sWJ"
    private const val PBKDF2_ITERATIONS = 999
    private const val KEY_SIZE_BITS = 256

    private val encryptedContentRegex = Regex(
        """var\s+htmlContent\s*=\s*"(.*?)"\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )

    @Serializable
    private class EncryptedData(
        val ciphertext: String,
        val iv: String,
        val salt: String,
    )

    fun extractImageUrls(html: String, baseUrl: String): List<String> {
        val match = encryptedContentRegex.find(html) ?: return extractFallbackImages(html, baseUrl)
        val encryptedJsonString = match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\/", "/")

        return try {
            val decryptedHtml = decryptContent(encryptedJsonString)
            extractImagesFromDecryptedHtml(decryptedHtml, baseUrl)
        } catch (_: Exception) {
            extractFallbackImages(html, baseUrl)
        }
    }

    private fun decryptContent(encryptedJsonString: String): String {
        val encryptedData = encryptedJsonString.parseAs<EncryptedData>()

        val passphrase = KEY_PART_1 + KEY_PART_2 + KEY_PART_3
        val ciphertext = Base64.decode(encryptedData.ciphertext, Base64.DEFAULT)
        val ivBytes = encryptedData.iv.decodeHex()
        val saltBytes = encryptedData.salt.decodeHex()

        val keySpec = PBEKeySpec(
            passphrase.toCharArray(),
            saltBytes,
            PBKDF2_ITERATIONS,
            KEY_SIZE_BITS,
        )
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun extractImagesFromDecryptedHtml(html: String, baseUrl: String): List<String> {
        val doc = Jsoup.parseBodyFragment(html, baseUrl)
        return doc.select("img").mapNotNull { img ->
            val dataAttr = img.attr("data-${KEY_PART_1.lowercase()}")
            if (dataAttr.isNotBlank()) {
                return@mapNotNull deobfuscateUrl(dataAttr)
            }

            val src = img.attr("abs:src")
            if (src.startsWith("data:")) {
                return@mapNotNull null
            }

            src.takeIf { it.isNotBlank() && it.startsWith("http") }
        }
    }

    private fun deobfuscateUrl(url: String): String = url
        .replace(KEY_PART_1, ".")
        .replace(KEY_PART_2, ":")
        .replace(KEY_PART_3, "/")

    private fun extractFallbackImages(html: String, baseUrl: String): List<String> {
        val doc = Jsoup.parseBodyFragment(html, baseUrl)
        val images = doc.select("#view-chapter img")
            .ifEmpty { doc.select(".chapter-content img, .reading-content img, .content-chapter img") }

        return images.mapNotNull { element ->
            val imageUrl = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
            imageUrl.takeIf { it.isNotBlank() }
        }
    }
}
