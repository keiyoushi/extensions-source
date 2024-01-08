package eu.kanade.tachiyomi.extension.en.toonily

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

private const val domain = "toonily.com"
class Toonily : Madara(
    "Toonily",
    "https://$domain",
    "en",
    SimpleDateFormat("MMM d, yy", Locale.US),
) {

    private val cookieInterceptor = CookieInterceptor(domain, "toonily-mature", "1")
    override val client: OkHttpClient = super.client.newBuilder()
        .addNetworkInterceptor(cookieInterceptor)
        .build()

    override val mangaSubString = "webtoon"

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }

    private fun searchPage(page: Int, query: String): String {
        val urlQuery = query.trim()
            .lowercase(Locale.US)
            .replace(titleSpecialCharactersRegex, "-")
            .replace(trailingHyphenRegex, "")
            .let { if (it.isNotEmpty()) "$it/" else it }
        return if (page > 1) {
            "search/${urlQuery}page/$page/"
        } else {
            "search/$urlQuery"
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)

        val queries = request.url.queryParameterNames
            .filterNot { it == "s" }

        val newUrl = "$baseUrl/${searchPage(page, query)}".toHttpUrlOrNull()!!.newBuilder().apply {
            queries.map { q ->
                request.url.queryParameterValues(q).map {
                    this.addQueryParameter(q, it)
                }
            }
        }.build()

        return request.newBuilder()
            .url(newUrl)
            .build()
    }

    override fun genresRequest(): Request {
        return GET("$baseUrl/search/?post_type=wp-manga", headers)
    }

    // The source customized the Madara theme and broke the filter.
    override val filterNonMangaItems = false

    override val useNewChapterEndpoint: Boolean = true

    override fun searchMangaSelector() = "div.page-item-detail.manga"

    override val pageListParseSelector = "div.reading-content div"

    override fun parseChapterDate(date: String?): Long {
        val formattedDate = if (date?.contains("UP") == true) "today" else date
        return super.parseChapterDate(formattedDate)
    }

    companion object {
        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
        val trailingHyphenRegex = "-+$".toRegex()
    }
}
