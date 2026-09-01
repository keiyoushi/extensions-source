package eu.kanade.tachiyomi.extension.vi.hentaicube

import android.content.SharedPreferences
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiCB : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = followRedirects(false)
        .addInterceptor { chain ->
            val maxRedirects = 5
            var request = chain.request()
            var response = chain.proceed(request)
            var redirectCount = 0

            while (response.isRedirect && redirectCount < maxRedirects) {
                val newUrl = response.header("Location") ?: break
                val newUrlHttp = newUrl.toHttpUrl()
                val redirectedDomain = newUrlHttp.run { "$scheme://$host" }
                if (redirectedDomain != baseUrl) {
                    synchronized(prefsLock) {
                        preferences.edit().putString(BASE_URL_PREF, redirectedDomain).commit()
                    }
                }
                response.close()
                request = request.newBuilder()
                    .url(newUrlHttp)
                    .build()
                response = chain.proceed(request)
                redirectCount++
            }
            if (redirectCount >= maxRedirects) {
                response.close()
                throw java.io.IOException("Too many redirects: $maxRedirects")
            }
            response
        }
        .rateLimit(3)

    private val preferences: SharedPreferences = getPreferences()
    private val prefsLock = Any()

    override val filterNonMangaItems = false

    override val mangaSubString = "read"

    override val altNameSelector = ".post-content_item:contains(Tên khác) .summary-content"

    private val thumbnailOriginalUrlRegex = Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$")

    override val chapterMode = ChapterMode.MangaAjaxPaginated

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val queryFixed = query
            .replace("–", "-")
            .replace("’", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("…", "...")

        return super.getSearchMangaList(page, queryFixed, filters)
    }

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = super.processThumbnail(url, fromSearch)?.replace(thumbnailOriginalUrlRegex, "$1")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()

        val readerElement = document.selectFirst(".masr2-reader[data-masr2-token]")
            ?: error("Reader element not found")

        val token = readerElement.attr("data-masr2-token")
        if (token.isEmpty()) error("Token not found in reader element")

        val clientId = generateClientId()
        val apiHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("Referer", chapterUrl)
            .build()

        val imageUrls = mutableListOf<String>()
        var currentToken = token

        do {
            val url = buildApiUrl(currentToken, clientId)
            val response = client.get(url, apiHeaders)
            val data = response.parseAs<ReaderPageResponse>()

            imageUrls.addAll(data.items)

            if (data.done || data.nextToken.isNullOrEmpty()) break
            currentToken = data.nextToken
        } while (true)

        return imageUrls.mapIndexed { i, imageUrl ->
            Page(i, chapterUrl, imageUrl)
        }
    }

    private fun generateClientId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun buildApiUrl(token: String, clientId: String): String = "$baseUrl$PAGES_URL".toHttpUrl()
        .newBuilder()
        .addQueryParameter("token", token)
        .addQueryParameter("cid", clientId)
        .build()
        .toString()

    @Serializable
    private class ReaderPageResponse(
        @SerialName("items") val items: List<String>,
        @SerialName("done") val done: Boolean,
        @SerialName("next_token") val nextToken: String? = null,
    )

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val PAGES_URL = "/wp-json/manga-reader/v2/pages"
    }
}
