package eu.kanade.tachiyomi.extension.en.multporn

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Multporn : KeiSource() {

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", HEADER_USER_AGENT)
        set("Content-Type", HEADER_CONTENT_TYPE)
    }

    // Popular

    private suspend fun fetchPopularManga(page: Int, filters: FilterList): MangasPage {
        val url = "$baseUrl/best".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        filters.forEach {
            when (it) {
                is SortBySelectFilter -> url.addQueryParameter("sort_by", it.selected.uri)
                is SortOrderSelectFilter -> url.addQueryParameter("sort_order", it.selected.uri)
                is PopularTypeSelectFilter -> url.addQueryParameter("type", it.selected.uri)
                else -> { }
            }
        }

        return fetchMangaList(url.build())
    }

    override suspend fun getPopularManga(page: Int) = fetchPopularManga(page - 1, getMultpornFilterList(POPULAR_DEFAULT_SORT_BY_FILTER_STATE))

    // Latest

    private suspend fun fetchLatestManga(page: Int, filters: FilterList): MangasPage {
        val url = "$baseUrl/new".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        filters.forEach {
            when (it) {
                is SortBySelectFilter -> url.addQueryParameter("sort_by", it.selected.uri)
                is SortOrderSelectFilter -> url.addQueryParameter("sort_order", it.selected.uri)
                is LatestTypeSelectFilter -> url.addQueryParameter("type", it.selected.uri)
                else -> { }
            }
        }

        return fetchMangaList(url.build())
    }

    override suspend fun getLatestUpdates(page: Int) = fetchLatestManga(page - 1, getMultpornFilterList(LATEST_DEFAULT_SORT_BY_FILTER_STATE))

    // Search

    private suspend fun fetchSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("search_api_views_fulltext", query)

        filters.forEach {
            when (it) {
                is SortBySelectFilter -> url.addQueryParameter("sort_by", it.selected.uri)
                is SearchTypeSelectFilter -> url.addQueryParameter("type", it.selected.uri)
                else -> { }
            }
        }

        return fetchMangaList(url.build())
    }

    private suspend fun fetchTextSearchFilters(page: Int, filters: List<TextSearchFilter>): MangasPage {
        val pages = coroutineScope {
            filters.flatMap {
                it.stateURIs.map { queryURI ->
                    async {
                        val response = client.get("$baseUrl/${it.uri}/$queryURI?page=0,$page", ensureSuccess = false)
                        if (response.code != 200) {
                            response.close()
                            return@async null
                        }

                        val document = response.asJsoup()
                        val mangas = document.select("#content .col-1:contains(Views:),.col-2:contains(Views:)")
                            .map { element -> popularMangaFromElement(element) }
                        val hasNextPage = document.select(popularMangaNextPageSelector).firstOrNull() != null

                        MangasPage(mangas, hasNextPage)
                    }
                }
            }.awaitAll().filterNotNull()
        }

        return MangasPage(
            pages.flatMap { it.mangas }.distinctBy { it.url },
            pages.any { it.hasNextPage },
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortByFilterType = filters.firstInstanceOrNull<SortBySelectFilter>()?.requestType ?: POPULAR_REQUEST_TYPE
        val textSearchFilters = filters.filterIsInstance<TextSearchFilter>().filter { it.state.isNotBlank() }

        return when {
            textSearchFilters.isNotEmpty() -> fetchTextSearchFilters(page - 1, textSearchFilters)
            query.isNotEmpty() || sortByFilterType == SEARCH_REQUEST_TYPE -> fetchSearchManga(page - 1, query, filters)
            sortByFilterType == LATEST_REQUEST_TYPE -> fetchLatestManga(page - 1, filters)
            else -> fetchPopularManga(page - 1, filters)
        }
    }

    private suspend fun fetchMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select(popularMangaSelector).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector).firstOrNull() != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details

    private fun parseUnlabelledAuthorNames(document: Document): List<String> = listOf(
        "field-name-field-author",
        "field-name-field-authors-gr",
        "field-name-field-img-group",
        "field-name-field-hentai-img-group",
        "field-name-field-rule-63-section",
    ).flatMap { document.select(".$it a").map { a -> a.text() } }

    private fun mangaDetailsParse(manga: SManga, document: Document): SManga = manga.apply {
        title = document.select("h1#page-title").text()

        val infoMap = listOf(
            "Section",
            "Characters",
            "Tags",
            "Author",
        ).associateWith {
            document.select(".field:has(.field-label:contains($it:)) .links a").map { t -> t.text() }
        }

        artist = (infoMap.getValue("Author") + parseUnlabelledAuthorNames(document))
            .distinct().joinToString()
        author = artist

        genre = listOf("Tags", "Section", "Characters")
            .flatMap { infoMap.getValue(it) }.distinct().joinToString()

        status = infoMap["Section"]?.firstOrNull { it == "Ongoings" }?.let { SManga.ONGOING } ?: SManga.COMPLETED

        val pageCount = document.select(".jb-image img").size

        description = infoMap
            .filter { it.key in arrayOf("Section", "Characters") }
            .filter { it.value.isNotEmpty() }
            .map { "${it.key}:\n${it.value.joinToString()}" }
            .let {
                it + listOf(
                    "Pages:\n$pageCount",
                )
            }
            .joinToString("\n\n")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) {
            mangaDetailsParse(manga, client.get(getMangaUrl(manga)).asJsoup())
        } else {
            manga
        }

        val updatedChapters = listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                chapter_number = 1f
            },
        )

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".jb-image img").mapIndexed { i, image ->
            Page(i, imageUrl = image.absUrl("src").replace("/styles/juicebox_2k/public", "").substringBefore("?"))
        }
    }

    // Selectors

    private val popularMangaSelector = ".masonry-item"
    private val popularMangaNextPageSelector = ".pager-next a"

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select(".views-field-title a").attr("abs:href"))
        title = element.select(".views-field-title").text()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    // Filters

    override fun getFilterList(data: JsonElement?) = getMultpornFilterList(POPULAR_DEFAULT_SORT_BY_FILTER_STATE)

    companion object {
        private const val HEADER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"
        private const val HEADER_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8"
    }
}
