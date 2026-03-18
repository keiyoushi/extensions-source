package eu.kanade.tachiyomi.extension.vi.teamlanhlung

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
    private const val KEY_PART_1 = "DA9TqD"
    private const val KEY_PART_2 = "QqNm2h"
    private const val KEY_PART_3 = "wSUU8q"

    private const val PBKDF2_ITERATIONS = 999
    private const val KEY_SIZE_BITS = 256

    private val ENCRYPTED_CONTENT_REGEX = Regex(
        """var\s+htmlContent\s*=\s*"(.*?)"\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )

    @Serializable
    private class EncryptedData(
        val ciphertext: String,
        val iv: String,
        val salt: String,
    )

    fun extractImageUrls(html: String): List<String> {
        val match = ENCRYPTED_CONTENT_REGEX.find(html) ?: return extractFallbackImages(html)
        val encryptedJsonString = match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\/", "/")

        return try {
            val decryptedHtml = decryptContent(encryptedJsonString)
            extractImagesFromDecryptedHtml(decryptedHtml)
        } catch (_: Exception) {
            extractFallbackImages(html)
        }
    }

    private fun decryptContent(encryptedJsonString: String): String {
        val encryptedData = encryptedJsonString.parseAs<EncryptedData>()
        val passphrase = KEY_PART_1 + KEY_PART_2 + KEY_PART_3

        val ciphertext = Base64.decode(encryptedData.ciphertext, Base64.DEFAULT)
        val ivBytes = hexStringToByteArray(encryptedData.iv)
        val saltBytes = hexStringToByteArray(encryptedData.salt)

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

    private fun extractImagesFromDecryptedHtml(html: String): List<String> {
        val doc = Jsoup.parse(html)
        return doc.select("img").mapNotNull { img ->
            val encryptedUrl = img.attr("data-da9tqd")
            if (encryptedUrl.isNotBlank() && encryptedUrl != "loaded" && encryptedUrl != "stored") {
                return@mapNotNull deobfuscateUrl(encryptedUrl)
            }

            val src = img.attr("src")
            if (src.startsWith("data:")) {
                return@mapNotNull null
            }

            src.takeIf { it.isNotBlank() }
        }
    }

    private fun deobfuscateUrl(url: String): String = url
        .replace(KEY_PART_1, ".")
        .replace(KEY_PART_2, ":")
        .replace(KEY_PART_3, "/")

    private fun extractFallbackImages(html: String): List<String> {
        val doc = Jsoup.parse(html)
        val images = doc.select("#view-chapter img")
            .ifEmpty { doc.select(".chapter-content img, .reading-content img, .content-chapter img") }

        return images.mapNotNull { element ->
            val imageUrl = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
            imageUrl.takeIf { it.isNotBlank() }
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
