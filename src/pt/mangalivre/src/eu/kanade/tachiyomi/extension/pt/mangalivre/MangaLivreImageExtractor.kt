package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document

object MangaLivreImageExtractor {

    private const val PADDING_SIZE = 16
    private val PLACEHOLDER_PATTERNS = listOf("hi.png", "placeholder", "loading")

    private val KEY_ARRAY_REGEX = Regex("""var\s+(\w+)\s*=\s*\[(-?\d+(?:\s*,\s*-?\d+){15,})\]""")
    private val KEY_OFFSET_REGEX = Regex("""String\.fromCharCode\s*\(\s*\w+\s*([+\-])\s*(\d+)\s*\)""")
    private val URL_PATTERN = Regex("""https?://[^\s"\\]+\.(webp|jpg|jpeg|png|gif)""", RegexOption.IGNORE_CASE)

    private data class DecryptionParams(
        val keyArray: List<Int>,
        val keyOffset: Int,
        val operation: Char,
    )

    fun extractImageUrls(document: Document): List<String>? {
        val readingContent = document.selectFirst(".reading-content") ?: return null
        val params = findDecryptionParams(document) ?: return null

        val encodedData = collectEncodedData(readingContent)
        if (encodedData.isBlank()) return null

        val decodedStr = decodeData(encodedData, params)
        return parseUrls(decodedStr)
    }

    private fun collectEncodedData(readingContent: org.jsoup.nodes.Element): String {
        val ignoredAttrs = setOf("data-site-url", "style", "class", "id")

        return buildString {
            for (el in readingContent.select("div.sec-part[style*=display:none]")) {
                val dataAttr = el.attributes()
                    .firstOrNull { it.key.startsWith("data-") && it.key !in ignoredAttrs && it.value.length > 20 }
                if (dataAttr != null) {
                    append(dataAttr.value)
                }
            }
        }
    }

    private fun findDecryptionParams(document: Document): DecryptionParams? {
        var keyArray: List<Int>? = null
        var keyOffset: Int? = null
        var operation: Char? = null

        for (script in document.select("script:not([src])")) {
            val data = script.data()
            if (data.length < 50) continue

            if (keyArray == null) {
                KEY_ARRAY_REGEX.find(data)?.let { match ->
                    keyArray = match.groupValues[2].split(",").mapNotNull { it.trim().toIntOrNull() }
                }
            }

            if (operation == null) {
                KEY_OFFSET_REGEX.find(data)?.let { match ->
                    operation = match.groupValues[1].firstOrNull()
                    keyOffset = match.groupValues[2].toIntOrNull()
                }
            }

            if (keyArray != null && keyOffset != null && operation != null) break
        }

        return if (keyArray != null && keyOffset != null && operation != null) {
            DecryptionParams(keyArray!!, keyOffset!!, operation!!)
        } else {
            null
        }
    }

    private fun decodeData(encodedData: String, params: DecryptionParams): String {
        val decodedBytes = runCatching { Base64.decode(encodedData, Base64.DEFAULT) }.getOrNull() ?: return ""

        val realKey = params.keyArray.map { value ->
            val charCode = if (params.operation == '+') value + params.keyOffset else value - params.keyOffset
            (charCode and 0xFF).toChar()
        }.joinToString("")

        val result = buildString {
            for (i in decodedBytes.indices) {
                append(((decodedBytes[i].toInt() and 0xFF) xor realKey[i % realKey.length].code).toChar())
            }
        }

        val jsonStart = result.indexOf("[\"")
        return when {
            jsonStart >= 0 -> result.substring(jsonStart)
            result.length > PADDING_SIZE -> result.substring(PADDING_SIZE)
            else -> result
        }
    }

    private fun parseUrls(jsonStr: String): List<String>? {
        return runCatching {
            jsonStr.parseAs<List<String>>()
                .map { it.trim() }
                .filter { isValidImageUrl(it) }
                .ifEmpty { null }
        }.getOrElse {
            extractUrlsWithRegex(jsonStr)
        }
    }

    private fun extractUrlsWithRegex(data: String): List<String>? {
        val normalizedData = data.replace("\\/", "/")
        return URL_PATTERN.findAll(normalizedData)
            .map { it.value.trim() }
            .filter { isValidImageUrl(it) }
            .distinct()
            .toList()
            .ifEmpty { null }
    }

    private fun isValidImageUrl(url: String): Boolean {
        return PLACEHOLDER_PATTERNS.none { url.contains(it, ignoreCase = true) } &&
            (url.startsWith("http") || url.startsWith("/"))
    }
}
