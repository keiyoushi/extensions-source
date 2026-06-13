package eu.kanade.tachiyomi.extension.pt.littletyrant

import android.util.Base64
import org.jsoup.nodes.Document

class Decoder {
    fun extractPaths(document: Document): List<String> {
        val urlScript = document.selectFirst("script:containsData(var pages)")?.data()
            ?: error("No image URLs")

        val match = PAGES_REGEX.find(urlScript) ?: error("Unable to parse pages")

        return match.groupValues[1]
            .split(",")
            .map { it.trim().trim('"').trim('\'') }
            .filter { it.isNotEmpty() }
            .map { base64 ->
                Base64.decode(base64, Base64.DEFAULT)
                    .toString(Charsets.UTF_8)
                    .trim()
            }
    }

    companion object {
        private val PAGES_REGEX = Regex(
            """var pages\s*=\s*\[(.*?)\]""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
