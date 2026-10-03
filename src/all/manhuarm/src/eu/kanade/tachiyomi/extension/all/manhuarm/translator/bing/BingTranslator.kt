package eu.kanade.tachiyomi.extension.all.manhuarm.translator.bing

import eu.kanade.tachiyomi.extension.all.manhuarm.translator.TranslatorEngine
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class BingTranslator(private val client: OkHttpClient, private val headers: () -> Headers) : TranslatorEngine {

    private var tokens: TokenGroup = TokenGroup()

    override suspend fun translate(from: String, to: String, text: String): String {
        if (!tokens.isValid() && !refreshTokens()) {
            return text
        }
        repeat(ATTEMPTS) {
            try {
                return fetchTranslatedText(from, to, text)
            } catch (_: Exception) {
                refreshTokens()
            }
        }
        return text
    }

    private suspend fun fetchTranslatedText(from: String, to: String, text: String): String {
        val url = "$BASE_URL/ttranslatev3".toHttpUrl().newBuilder()
            .addQueryParameter("isVertical", "1")
            .addQueryParameter("", "") // Present in Bing URL
            .addQueryParameter("IG", tokens.ig)
            .addQueryParameter("IID", tokens.iid)
            .build()

        val apiHeaders = headers().newBuilder()
            .set("Accept", "*/*")
            .set("Origin", BASE_URL)
            .set("Referer", TRANSLATOR_URL)
            .set("Alt-Used", BASE_URL)
            .build()

        val payload = FormBody.Builder()
            .add("fromLang", from)
            .add("to", to)
            .add("text", text)
            .add("tryFetchingGenderDebiasedTranslations", "true")
            .add("token", tokens.token)
            .add("key", tokens.key)
            .build()

        return client.post(url.toString(), apiHeaders, payload).parseAs<List<TranslateDto>>().first().text
    }

    private suspend fun refreshTokens(): Boolean {
        tokens = try {
            loadTokens()
        } catch (_: Exception) {
            TokenGroup()
        }
        return tokens.isValid()
    }

    private suspend fun loadTokens(): TokenGroup {
        val document = client.get(TRANSLATOR_URL, headers()).asJsoup()

        val scripts = document.select("script").map(Element::data)

        val scriptOne = scripts.firstOrNull(TOKENS_REGEX::containsMatchIn) ?: return TokenGroup()
        val scriptTwo = scripts.firstOrNull(IG_PARAM_REGEX::containsMatchIn) ?: return TokenGroup()

        val matchOne = TOKENS_REGEX.find(scriptOne)?.groups
        val matchTwo = IG_PARAM_REGEX.find(scriptTwo)?.groups

        return TokenGroup(
            token = matchOne?.get(4)?.value ?: "",
            key = matchOne?.get(3)?.value ?: "",
            ig = matchTwo?.get(1)?.value ?: "",
            iid = document.selectFirst("div[data-iid]:not([class])")?.attr("data-iid") ?: "",
        )
    }

    companion object {
        const val HOST = "www.bing.com"
        private const val BASE_URL = "https://$HOST"
        private const val TRANSLATOR_URL = "$BASE_URL/translator"
        private const val ATTEMPTS = 3
        private val TOKENS_REGEX = """params_AbusePreventionHelper(\s+)?=(\s+)?[^\[]\[(\d+),"([^"]+)""".toRegex()
        private val IG_PARAM_REGEX = """IG:"([^"]+)""".toRegex()
    }
}
