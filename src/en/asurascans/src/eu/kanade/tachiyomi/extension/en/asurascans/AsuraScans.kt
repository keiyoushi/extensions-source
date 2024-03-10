package eu.kanade.tachiyomi.extension.en.asurascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class AsuraScans : MangaThemesiaAlt(
    "Asura Scans",
    "https://asuratoon.com",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
    randomUrlPrefKey = "pref_permanent_manga_url_2_en",
) {
    init {
        // remove legacy preferences
        preferences.run {
            if (contains("pref_url_map")) {
                edit().remove("pref_url_map").apply()
            }
            if (contains("pref_base_url_host")) {
                edit().remove("pref_base_url_host").apply()
            }
        }
    }

    override val client = super.client.newBuilder()
        .rateLimit(1, 3)
        .apply {
            val interceptors = interceptors()
            val index = interceptors.indexOfFirst { "Brotli" in it.javaClass.simpleName }
            if (index >= 0) {
                interceptors.add(interceptors.removeAt(index))
            }
        }
        .build()

    override val seriesDescriptionSelector = "div.desc p, div.entry-content p, div[itemprop=description]:not(:has(p))"
    override val seriesArtistSelector = ".fmed b:contains(artist)+span, .infox span:contains(artist)"
    override val seriesAuthorSelector = ".fmed b:contains(author)+span, .infox span:contains(author)"

    override val pageSelector = "div.rdminimal > img, div.rdminimal > p > img, div.rdminimal > a > img, div.rdminimal > p > a > img, " +
        "div.rdminimal > noscript > img, div.rdminimal > p > noscript > img, div.rdminimal > a > noscript > img, div.rdminimal > p > a > noscript > img"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)
        if (query.isBlank()) return request

        val url = request.url.newBuilder()
            .addPathSegment("page/$page/")
            .removeAllQueryParameters("page")
            .removeAllQueryParameters("title")
            .addQueryParameter("s", query)
            .build()

        return request.newBuilder()
            .url(url)
            .build()
    }

    // Skip scriptPages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageSelector)
            .filterNot { it.attr("src").isNullOrEmpty() }
            .mapIndexed { i, img -> Page(i, document.location(), img.attr("abs:src")) }
    }
}
