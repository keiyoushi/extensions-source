package eu.kanade.tachiyomi.extension.en.manhwahentaime

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class ManhwahentaiMe : Madara("Manhwahentai.me", "https://manhwahentai.me", "en") {

    override val useNewChapterEndpoint: Boolean = true

    override val mangaSubString = "webtoon"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        launchIO { countViews(document) }

        val comicObj = document.selectFirst("script:containsData(comicObj)")!!.data()
        val id = comicObj.filter { it.isDigit() }
        val name = comicObj.substringBefore(":").substringAfter("{").trim()
        val ajax_url = document.selectFirst("script:containsData(ajax)")!!.data().substringAfter('"').substringBefore('"')

        val body = FormBody.Builder()
            .add(name, id)
            .add("action", "ajax_chap")
            .build()
        val doc = client.newCall(POST(ajax_url, headers, body)).execute().asJsoup()
        val chapterElements = doc.select(chapterListSelector())

        return chapterElements.map(::chapterFromElement)
    }

    override fun popularMangaRequest(page: Int): Request =
        if (useLoadMoreRequest()) {
            loadMoreRequest(page, popular = true)
        } else {
            GET("$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=trending", headers)
        }

    override fun latestUpdatesRequest(page: Int): Request =
        if (useLoadMoreRequest()) {
            loadMoreRequest(page, popular = false)
        } else {
            GET("$baseUrl/home/${searchPage(page)}", headers)
        }

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/${searchPage(page)}".toHttpUrl().newBuilder()
        var alr = false
        url.addQueryParameter("s", query)
        url.addQueryParameter("post_type", "wp-manga")

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    if (filter.state.isNotBlank() && !alr) {
                        url.addPathSegments("manga-author/${filter.state.replace(" ", "-")}")
                        alr = true
                    }
                }
                is ArtistFilter -> {
                    if (filter.state.isNotBlank() && !alr) {
                        url.addQueryParameter("artist", filter.state.replace(" ", "-"))
                        alr = true
                    }
                }
                is YearFilter -> {
                    if (filter.state.isNotBlank() && !alr) {
                        url.addPathSegments("webtoon-release/${filter.state}")
                        alr = true
                    }
                }
                is OrderByFilter -> {
                    url.addQueryParameter("m_orderby", filter.toUriPart())
                }
                is GenreConditionFilter -> {
                    val name = filter.toUriPart()
                    if (name != "all") {
                        url.addPathSegments("webtoon-genre/$name")
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override val orderByFilterOptions: Map<String, String> = mapOf(
        intl["order_by_filter_az"] to "alphabet",
        intl["order_by_filter_rating"] to "rating",
        intl["order_by_filter_trending"] to "trending",
        intl["order_by_filter_views"] to "views",
        intl["order_by_filter_new"] to "new-manga",
    )

    private var genresList: List<Pair<String, String>> = emptyList()
    private var fetchGenresAttempts: Int = 0

    private fun fetchGenresMe() {
        if (fetchGenres && fetchGenresAttempts < 3 && genresList.isEmpty()) {
            try {
                genresList = client.newCall(genresRequest()).execute()
                    .use { parseGenresMe(it.asJsoup()) }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    private fun parseGenresMe(document: Document): List<Pair<String, String>> {
        return document.selectFirst("div.genres")
            ?.select("a")
            .orEmpty()
            .map { a ->
                a.ownText() to
                    a.attr("href").substringBeforeLast("/").substringAfterLast("/")
            }.let {
                listOf(("All" to "all")) + it
            }
    }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenresMe() }

        val filters = mutableListOf(
            Filter.Header("All filters except the orderby filter are incompatible with each other"),
            AuthorFilter(intl["author_filter_title"]),
            ArtistFilter(intl["artist_filter_title"]),
            YearFilter(intl["year_filter_title"]),
            OrderByFilter(
                title = intl["order_by_filter_title"],
                options = orderByFilterOptions.toList(),
            ),
        )

        if (genresList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_filter_header"]),
                GenreConditionFilter(
                    title = intl["genre_filter_title"],
                    options = genresList,
                ),
            )
        } else if (fetchGenres) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header(intl["genre_missing_warning"]),
            )
        }

        return FilterList(filters)
    }

    override fun searchMangaSelector() = "div.page-item-detail"
}
