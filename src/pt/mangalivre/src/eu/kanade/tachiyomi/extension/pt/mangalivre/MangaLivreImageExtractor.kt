package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object MangaLivreImageExtractor {

    private const val PADDING_SIZE = 16

    private val KEY_STRING_REGEX = Regex("""var\s+\w+\s*=\s*["'](-?\d+[^"']+)["']""")

    private val XOR_OP_REGEX = Regex("""calcVal\s*=\s*rawVal\s*\^\s*(\d+)""")

    private val ADD_OP_REGEX = Regex("""calcVal\s*=\s*rawVal\s*\+\s*(\d+)""")

    private val SUB_OP_REGEX = Regex("""calcVal\s*=\s*rawVal\s*-\s*(\d+)""")

    private val DATA_ATTR_REGEX = Regex("""querySelectorAll\s*\(\s*['"]div\[data-(\w+)\]['"]""")

    private enum class Operation { XOR, ADD, SUB }

    private data class DecryptionParams(
        val keyParts: List<Int>,
        val operand: Int,
        val operation: Operation,
        val dataAttrName: String,
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
        return buildString {
            for (el in readingContent.select("div.sec-part-real[data-${params.dataAttrName}]")) {
                append(el.attr("data-${params.dataAttrName}"))
            }
        }
    }

    private fun findDecryptionParams(document: Document): DecryptionParams? {
        var keyString: String? = null
        var operand: Int? = null
        var operation: Operation? = null
        var dataAttrName: String? = null

        for (script in document.select("script:not([src])")) {
            val data = script.data()
            if (data.length < 50) continue

            if (dataAttrName == null) {
                DATA_ATTR_REGEX.find(data)?.let { match ->
                    dataAttrName = match.groupValues[1]
                }
            }

            if (keyString == null) {
                KEY_STRING_REGEX.find(data)?.let { match ->
                    val value = match.groupValues[1]
                    if (value.contains(Regex("""-?\d+[^0-9\-]+-?\d+"""))) {
                        keyString = value
                    }
                }
            }

            if (operation == null) {
                XOR_OP_REGEX.find(data)?.let { match ->
                    operand = match.groupValues[1].toIntOrNull()
                    operation = Operation.XOR
                }
                if (operation == null) {
                    ADD_OP_REGEX.find(data)?.let { match ->
                        operand = match.groupValues[1].toIntOrNull()
                        operation = Operation.ADD
                    }
                }
                if (operation == null) {
                    SUB_OP_REGEX.find(data)?.let { match ->
                        operand = match.groupValues[1].toIntOrNull()
                        operation = Operation.SUB
                    }
                }
            }

            if (keyString != null && operand != null && operation != null && dataAttrName != null) break
        }

        if (keyString == null || operand == null || operation == null || dataAttrName == null) return null

        val separator = keyString!!.firstOrNull { !it.isDigit() && it != '-' }?.toString() ?: ":"
        val keyParts = keyString!!.split(separator).mapNotNull { it.toIntOrNull() }

        if (keyParts.isEmpty()) return null

        return DecryptionParams(keyParts, operand!!, operation!!, dataAttrName!!)
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
                .map { it.trim() }
                .ifEmpty { null }
        }.getOrNull()
    }
}
