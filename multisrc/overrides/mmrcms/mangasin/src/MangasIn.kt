package eu.kanade.tachiyomi.extension.es.mangasin

import android.net.Uri
import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale


class MangasIn : MMRCMS("Mangas.in", "https://mangas.in", "es") {

    private val json: Json by injectLazy()

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private var key = ""

    private fun getKey(): String {
        val script = client.newCall(GET("$baseUrl/js/datachs.js")).execute().body.string()
        val deobfuscatedScript = Deobfuscator.deobfuscateScript(script)
            ?: throw Exception("No se pudo desofuscar el script")

        return KEY_REGEX.find(deobfuscatedScript)?.groupValues?.get(1)
            ?: throw Exception("No se pudo encontrar la clave")

    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url: Uri.Builder
        when {
            query.isNotBlank() -> {
                url = Uri.parse("$baseUrl/search")!!.buildUpon()
                url.appendQueryParameter("q", query)
            }
            else -> {
                url = Uri.parse("$baseUrl/filterList?page=$page")!!.buildUpon()
                filters.filterIsInstance<UriFilter>()
                    .forEach { it.addToUri(url) }
            }
        }
        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return if (listOf("query", "q").any { it in response.request.url.queryParameterNames }) {
            val searchResult = json.decodeFromString<List<SearchResult>>(response.body.string())
            MangasPage(
                searchResult
                    .map {
                        SManga.create().apply {
                            url = getUrlWithoutBaseUrl(itemUrl + it.slug)
                            title = it.name
                            thumbnail_url = "$baseUrl/uploads/manga/${it.slug}/cover/cover_250x350.jpg"
                        }
                    },
                false,
            )
        } else {
            internalMangaParse(response)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaUrl = document.location().removeSuffix("/")
        val receivedData = RECEIVED_DATA_REGEX.find(document.html())?.groupValues?.get(1) ?: throw Exception("No se pudo encontrar la lista de capítulos")
        val unescapedReceivedData = receivedData.unescape()
        val chapterData = json.decodeFromString<CDT>(unescapedReceivedData)
        val salt = chapterData.s.decodeHex()

        val unsaltedCipherText = Base64.decode(chapterData.ct, Base64.DEFAULT)
        val cipherText = SALTED + salt + unsaltedCipherText

        val decrypted = CryptoAES.decrypt(Base64.encodeToString(cipherText, Base64.DEFAULT), key).ifEmpty {
            key = getKey()
            CryptoAES.decrypt(Base64.encodeToString(cipherText, Base64.DEFAULT), key)
        }

        val unescaped = decrypted.unescapeJava().removeSurrounding("\"").unescape()

        val chapters = json.decodeFromString<List<Chapter>>(unescaped)

        return chapters.map {
            SChapter.create().apply {
                name = "Capítulo ${it.number}: ${it.name}"
                date_upload = it.createdAt.parseDate()
                setUrlWithoutDomain("$mangaUrl/${it.slug}")
            }
        }
    }

    private fun String.unescape(): String {
        return UNESCAPE_REGEX.replace(this) {
            it.groupValues[1]
        }
    }

    private fun String.unescapeJava(): String {
        var escaped = this
        if (!escaped.contains("\\u")) return escaped

        var processed = ""
        var position = escaped.indexOf("\\u")
        while (position != -1) {
            if (position != 0) {
                processed += escaped.substring(0, position)
            }
            val token = escaped.substring(position + 2, position + 6)
            escaped = escaped.substring(position + 6)
            processed += Integer.parseInt(token, 16).toChar()
            position = escaped.indexOf("\\u")
        }
        processed += escaped
        return processed
    }

    private fun String.parseDate(): Long {
        return dateFormat.parse(this)?.time ?: 0L
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    companion object {
        val UNESCAPE_REGEX = """\\(.)""".toRegex()
        val RECEIVED_DATA_REGEX = """receivedData\s*=\s*["'](.*)["']\s*;""".toRegex()
        val KEY_REGEX = """decrypt\(.*'(.*)'.*\)""".toRegex()
        val SALTED = "Salted__".toByteArray(Charsets.UTF_8)

        val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        }
    }
}
