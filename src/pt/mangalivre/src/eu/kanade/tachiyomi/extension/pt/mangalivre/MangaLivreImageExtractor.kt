package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document

object MangaLivreImageExtractor {

    private val PLACEHOLDER_PATTERNS = listOf("hi.png", "placeholder", "loading")
    private val SCRIPT_XOR_KEY_REGEX = Regex(""""([a-f0-9]{8})"\s*,\s*"([a-f0-9]{8})"""")
    private const val DEFAULT_KEY1 = "2e88429b"
    private const val DEFAULT_KEY2 = "29d076b6"

    fun extractImageUrls(document: Document): List<String>? {
        val dataXSpans = document.select("span[data-x]")
        if (dataXSpans.isEmpty()) return null

        val (key1, key2) = findXorKeysFromScript(document) ?: Pair(DEFAULT_KEY1, DEFAULT_KEY2)

        val allEncodedData = dataXSpans.mapNotNull { span ->
            span.attr("data-x").takeIf { it.isNotBlank() }
        }.joinToString("")

        if (allEncodedData.isBlank()) return null

        val jsonArray = decodeDataXToJson(allEncodedData, key1, key2)
        return parseUrlsFromJson(jsonArray)
    }

    private fun decodeDataXToJson(encodedData: String, key1: String, key2: String): String {
        val decodedBytes = Base64.decode(encodedData, Base64.DEFAULT)

        val result = buildString {
            for (i in decodedBytes.indices) {
                val block = i / 8
                val key = if (block % 2 == 0) key1 else key2
                val c = decodedBytes[i].toInt() and 0xFF xor key[i % 8].code
                append(c.toChar())
            }
        }

        val startIdx = result.indexOf('[')
        val endIdx = result.lastIndexOf(']')
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            return result
        }

        return result.substring(startIdx, endIdx + 1)
            .replace("\\/", "/")
    }

    private fun parseUrlsFromJson(jsonStr: String): List<String>? {
        return try {
            jsonStr.parseAs<List<String>>()
                .map { it.trim() }
                .filter { isValidImageUrl(it) }
                .ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun findXorKeysFromScript(document: Document): Pair<String, String>? {
        for (script in document.select("script:not([src])")) {
            val data = script.data()
            if (data.length < 100) continue

            val match = SCRIPT_XOR_KEY_REGEX.find(data)
            if (match != null) {
                return Pair(match.groupValues[1], match.groupValues[2])
            }
        }
        return null
    }

    private fun isValidImageUrl(url: String): Boolean {
        return PLACEHOLDER_PATTERNS.none { url.contains(it, ignoreCase = true) } &&
            (url.startsWith("http") || url.startsWith("/"))
    }
}
