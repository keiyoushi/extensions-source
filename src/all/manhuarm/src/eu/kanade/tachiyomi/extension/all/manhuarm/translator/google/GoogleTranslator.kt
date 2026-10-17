package eu.kanade.tachiyomi.extension.all.manhuarm.translator.google

import eu.kanade.tachiyomi.extension.all.manhuarm.translator.TranslatorEngine
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * This client is an adaptation of the following python repository: https://github.com/ssut/py-googletrans.
 */
class GoogleTranslator(private val client: OkHttpClient, private val headers: () -> Headers) : TranslatorEngine {

    override suspend fun translate(from: String, to: String, text: String): String = try {
        fetchTranslatedText(from, to, text)
    } catch (_: Exception) {
        text
    }

    private suspend fun fetchTranslatedText(from: String, to: String, text: String): String {
        val url = "https://$HOST/translate_a/single".toHttpUrl().newBuilder()
            .setQueryParameter("client", "gtx")
            .setQueryParameter("sl", from)
            .setQueryParameter("tl", to)
            .setQueryParameter("hl", to)
            .setQueryParameter("ie", "UTF-8")
            .setQueryParameter("oe", "UTF-8")
            .setQueryParameter("otf", "1")
            .setQueryParameter("ssel", "0")
            .setQueryParameter("tsel", "0")
            .setQueryParameter("tk", "xxxx")
            .setQueryParameter("q", text)
            .apply {
                arrayOf("at", "bd", "ex", "ld", "md", "qca", "rw", "rm", "ss", "t").forEach {
                    addQueryParameter("dt", it)
                }
            }
            .build()

        val apiHeaders = headers().newBuilder()
            .set("Origin", WEBPAGE)
            .set("Alt-Used", WEBPAGE.substringAfterLast("/"))
            .set("Referer", "$WEBPAGE/")
            .build()

        return client.get(url, apiHeaders).parseAs<JsonElement>()
            .jsonArray[0].jsonArray
            .joinToString("") { it.jsonArray[0].jsonPrimitive.content }
    }

    companion object {
        const val HOST = "translate.googleapis.com"
        private const val WEBPAGE = "https://translate.google.com"
    }
}
