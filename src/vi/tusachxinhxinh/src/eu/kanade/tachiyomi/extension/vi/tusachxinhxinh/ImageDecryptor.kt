package eu.kanade.tachiyomi.extension.vi.tusachxinhxinh

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
    // Decryption key parts (concatenated: qX3xRLguhD2Z9f7sWJ)
    private const val KEY_PART_1 = "qX3xRL"
    private const val KEY_PART_2 = "guhD2Z"
    private const val KEY_PART_3 = "9f7sWJ"

    // PBKDF2 parameters (from CryptoJSAesDecrypt function)
    private const val PBKDF2_ITERATIONS = 999
    private const val KEY_SIZE_BITS = 256

    // Regex to match: var htmlContent = "...";
    // Uses .*? with dot-all to capture the entire escaped JSON string
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

    /**
     * Extract and decrypt image URLs from the page HTML.
     * Returns a list of image URLs.
     */
    fun extractImageUrls(html: String): List<String> {
        // Try to find encrypted content
        val match = ENCRYPTED_CONTENT_REGEX.find(html) ?: return extractFallbackImages(html)
        val encryptedJsonString = match.groupValues[1]
            // Unescape the JSON string (it's double-escaped in HTML)
            .replace("\\\"", "\"")
            .replace("\\/", "/")

        return try {
            val decryptedHtml = decryptContent(encryptedJsonString)
            extractImagesFromDecryptedHtml(decryptedHtml)
        } catch (e: Exception) {
            // Fallback to regular image extraction if decryption fails
            extractFallbackImages(html)
        }
    }

    /**
     * Decrypt the AES-encrypted content using CryptoJS format with PBKDF2.
     */
    private fun decryptContent(encryptedJsonString: String): String {
        val encryptedData = encryptedJsonString.parseAs<EncryptedData>()

        val passphrase = KEY_PART_1 + KEY_PART_2 + KEY_PART_3

        // Decode components
        val ciphertext = Base64.decode(encryptedData.ciphertext, Base64.DEFAULT)
        val ivBytes = hexStringToByteArray(encryptedData.iv)
        val saltBytes = hexStringToByteArray(encryptedData.salt)

        // Derive key using PBKDF2 with SHA-512 (as used by CryptoJSAesDecrypt)
        val keySpec = PBEKeySpec(
            passphrase.toCharArray(),
            saltBytes,
            PBKDF2_ITERATIONS,
            KEY_SIZE_BITS,
        )
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded

        // Decrypt using AES-CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Extract image URLs from the decrypted HTML.
     * The real URL is in data attribute (obfuscated) with key part replacements.
     */
    private fun extractImagesFromDecryptedHtml(html: String): List<String> {
        val doc = Jsoup.parse(html)
        return doc.select("img").mapNotNull { img ->
            // Check data attribute first (contains obfuscated URL)
            val dataAttr = img.attr("data-${KEY_PART_1.lowercase()}")
            if (dataAttr.isNotBlank()) {
                return@mapNotNull deobfuscateUrl(dataAttr)
            }

            // Fallback to src if data attribute is empty
            val src = img.attr("src")
            // Skip placeholder SVGs
            if (src.startsWith("data:")) {
                return@mapNotNull null
            }

            src.takeIf { it.isNotBlank() && it.startsWith("http") }
        }
    }

    /**
     * De-obfuscate URL by replacing key parts with special characters.
     * qX3xRL -> .
     * guhD2Z -> :
     * 9f7sWJ -> /
     */
    private fun deobfuscateUrl(url: String): String = url
        .replace(KEY_PART_1, ".")
        .replace(KEY_PART_2, ":")
        .replace(KEY_PART_3, "/")

    /**
     * Fallback: extract images directly from HTML if decryption is not needed or fails.
     */
    private fun extractFallbackImages(html: String): List<String> {
        val doc = Jsoup.parse(html)
        val images = doc.select("#view-chapter img")
            .ifEmpty { doc.select(".chapter-content img, .reading-content img, .content-chapter img") }

        return images.mapNotNull { element ->
            val imageUrl = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
            imageUrl.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Convert hex string to byte array.
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
