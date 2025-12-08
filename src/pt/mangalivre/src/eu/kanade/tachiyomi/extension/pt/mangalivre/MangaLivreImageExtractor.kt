package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document

object MangaLivreImageExtractor {

    private val PUSH_ARRAY_REGEX = Regex("""_27d3ae\[_0bf0\]\s*\(\s*['"]([A-Za-z0-9+/=]+)['"]\s*\)""")
    private val GENERIC_PUSH_REGEX = Regex("""_[a-f0-9]+\[_[a-f0-9]+\]\s*\(\s*['"]([A-Za-z0-9+/=]+)['"]\s*\)""")
    private val PLACEHOLDER_PATTERNS = listOf("hi.png", "placeholder", "loading")

    fun extractImageUrls(document: Document, json: Json): List<String>? {
        val scripts = document.select("script:not([src])")

        for (script in scripts) {
            val data = script.data()
            if (data.length < 100) continue

            if (containsObfuscationPattern(data)) {
                val urls = extractFromPushArray(data, json)
                if (urls != null && urls.isNotEmpty()) {
                    return urls
                }
            }
        }

        return null
    }

    private fun containsObfuscationPattern(scriptData: String): Boolean {
        val hasPushVar = scriptData.contains("\\x70\\x75\\x73\\x68") ||
            scriptData.contains("_0bf0")
        val hasArrayDecl = scriptData.contains("var _27d3ae") ||
            scriptData.contains("= []")
        val hasBracketPush = scriptData.contains("[_0bf0]") ||
            GENERIC_PUSH_REGEX.containsMatchIn(scriptData)

        return hasPushVar && hasArrayDecl && hasBracketPush
    }

    private fun extractFromPushArray(scriptData: String, json: Json): List<String>? {
        var matches = PUSH_ARRAY_REGEX.findAll(scriptData).toList()

        if (matches.isEmpty()) {
            matches = GENERIC_PUSH_REGEX.findAll(scriptData).toList()
        }

        if (matches.isEmpty()) {
            return null
        }

        val joinedBase64 = matches.joinToString("") { it.groupValues[1] }

        if (joinedBase64.isEmpty()) {
            return null
        }

        return decodeBase64ToUrls(joinedBase64, json)
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
