package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Manhwa18Cc : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    override val fetchGenres = false

    override fun popularMangaSelector() = when (lang) {
        "en" -> "div.manga-item:not(:has(h3 a[title$='Raw']))"
        "ko" -> "div.manga-item:has(h3 a[title$='Raw'])"
        else -> "div.manga-item"
    }

    override val popularMangaUrlSelector = "div.manga-item div.data a"

    override fun popularMangaNextPageSelector() = "ul.pagination li.next a"

    override fun popularMangaRequest(page: Int): Request = when (lang) {
        "ko" -> GET("$baseUrl/raw/$page", headers)
        else -> GET("$baseUrl/webtoons/$page?orderby=trending", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/webtoons/$page", headers)

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        }

        filters.firstInstanceOrNull<Manhwa18GenreFilter>()
            ?.takeIf { it.selected != null }
            ?.let {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("webtoon-genre")
                    .addPathSegment(it.selected.toString())
                    .addPathSegment(page.toString())
                    .build()

                return GET(url, headers)
            }

        filters.firstInstanceOrNull<Manhwa18OrderFilter>()
            ?.takeIf { it.selected != null }
            ?.let {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("webtoons")
                    .addPathSegment(page.toString())
                    .addQueryParameter("orderby", it.selected.toString())
                    .build()

                return GET(url, headers)
            }

        filters.firstInstanceOrNull<Manhwa18StatusFilter>()
            ?.takeIf { it.selected != null }
            ?.let {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment(it.selected.toString())
                    .addPathSegment(page.toString())
                    .build()

                return GET(url, headers)
            }

        return popularMangaRequest(page)
    }

    override val mangaSubString = "webtoon"

    override val mangaDetailsSelectorDescription = "div.panel-story-description div.dsct"

    override fun chapterListSelector() = "li.a-h"

    override fun chapterDateSelector() = "span.chapter-time"

    override val pageListParseSelector = "div.read-content img"

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filters are ignored when using text search."),
        Manhwa18OrderFilter(intl["order_by_filter_title"]),
        Filter.Separator(),
        Manhwa18GenreFilter(intl["genre_filter_title"]),
        Filter.Separator(),
        Manhwa18StatusFilter(intl["status_filter_title"]),
        Filter.Separator(),
        Filter.Header("Only one filter can be used"),
        Filter.Header("If more than one is selected, they will be applied based on priority"),
        Filter.Header("Priority: Genre > Order > Status"),
    )
}
