package eu.kanade.tachiyomi.extension.vi.medamtruyen

import android.util.Base64
import keiyoushi.utils.parseAs
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ImageDecryptor {
    private const val PBKDF2_ITERATIONS = 999
    private const val KEY_SIZE_BITS = 256

    private val ENCRYPTED_CONTENT_REGEX = Regex(
        """var\s+htmlContent\s*=\s*"(.*?)"\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val SECRET_ONE_REGEX = Regex("""var\s+secretOne\s*=\s*['"]([^'"]+)['"]""")
    private val SECRET_TWO_REGEX = Regex("""var\s+secretTwo\s*=\s*['"]([^'"]+)['"]""")
    private val SECRET_THREE_REGEX = Regex("""var\s+secretThree\s*=\s*['"]([^'"]+)['"]""")
    private val SECRET_DATA_KEY_REGEX = Regex("""var\s+secretDataKey\s*=\s*['"]([^'"]+)['"]""")

    private class SecretParts(
        val one: String,
        val two: String,
        val three: String,
        val dataKey: String?,
    ) {
        val passphrase = one + two + three
    }

    fun extractImageUrls(html: String, baseUrl: String): List<String> {
        val secretParts = extractSecretParts(html)
        val encryptedJsonString = ENCRYPTED_CONTENT_REGEX.find(html)?.groupValues?.get(1)

        if (secretParts != null && encryptedJsonString != null) {
            val decryptedHtml = runCatching {
                decryptContent(secretParts.passphrase, encryptedJsonString)
            }.getOrNull()

            if (decryptedHtml != null) {
                val decryptedImages = extractImagesFromDecryptedHtml(decryptedHtml, baseUrl, secretParts)
                if (decryptedImages.isNotEmpty()) {
                    return decryptedImages
                }
            }
        }

        return extractFallbackImages(html, baseUrl)
    }

    private fun extractSecretParts(html: String): SecretParts? {
        val one = SECRET_ONE_REGEX.find(html)?.groupValues?.get(1) ?: return null
        val two = SECRET_TWO_REGEX.find(html)?.groupValues?.get(1) ?: return null
        val three = SECRET_THREE_REGEX.find(html)?.groupValues?.get(1) ?: return null
        val dataKey = SECRET_DATA_KEY_REGEX.find(html)?.groupValues?.get(1)

        return SecretParts(one, two, three, dataKey)
    }

    private fun decryptContent(passphrase: String, encryptedJsonString: String): String {
        val normalizedJson = encryptedJsonString
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
        val encryptedData = normalizedJson.parseAs<EncryptedContentDto>()

        val ciphertextString = encryptedData.ciphertext ?: error("Missing ciphertext")
        val ivString = encryptedData.iv ?: error("Missing iv")
        val saltString = encryptedData.salt ?: error("Missing salt")

        val ciphertext = Base64.decode(ciphertextString, Base64.DEFAULT)
        val ivBytes = hexStringToByteArray(ivString)
        val saltBytes = hexStringToByteArray(saltString)

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

    private fun extractImagesFromDecryptedHtml(
        html: String,
        baseUrl: String,
        secretParts: SecretParts,
    ): List<String> {
        val document = Jsoup.parse(html, baseUrl)
        val dataAttribute = secretParts.dataKey?.let { "data-$it" }

        return document.select("img").mapNotNull { imageElement ->
            val obfuscatedUrl = dataAttribute
                ?.let(imageElement::attr)
                ?.takeIf { it.isNotBlank() }

            val decodedUrl = obfuscatedUrl?.let {
                decodeObfuscatedUrl(it, secretParts)
            }

            val directUrl = imageElement.absUrl("data-src")
                .ifEmpty { imageElement.absUrl("src") }

            val imageUrl = decodedUrl ?: directUrl
            imageUrl.takeIf {
                it.isNotBlank() && !it.startsWith("data:")
            }
        }.distinct()
    }

    private fun decodeObfuscatedUrl(url: String, secretParts: SecretParts): String = url
        .replace(secretParts.one, ".")
        .replace(secretParts.two, ":")
        .replace(secretParts.three, "/")

    private fun extractFallbackImages(html: String, baseUrl: String): List<String> {
        val document = Jsoup.parse(html, baseUrl)
        val images = document.select("#view-chapter img")
            .ifEmpty { document.select(".chapter-content img, .reading-content img, .content-chapter img, .comic-chapter img") }

        return images.mapNotNull { imageElement ->
            imageElement.absUrl("data-src")
                .ifEmpty { imageElement.absUrl("src") }
                .takeIf { it.isNotBlank() }
        }.distinct()
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val bytes = ByteArray(hex.length / 2)
        var index = 0
        while (index < hex.length) {
            bytes[index / 2] = ((Character.digit(hex[index], 16) shl 4) + Character.digit(hex[index + 1], 16)).toByte()
            index += 2
        }
        return bytes
    }
}
