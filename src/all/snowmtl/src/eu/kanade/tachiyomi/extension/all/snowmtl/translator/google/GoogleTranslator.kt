package eu.kanade.tachiyomi.extension.all.snowmtl.translator.google

import eu.kanade.tachiyomi.extension.all.snowmtl.translator.TranslatorEngine
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException

/**
 * This client is an adaptation of the following python repository: https://github.com/ssut/py-googletrans.
 */
class GoogleTranslator(private val client: OkHttpClient, private val headers: Headers) : TranslatorEngine {

    private val baseUrl: String = "https://translate.googleapis.com"

    private val webpage: String = "https://translate.google.com"

    private val translatorUrl = "$baseUrl/translate_a/single"

    override val capacity: Int = 5000

    private val json: Json by injectLazy()

    override fun translate(from: String, to: String, text: String): String {
        val request = translateRequest(text, from, to)
        return try { fetchTranslatedText(request) } catch (_: Exception) { text }
    }

    private fun translateRequest(text: String, from: String, to: String): Request {
        return GET(clientUrlBuilder(text, from, to).build(), headersBuilder().build())
    }

    private fun headersBuilder(): Headers.Builder = headers.newBuilder()
        .set("Origin", webpage)
        .set("Alt-Used", webpage.substringAfterLast("/"))
        .set("Referer", "$webpage/")

    private fun clientUrlBuilder(text: String, src: String, dest: String, token: String = "xxxx"): HttpUrl.Builder {
        return translatorUrl.toHttpUrl().newBuilder()
            .setQueryParameter("client", "gtx")
            .setQueryParameter("sl", src)
            .setQueryParameter("tl", dest)
            .setQueryParameter("hl", dest)
            .setQueryParameter("ie", Charsets.UTF_8.toString())
            .setQueryParameter("oe", Charsets.UTF_8.toString())
            .setQueryParameter("otf", "1")
            .setQueryParameter("ssel", "0")
            .setQueryParameter("tsel", "0")
            .setQueryParameter("tk", token)
            .setQueryParameter("q", text)
            .apply {
                arrayOf("at", "bd", "ex", "ld", "md", "qca", "rw", "rm", "ss", "t").forEach {
                    addQueryParameter("dt", it)
                }
            }
    }

    private fun fetchTranslatedText(request: Request): String {
        val response = client.newCall(request).execute()

        if (response.isSuccessful.not()) {
            throw IOException("Request failed: ${response.code}")
        }

        return response.parseJson().let(::extractTranslatedText)
    }

    private fun Response.parseJson(): JsonElement = json.parseToJsonElement(this.body.string())

    private fun extractTranslatedText(data: JsonElement): String {
        return data.jsonArray[0].jsonArray.joinToString("") {
            it.jsonArray[0].jsonPrimitive.content
        }
    }
}
