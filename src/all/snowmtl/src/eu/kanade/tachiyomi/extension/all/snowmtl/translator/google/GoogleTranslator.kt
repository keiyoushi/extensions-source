package eu.kanade.tachiyomi.extension.all.snowmtl.translator.google

import eu.kanade.tachiyomi.extension.all.snowmtl.translator.TranslatorEngine
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * This client is an adaptation of the following python repository: https://github.com/ssut/py-googletrans.
 */
class GoogleTranslator(private val client: OkHttpClient, private val headers: Headers) : TranslatorEngine {

    private val baseUrl: String = "https://translate.googleapis.com"

    private val webpage: String = "https://translate.google.com"

    private val translatorUrl = "$baseUrl/translate_a/single"

    override val capacity: Int = 5000

    override fun translate(from: String, to: String, text: String): String {
        val request = translateRequest(text, from, to)
        return try { executeRequest(request, text, from, to).text } catch (_: Exception) { text }
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

    private fun executeRequest(request: Request, origin: String, from: String, to: String): Translated {
        val response = client.newCall(request).execute()

        if (response.isSuccessful.not()) {
            throw IOException("Request failed: ${response.code}")
        }

        val json = parseJson(response.body.string())

        val translatedText = extractTranslatedText(json)
        val pronunciation = extractPronunciation(json, translatedText)
        val srcLang = json.jsonArray.getOrNull(2)?.jsonPrimitive?.content ?: from

        return Translated(
            from = srcLang,
            to = to,
            origin = origin,
            text = translatedText,
            pronunciation = pronunciation,
            extraData = parseExtraData(json),
            response = response,
        )
    }

    private fun parseJson(jsonString: String): JsonElement {
        return Json.parseToJsonElement(jsonString)
    }

    private fun extractTranslatedText(data: JsonElement): String {
        return data.jsonArray[0].jsonArray.joinToString("") {
            it.jsonArray[0].jsonPrimitive.content
        }
    }

    private fun extractPronunciation(data: JsonElement, translated: String): String {
        return try {
            data.jsonArray[0].jsonArray[0].jsonArray[1].jsonPrimitive.content
        } catch (e: Exception) {
            translated
        }
    }

    private fun parseExtraData(data: JsonElement): Map<String, Any?> {
        val parts = mapOf(
            "0" to "translation",
            "1" to "all-translations",
            "2" to "original-language",
            "5" to "possible-translations",
            "6" to "confidence",
            "7" to "possible-mistakes",
            "8" to "language",
            "11" to "synonyms",
            "12" to "definitions",
            "13" to "examples",
            "14" to "see-also",
        )

        return parts.mapValues { (index, _) ->
            data.jsonArray.getOrNull(index.toInt())?.let { element ->
                when (element) {
                    is JsonArray -> element.toList()
                    is JsonPrimitive -> element.content
                    else -> null
                }
            }
        }
    }
}
