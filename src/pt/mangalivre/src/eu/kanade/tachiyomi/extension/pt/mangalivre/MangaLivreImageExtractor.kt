package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object MangaLivreImageExtractor {

    private const val PADDING_SIZE = 16

    private val ATTR_STRING_REGEX = Regex("""(?:var|let|const)\s+a\s*=\s*["'](data-[\w-]+)["']""")

    private val KS_REGEX = Regex("""(?:var|let|const)\s+ks\s*=\s*["']([^"']+)["']""")

    private val MODE_REGEX = Regex("""(?:var|let|const)\s+mode\s*=\s*(\d+)""")

    private val SALT_REGEX = Regex("""(?:var|let|const)\s+salt\s*=\s*(\d+)""")

    private enum class Operation { XOR, ADD, SUB }

    private data class DecryptionParams(
        val keyParts: List<Int>,
        val operand: Int,
        val operation: Operation,
        val dataAttr: String,
    )

    fun extractImageUrls(document: Document): List<String>? {
        val readingContent = document.selectFirst(".reading-content") ?: return null
        val params = findDecryptionParams(document) ?: return null

        val encodedData = collectEncodedData(readingContent, params)
        if (encodedData.isBlank()) return null

        val decodedStr = decodeData(encodedData, params)
        return parseUrls(decodedStr)
    }

    private fun collectEncodedData(readingContent: Element, params: DecryptionParams): String {
        val attr = params.dataAttr
        return buildString {
            for (el in readingContent.select("[$attr]")) {
                append(el.attr(attr))
            }
        }
    }

    private fun findDecryptionParams(document: Document): DecryptionParams? {
        var dataAttr: String? = null

        var newKs: String? = null
        var newMode: Int? = null
        var newSalt: Int? = null

        for (script in document.select("script:not([src])")) {
            val data = script.data()
            if (data.length < 50) continue

            if (dataAttr == null) {
                ATTR_STRING_REGEX.find(data)?.let { match ->
                    dataAttr = match.groupValues[1]
                }
            }

            if (newKs == null) {
                KS_REGEX.find(data)?.let { match ->
                    newKs = match.groupValues[1]
                }
            }

            if (newMode == null) {
                MODE_REGEX.find(data)?.let { match ->
                    newMode = match.groupValues[1].toIntOrNull()
                }
            }

            if (newSalt == null) {
                SALT_REGEX.find(data)?.let { match ->
                    newSalt = match.groupValues[1].toIntOrNull()
                }
            }

            if (dataAttr != null && newKs != null && newMode != null && newSalt != null) {
                val separator = detectSeparator(newKs!!)
                val keyParts = newKs!!.split(separator).mapNotNull { it.toIntOrNull() }
                if (keyParts.isEmpty()) return null

                val (op, opnd) = when (newMode) {
                    0 -> Operation.SUB to newSalt!!
                    1 -> Operation.ADD to newSalt!!
                    else -> Operation.XOR to newSalt!!
                }

                return DecryptionParams(
                    keyParts = keyParts,
                    operand = opnd,
                    operation = op,
                    dataAttr = dataAttr!!,
                )
            }
        }

        return null
    }

    private fun detectSeparator(ks: String): String {
        return when {
            '|' in ks -> "|"
            '_' in ks -> "_"
            else -> "."
        }
    }

    private fun decodeData(encodedData: String, params: DecryptionParams): String {
        val decodedBytes = runCatching { Base64.decode(encodedData, Base64.DEFAULT) }.getOrNull()
            ?: return ""

        val realKey = params.keyParts.map { value ->
            val charCode = when (params.operation) {
                Operation.XOR -> value xor params.operand
                Operation.ADD -> value + params.operand
                Operation.SUB -> value - params.operand
            }
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
                .ifEmpty { null }
        }.getOrNull()
    }
}
