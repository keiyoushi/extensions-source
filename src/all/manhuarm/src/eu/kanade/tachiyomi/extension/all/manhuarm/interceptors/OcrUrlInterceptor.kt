package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import org.jsoup.nodes.Document

class OcrUrlInterceptor {

    /**
     * The chapter page embeds the OCR endpoint and its single-use gate credentials in a
     * `_0xvault` array. The site's own script refuses to call the endpoint when it detects a
     * patched `XMLHttpRequest`/`fetch` — sending a decoy request instead — so the credentials are
     * read straight out of the HTML rather than by intercepting the page's request.
     */
    fun getOcrRequest(document: Document): OcrRequest? {
        val vault = VAULT_REGEX.find(document.html())
            ?.groupValues?.get(1)
            ?.let { entries -> ENTRY_REGEX.findAll(entries).map(::entryValue).toList() }
            ?: return null

        if (vault.size <= REF_INDEX) {
            return null
        }

        val url = vault.getOrNull(URL_INDEX)?.takeIf { it.contains("fetch-ocr") } ?: return null

        return OcrRequest(
            url = url,
            body = """{"cid":"${vault[CID_INDEX]}","ref":"${vault[REF_INDEX]}"}""",
            interceptedHeaders = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Cache-Control" to "no-cache",
                "X-Gate-Token" to vault[TOKEN_INDEX],
                "X-Gate-Nonce" to vault[NONCE_INDEX],
                "X-Gate-Timestamp" to vault[TIMESTAMP_INDEX],
            ),
        )
    }

    private fun entryValue(match: MatchResult): String = match.groups[1]?.value?.replace("\\/", "/") ?: match.groupValues[2]

    companion object {
        private val VAULT_REGEX = Regex("""_0xvault\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        private val ENTRY_REGEX = Regex(""""([^"]*)"|(\d+)""")

        private const val CID_INDEX = 0
        private const val TOKEN_INDEX = 1
        private const val TIMESTAMP_INDEX = 2
        private const val NONCE_INDEX = 3
        private const val URL_INDEX = 4
        private const val REF_INDEX = 5
    }
}

data class OcrRequest(
    val url: String,
    val body: String,
    val interceptedHeaders: Map<String, String>,
)
