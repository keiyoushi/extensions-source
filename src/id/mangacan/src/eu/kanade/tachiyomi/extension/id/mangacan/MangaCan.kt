package eu.kanade.tachiyomi.extension.id.mangacan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
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

    private var genreList: Array<Pair<String, String>> = emptyArray()

    override fun imageRequest(page: Page): Request {
        return super.imageRequest(page).newBuilder()
            .removeHeader("Referer")
            .addHeader("Referer", "$baseUrl/")
            .build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (genreList.isEmpty()) {
            genreList += "All" to ""
            genreList += parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        }

        return super.searchMangaParse(response)
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
        if (genreList.isNotEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Header(intl["genre_exclusion_warning"]),
                    GenreFilter(intl["genre_filter_title"], genreList),
                ),
            )
        } else {
            filters.add(
                Filter.Header(intl["genre_missing_warning"]),
            )
        }
        return FilterList(filters)
    }

    private fun parseGenres(document: Document): Array<Pair<String, String>> {
        return document.select(".textwidget.custom-html-widget a").map { element ->
            element.text() to element.attr("href")
        }.toTypedArray()
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
