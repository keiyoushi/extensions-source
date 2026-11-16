package eu.kanade.tachiyomi.multisrc.koi

import android.util.Base64
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object ImageDecryptor {
    private const val PBKDF2_ITERATIONS = 999
    private const val KEY_SIZE_BITS = 256

    private val encryptedContentRegex = Regex("""var\s+htmlContent\s*=\s*("(?:[^"\\]|\\.)*")\s*;""")
    private val passphraseCallRegex = Regex("""CryptoJSAesDecrypt\(([^()]+),\s*htmlContent\)""")
    private val quotedLiteralRegex = Regex("""['"]([A-Za-z0-9]+)['"]""")
    private val secretVarRegex = Regex("""var\s+secret(One|Two|Three|DataKey)\s*=\s*['"]([^'"]+)['"]""")

    private class Secrets(val parts: List<String>, val dataKey: String) {
        val passphrase = parts.joinToString("")

        fun deobfuscate(url: String) = url
            .replace(parts[0], ".")
            .replace(parts[1], ":")
            .replace(parts[2], "/")
    }

    fun extractImageUrls(html: String, document: Document): List<String> {
        val secrets = findSecrets(html)
        val encrypted = encryptedContentRegex.find(html)?.groupValues?.get(1)

        if (secrets != null && encrypted != null) {
            val images = runCatching {
                Jsoup.parseBodyFragment(decrypt(encrypted, secrets.passphrase), document.location())
                    .select("img")
                    .mapNotNull { it.decryptedImageUrl(secrets) }
            }.getOrNull()
            if (!images.isNullOrEmpty()) return images.distinct()
        }

        return document.select("#view-chapter img")
            .ifEmpty { document.select(".chapter-content img, .reading-content img, .content-chapter img, .comic-chapter img") }
            .mapNotNull { it.plainImageUrl() }
            .distinct()
    }

    // The key parts are either inlined in the decrypt call or declared as secretOne/Two/Three
    private fun findSecrets(html: String): Secrets? {
        val inlined = passphraseCallRegex.find(html)?.groupValues?.get(1)
            ?.let { call -> quotedLiteralRegex.findAll(call).map { it.groupValues[1] }.toList() }
        if (inlined != null && inlined.size >= 3) return Secrets(inlined, inlined[0].lowercase())

        val vars = secretVarRegex.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
        val parts = listOf("One", "Two", "Three").map { vars[it] ?: return null }
        return Secrets(parts, vars["DataKey"] ?: parts[0].lowercase())
    }

    private fun decrypt(encryptedLiteral: String, passphrase: String): String {
        val content = encryptedLiteral.parseAs<String>().parseAs<EncryptedContent>()

        val keySpec = PBEKeySpec(passphrase.toCharArray(), content.salt.decodeHex(), PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(keySpec).encoded

        return Base64.decode(content.ciphertext, Base64.DEFAULT)
            .aesDecrypt(aesCbc(key, content.iv.decodeHex()))
            .decodeToString()
    }

    private fun Element.decryptedImageUrl(secrets: Secrets): String? {
        val obfuscated = attr("data-${secrets.dataKey}")
        if (obfuscated.isNotBlank() && obfuscated != "loaded" && obfuscated != "stored") {
            return secrets.deobfuscate(obfuscated)
        }
        return plainImageUrl()
    }

    private fun Element.plainImageUrl(): String? = absUrl("data-lazy-src")
        .ifEmpty { absUrl("data-src") }
        .ifEmpty { absUrl("src") }
        .takeUnless { it.isEmpty() || it.startsWith("data:") }
}
