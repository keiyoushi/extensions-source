package eu.kanade.tachiyomi.extension.en.creepyscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class CreepyScans : Madara(
    "CreepyScans",
    "https://creepyscans.com",
    "en",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 3, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/$mangaSubString/?m_orderby=views",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/$mangaSubString/?m_orderby=latest",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) throw Exception("Search not available")

        val url = "$baseUrl/$mangaSubString/".toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("m_orderby", filter.toUriPart())
                    }
                }
                is GenreFilter -> {
                    val selected = filter.vals[filter.state].second
                    if (selected.isNotBlank()) {
                        url.removePathSegment(0)
                        url.addPathSegment("manga-genre")
                        url.addPathSegment(filter.vals[filter.state].second)
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String =
        super.searchMangaSelector() + ",div.page-content-listing div.page-item-detail"

    override fun searchMangaNextPageSelector(): String? = null

    // Filter

    override fun genresRequest(): Request {
        return GET("$baseUrl/$mangaSubString/?genres=", headers)
    }

    override fun parseGenres(document: Document): List<Genre> {
        genresList = document.select(".list-unstyled li").mapNotNull { genre ->
            genre.selectFirst("a[href]")?.let {
                val slug = it.attr("href")
                    .split("/")
                    .last(String::isNotEmpty)

                Pair(it.ownText().trim(), slug)
            }
        }

        return emptyList()
    }

    // From manga18fx
    private var genresList: List<Pair<String, String>> = emptyList()

    class GenreFilter(val vals: List<Pair<String, String>>) :
        Filter.Select<String>("Genre", vals.map { it.first }.toTypedArray())

    override fun getFilterList(): FilterList {
        val filters = buildList(4) {
            add(
                OrderByFilter(
                    title = orderByFilterTitle,
                    options = orderByFilterOptions.zip(orderByFilterOptionsValues),
                    state = 0,
                ),
            )
            add(Filter.Separator())
            add(Filter.Header("Filters are ignored for text search!"))

            if (genresList.isNotEmpty()) {
                add(GenreFilter(listOf(Pair("<select>", "")) + genresList))
            } else {
                add(Filter.Header("Wait for mangas to load then tap Reset"))
            }
        }

        return FilterList(filters)
    }

    // Page list

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url.substringBefore("?style=list"), headers)
        }
        return super.pageListRequest(chapter)
    }
}
