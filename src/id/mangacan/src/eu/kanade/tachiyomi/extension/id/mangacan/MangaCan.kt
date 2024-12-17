package eu.kanade.tachiyomi.extension.id.mangacan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import rx.Observable

class MangaCan : MangaThemesia(
    "Manga Can",
    "https://mangacanblog.com",
    "id",
    "/",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val supportsLatest = false

    override val seriesGenreSelector = ".seriestugenre a[href*=genre]"

    override val pageSelector = "div.images img"

    override fun imageRequest(page: Page): Request {
        return super.imageRequest(page).newBuilder()
            .removeHeader("Referer")
            .addHeader("Referer", "$baseUrl/")
            .build()
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX).not()) return super.fetchSearchManga(page, query, filters)
        val url = query.substringAfter(URL_SEARCH_PREFIX)
        return fetchMangaDetails(SManga.create().apply { setUrlWithoutDomain(url) })
            .map {
                it.apply { setUrlWithoutDomain(url) }
                MangasPage(listOf(it), false)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var selected = ""
        with(filters.filterIsInstance<GenreFilter>()) {
            selected = when {
                isNotEmpty() -> firstOrNull { it.selectedValue().isNotBlank() }?.selectedValue() ?: ""
                else -> ""
            }
        }

        if (query.isBlank() && selected.isBlank()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("cari")
                .addPathSegment(query.trim().replace(SPACES_REGEX, "-").lowercase())
                .addPathSegment("$page.html")
                .build()
        } else {
            "$baseUrl$selected".toHttpUrl()
        }

        return GET(url, headers)
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        if (!genrelist.isNullOrEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreFilter(intl["genre_filter_title"], genrelist?.map { it.name to it.value }!!.toTypedArray()),
                ),
            )
        } else {
            filters.add(
                Filter.Header(intl["genre_missing_warning"]),
            )
        }
        return FilterList(filters)
    }

    override fun parseGenres(document: Document): List<GenreData> {
        return mutableListOf(GenreData("All", "")).apply {
            this += document.select(".textwidget.custom-html-widget a").map { element ->
                GenreData(element.text(), element.attr("href"))
            }
        }
    }

    private class GenreFilter(
        name: String,
        options: Array<Pair<String, String>>,
    ) : SelectFilter(
        name,
        options,
    )

    companion object {
        val SPACES_REGEX = "\\s+".toRegex()
    }
}
