package eu.kanade.tachiyomi.extension.zh.rumanhua

import android.util.Base64
import org.jsoup.nodes.Document

// all2.js?v=2.3
class PageDecrypt {
    private val SCRIPT_PATTERN = "eval(function(p,a,c,k,e,d)"
    private val CONTENT_MARKER = "var __c0rst96=\""

    fun toDecrypt(document: Document): String {
        document.head().select("script").forEach { script ->
            val scriptStr = script.data().trim()
            if (scriptStr.startsWith(SCRIPT_PATTERN)) {
                val obf = obfuscate(extractPackerParams(scriptStr)).substringAfter(CONTENT_MARKER)
                    .trimEnd('"')

                val selectedIndex =
                    document.selectFirst("div.readerContainer")?.attr("data-id")?.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid container index")

                return decryptToString(obf, selectedIndex)
            }
        }
        throw IllegalArgumentException("No valid script found for decryption")
    }

    private fun decryptToString(encodedContent: String, selectedIndex: Int): String {
        val boundedIndex = selectedIndex.coerceIn(0, 9)
        val xorKeyEncoded = getEncodedKeyForIndex(boundedIndex)
        val xorKey = base64Decode(xorKeyEncoded)
        val encryptedContent = base64Decode(encodedContent)
        val keyLength = xorKey.length
        val xoredString = StringBuilder(encryptedContent.length)

        for (i in encryptedContent.indices) {
            val k = i % keyLength
            val xoredChar = encryptedContent[i].code xor xorKey[k].code
            xoredString.append(xoredChar.toChar())
        }

        return base64Decode(xoredString.toString())
    }

    private fun getEncodedKeyForIndex(index: Int): String {
        return when (index) {
            0 -> "c21raHkyNTg="
            1 -> "c21rZDk1ZnY="
            2 -> "bWQ0OTY5NTI="
            3 -> "Y2Rjc2R3cQ=="
            4 -> "dmJmc2EyNTY="
            5 -> "Y2F3ZjE1MWM="
            6 -> "Y2Q1NmN2ZGE="
            7 -> "OGtpaG50OQ=="
            8 -> "ZHNvMTV0bG8="
            9 -> "NWtvNnBsaHk="
            else -> ""
        }
    }

    private fun base64Decode(input: String): String {
        if (input.isEmpty()) return input
        return String(Base64.decode(input, Base64.DEFAULT))
    }

    private fun obfuscate(pl: PackerPayload): String {
        fun eFunction(c: Int): String {
            return if (c < pl.a) {
                if (c > 35) {
                    (c + 29).toChar().toString()
                } else {
                    c.toString(36)
                }
            } else {
                eFunction(c / pl.a) + if (c % pl.a > 35) {
                    (c % pl.a + 29).toChar().toString()
                } else {
                    (c % pl.a).toString(36)
                }
            }
        }

        val d = mutableMapOf<String, String>()
        var tempC = pl.c
        while (tempC-- > 0) {
            d[eFunction(tempC)] = pl.k.getOrElse(tempC) { eFunction(tempC) }
        }

        var result = pl.p
        tempC = pl.c
        while (tempC-- > 0) {
            val key = eFunction(tempC)
            val replacement = d[key] ?: ""
            result = result.replace(Regex("\\b${Regex.escape(key)}\\b"), replacement)
        }

        return result
    }

    private class PackerPayload(val p: String, val a: Int, val c: Int, val k: List<String>)

    private fun extractPackerParams(source: String): PackerPayload {
        val args = source.substringAfter("}(").substringBefore(".split('|')").split(",")
        return PackerPayload(
            args[0].trim('\''),
            args[1].toInt(),
            args[2].toInt(),
            args[3].trim('\'').split("|"),
        )
    }
}
