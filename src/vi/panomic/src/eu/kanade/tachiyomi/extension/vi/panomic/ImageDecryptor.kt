package eu.kanade.tachiyomi.extension.vi.panomic

import android.util.Base64
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ImageDecryptor {
    private const val PBKDF2_ITERATIONS = 999
    private const val KEY_SIZE_BITS = 256

    private val DEFAULT_KEY_PARTS = listOf("qX3xRL", "guhD2Z", "9f7sWJ")

    private val ENCRYPTED_CONTENT_REGEX = Regex(
        """var\s+htmlContent\s*=\s*"(.*?)"\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val PASS_EXPRESSION_REGEX = Regex(
        """CryptoJSAesDecrypt\((.+?),\s*htmlContent\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val QUOTED_LITERAL_REGEX = Regex("""['\"]([A-Za-z0-9]+)['\"]""")

    @Serializable
    private class EncryptedData(
        val ciphertext: String,
        val iv: String,
        val salt: String,
    )

    fun extractImageUrls(html: String, pageUrl: String): List<String> {
        val encryptedJsonString = ENCRYPTED_CONTENT_REGEX.find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\/", "/")

        if (encryptedJsonString != null) {
            val keyParts = extractKeyParts(html)
            val passphrase = keyParts.joinToString(separator = "")
            val decryptedHtml = runCatching {
                decryptContent(encryptedJsonString, passphrase)
            }.getOrNull()

            if (decryptedHtml != null) {
                val decryptedUrls = extractImagesFromDecryptedHtml(decryptedHtml, pageUrl, keyParts)
                if (decryptedUrls.isNotEmpty()) {
                    return decryptedUrls.distinct()
                }
            }
        }

        return extractFallbackImages(html, pageUrl).distinct()
    }

    private fun extractKeyParts(html: String): List<String> {
        val expression = PASS_EXPRESSION_REGEX.find(html)?.groupValues?.getOrNull(1)
        val parsedParts = expression
            ?.let { QUOTED_LITERAL_REGEX.findAll(it).map { match -> match.groupValues[1] }.toList() }
            .orEmpty()

        return parsedParts.takeIf { it.size >= 3 } ?: DEFAULT_KEY_PARTS
    }

    private fun decryptContent(encryptedJsonString: String, passphrase: String): String {
        val encryptedData = encryptedJsonString.parseAs<EncryptedData>()

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

    private fun extractImagesFromDecryptedHtml(
        html: String,
        pageUrl: String,
        keyParts: List<String>,
    ): List<String> {
        val doc = Jsoup.parse(html, pageUrl)
        val dataAttrKey = keyParts.first().lowercase(Locale.ROOT)

        return doc.select("img").mapNotNull { img ->
            val obfuscatedUrl = img.attr("data-$dataAttrKey").ifEmpty { img.attr("data-src") }
            if (obfuscatedUrl.isNotBlank()) {
                return@mapNotNull deobfuscateUrl(obfuscatedUrl, keyParts)
            }

            img.imageUrlFromAttributes()
        }
    }

    private fun deobfuscateUrl(url: String, keyParts: List<String>): String {
        val key1 = keyParts.getOrNull(0) ?: DEFAULT_KEY_PARTS[0]
        val key2 = keyParts.getOrNull(1) ?: DEFAULT_KEY_PARTS[1]
        val key3 = keyParts.getOrNull(2) ?: DEFAULT_KEY_PARTS[2]

        return url
            .replace(key1, ".")
            .replace(key2, ":")
            .replace(key3, "/")
    }

    private fun extractFallbackImages(html: String, pageUrl: String): List<String> {
        val doc = Jsoup.parse(html, pageUrl)
        val images = doc.select("#view-chapter img")
            .ifEmpty { doc.select(".chapter-content img, .reading-content img, .content-chapter img") }

        return images.mapNotNull { element ->
            element.imageUrlFromAttributes()
        }
    }

    private fun Element.imageUrlFromAttributes(): String? = absUrl("data-lazy-src")
        .ifEmpty { absUrl("data-src") }
        .ifEmpty { absUrl("src") }
        .takeUnless { it.isBlank() || it.startsWith("data:") }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)

        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }

        return data
    }
}
