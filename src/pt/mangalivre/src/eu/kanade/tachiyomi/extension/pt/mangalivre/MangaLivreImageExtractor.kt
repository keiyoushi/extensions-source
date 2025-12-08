package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document

object MangaLivreImageExtractor {

    private val PLACEHOLDER_PATTERNS = listOf("hi.png", "placeholder", "loading")
    private val XOR_DATA_REGEX = Regex("""_[a-f0-9]+\[idx\+\+\]\s*=\s*['"]([A-Za-z0-9+/=]+)['"];""")
    private val XOR_KEY_REGEX = Regex("""var\s+_[a-f0-9]+\s*=\s*["']([a-f0-9]+)["'];""")

    fun extractImageUrls(document: Document, json: Json): List<String>? {
        for (script in document.select("script:not([src])")) {
            val data = script.data()
            if (data.length < 100) continue

            if (data.contains("idx++") && data.contains("var _x = [")) {
                extractFromXorEncryption(data, json)?.let { return it }
            }
        }
        return null
    }

    private fun extractFromXorEncryption(scriptData: String, json: Json): List<String>? {
        val dataMatches = XOR_DATA_REGEX.findAll(scriptData).toList()
        if (dataMatches.isEmpty()) return null

        val joinedBase64 = dataMatches.joinToString("") { it.groupValues[1] }
        if (joinedBase64.isEmpty()) return null

        val xorKey = XOR_KEY_REGEX.findAll(scriptData)
            .map { it.groupValues[1] }
            .find { it.length == 8 }
            ?: return null

        return try {
            val decodedBytes = Base64.decode(joinedBase64, Base64.DEFAULT)

            val decrypted = buildString {
                for (i in decodedBytes.indices) {
                    append((decodedBytes[i].toInt() xor xorKey[i % xorKey.length].code).toChar())
                }
            }

            json.parseToJsonElement(decrypted).jsonArray
                .map { it.jsonPrimitive.content.trim() }
                .filter { it.isNotBlank() && isValidImageUrl(it) }
                .ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidImageUrl(url: String): Boolean {
        return PLACEHOLDER_PATTERNS.none { url.contains(it, ignoreCase = true) }
    }
}
