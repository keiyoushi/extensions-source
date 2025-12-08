package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document

object MangaLivreImageExtractor {

    private val PLACEHOLDER_PATTERNS = listOf("hi.png", "placeholder", "loading")

    private val INDEXED_ARRAY_REGEX = Regex("""(_[a-f0-9]+)\[(\d+)\]\s*=\s*['"]([A-Za-z0-9+/=]+)['"];""")

    private val PUSH_VAR_REGEX = Regex("""_[a-f0-9]+\[_[a-f0-9]+\]\s*\(\s*['"]([A-Za-z0-9+/=]+)['"]\s*\)""")

    fun extractImageUrls(document: Document, json: Json): List<String>? {
        val scripts = document.select("script:not([src])")

        for (script in scripts) {
            val data = script.data()
            if (data.length < 100) continue

            if (containsObfuscationPattern(data)) {
                val urls = extractFromReversedBase64(data, json)
                if (urls != null && urls.isNotEmpty()) {
                    return urls
                }
            }
        }

        return null
    }

    private fun containsObfuscationPattern(scriptData: String): Boolean {
        val hasPushHex = scriptData.contains("\\x70\\x75\\x73\\x68")
        val hasArrayDecl = scriptData.contains("= []") || scriptData.contains("= [];")
        val hasReverse = scriptData.contains(".reverse()") || scriptData.contains("chapter-images-render")

        return hasPushHex && hasArrayDecl && hasReverse
    }

    private fun extractFromReversedBase64(scriptData: String, json: Json): List<String>? {
        val indexedMatches = INDEXED_ARRAY_REGEX.findAll(scriptData).toList()

        if (indexedMatches.isNotEmpty()) {
            val groupedByArray = indexedMatches.groupBy { it.groupValues[1] }

            val largestArray = groupedByArray.maxByOrNull { it.value.size }?.value ?: return null

            val sortedParts = largestArray
                .sortedBy { it.groupValues[2].toIntOrNull() ?: 0 }
                .map { it.groupValues[3] }

            val joinedBase64 = sortedParts.joinToString("")

            if (joinedBase64.isNotEmpty()) {
                val reversedBase64 = joinedBase64.reversed()
                val urls = decodeBase64ToUrls(reversedBase64, json)
                if (urls != null && urls.isNotEmpty()) {
                    return urls
                }
            }
        }

        val pushMatches = PUSH_VAR_REGEX.findAll(scriptData).toList()
        if (pushMatches.isNotEmpty()) {
            val joinedBase64 = pushMatches.joinToString("") { it.groupValues[1] }
            if (joinedBase64.isNotEmpty()) {
                return decodeBase64ToUrls(joinedBase64, json)
            }
        }

        return null
    }

    private fun decodeBase64ToUrls(base64: String, json: Json): List<String>? {
        return try {
            val decoded = String(Base64.decode(base64, Base64.DEFAULT))
            val jsonArray = json.parseToJsonElement(decoded).jsonArray
            val urls = jsonArray
                .map { it.jsonPrimitive.content.trim() }
                .filter { url -> isValidImageUrl(url) }

            urls.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidImageUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return PLACEHOLDER_PATTERNS.none { placeholder ->
            url.contains(placeholder, ignoreCase = true)
        }
    }
}
