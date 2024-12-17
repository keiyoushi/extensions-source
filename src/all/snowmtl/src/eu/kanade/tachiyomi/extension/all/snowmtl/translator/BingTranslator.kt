package eu.kanade.tachiyomi.extension.all.snowmtl.translator

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class BingTranslator(private val client: OkHttpClient, private val headers: Headers) : TranslatorEngine {

    private val baseUrl = "https://www.bing.com"

    private val translatorUrl = "$baseUrl/translator"

    private val json: Json by injectLazy()

    private var tokens: TokenGroup = TokenGroup()

    override val capacity: Int = MAX_CHARS_ALLOW

    private val attempts = 3

    override fun translate(from: String, to: String, text: String): String {
        if (tokens.isNotValid() && refreshTokens().not()) {
            return text
        }

        repeat(attempts) {
            try {
                val dto = client
                    .newCall(translatorRequest(from, to, text))
                    .execute()
                    .parseAs<List<TranslateDto>>()

                return dto.firstOrNull()?.text ?: text
            } catch (e: Exception) {
                refreshTokens()
            }
        }
        return text
    }

    private fun refreshTokens(): Boolean {
        tokens = loadTokens()
        return tokens.isValid()
    }

    private fun translatorRequest(from: String, to: String, text: String): Request {
        val url = "$baseUrl/ttranslatev3".toHttpUrl().newBuilder()
            .addQueryParameter("isVertical", "1")
            .addQueryParameter("", "") // Present in Bing URL
            .addQueryParameter("IG", tokens.ig)
            .addQueryParameter("IID", tokens.iid)
            .build()

        val headersApi = headers.newBuilder()
            .set("Accept", "*/*")
            .set("Origin", baseUrl)
            .set("Referer", translatorUrl)
            .set("Alt-Used", baseUrl)
            .build()

        val payload = FormBody.Builder()
            .add("fromLang", from)
            .add("to", to)
            .add("text", text)
            .add("tryFetchingGenderDebiasedTranslations", "true")
            .add("token", tokens.token)
            .add("key", tokens.key)
            .build()

        return POST(url.toString(), headersApi, payload)
    }

    private fun loadTokens(): TokenGroup {
        val document = client.newCall(GET(translatorUrl, headers)).execute().asJsoup()

        val scripts = document.select("script")
            .map(Element::data)

        val scriptOne: String = scripts.firstOrNull(TOKENS_REGEX::containsMatchIn)
            ?: return TokenGroup()

        val scriptTwo: String = scripts.firstOrNull(IG_PARAM_REGEX::containsMatchIn)
            ?: return TokenGroup()

        val matchOne = TOKENS_REGEX.find(scriptOne)?.groups
        val matchTwo = IG_PARAM_REGEX.find(scriptTwo)?.groups

        return TokenGroup(
            token = matchOne?.get("token")?.value ?: "",
            key = matchOne?.get("key")?.value ?: "",
            ig = matchTwo?.get("ig")?.value ?: "",
            iid = document.selectFirst("div[data-iid]:not([class])")?.attr("data-iid") ?: "",
        )
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromStream(body.byteStream())
    }

    companion object {
        val TOKENS_REGEX = """params_AbusePreventionHelper(\s+)?=(\s+)?[^\[]\[(?<key>\d+),"(?<token>[^"]+)""".toRegex()
        val IG_PARAM_REGEX = """IG:"(?<ig>[^"]+)""".toRegex()
        const val MAX_CHARS_ALLOW = 1000
    }
}

private class TokenGroup(
    val token: String = "",
    val key: String = "",
    val iid: String = "",
    val ig: String = "",
) {
    fun isNotValid() = listOf(token, key, iid, ig).any(String::isBlank)

    fun isValid() = isNotValid().not()
}

@Serializable
private class TranslateDto(
    val translations: List<TextTranslated>,
) {
    val text = translations.firstOrNull()?.text ?: ""
}

@Serializable
private class TextTranslated(
    val text: String,
)
