package eu.kanade.tachiyomi.extension.id.soulscans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class SoulScans : KeiSource() {

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val result = client.get(url).parseAs<MangaListResponseDto>()

        val page = url.queryParameter("page")!!.toInt()
        return MangasPage(result.data.map { it.toSManga() }, page < result.totalPages)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(searchUrl(page, sort = "popular"))

    private var latestCache: List<SManga> = emptyList()

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1 || latestCache.isEmpty()) {
            val response = client.get("$baseUrl/api/comic/home-sections?sections=latest_comic_updates&updateLimit=240")
                .parseAs<HomeSectionsDto>()

            latestCache = response.latestComicUpdates.map { it.toSManga() }
        }

        val itemsPerPage = 24
        val startIndex = (page - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, latestCache.size)

        if (startIndex >= latestCache.size) {
            return MangasPage(emptyList(), false)
        }

        return MangasPage(
            latestCache.subList(startIndex, endIndex),
            endIndex < latestCache.size,
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(searchUrl(page, query, filters = filters))

    private fun searchUrl(page: Int, query: String = "", sort: String? = null, filters: FilterList? = null) = baseUrl.toHttpUrl()
        .newBuilder()
        .addPathSegments("api/search")
        .apply {
            addQueryParameter("type", "COMIC")
            addQueryParameter("limit", "20")
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("q", query)

            filters?.forEach { filter ->
                when (filter) {
                    is SelectFilter.Status -> filter.selected.takeIf(String::isNotEmpty)?.let { addQueryParameter("status", it) }
                    is SelectFilter.Genre -> filter.selected.takeIf(String::isNotEmpty)?.let { addQueryParameter("genre", it) }
                    is SelectFilter.Type -> filter.selected.takeIf(String::isNotEmpty)?.let { addQueryParameter("comic_type", it) }
                    is SelectFilter.Colored -> filter.selected.takeIf(String::isNotEmpty)?.let { addQueryParameter("color_format", it) }
                    is SelectFilter.Format -> filter.selected.takeIf(String::isNotEmpty)?.let { addQueryParameter("reading_format", it) }
                    is TextFilter.Author -> filter.state.takeIf(String::isNotBlank)?.let { addQueryParameter("author", it) }
                    is TextFilter.Artist -> filter.state.takeIf(String::isNotBlank)?.let { addQueryParameter("artist", it) }
                    is TextFilter.Publisher -> filter.state.takeIf(String::isNotBlank)?.let { addQueryParameter("publisher", it) }
                    is SelectFilter.Sort -> filter.selected.takeIf(String::isNotEmpty)?.let { addQueryParameter("sort", it) }
                    is SelectFilter.Order -> filter.selected.takeIf(String::isNotEmpty)?.let { addQueryParameter("order", it) }
                    else -> {}
                }
            }
            if (sort != null) setQueryParameter("sort", sort)
        }
        .build()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.lastOrNull { it.isNotBlank() } ?: return null
        return fetchSeriesDetail(slug).toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = fetchSeriesDetail(manga.url.substringAfterLast("/"))

        return SMangaUpdate(detail.toSManga(), detail.toSChapterList())
    }

    private suspend fun fetchSeriesDetail(slug: String) = client.get("$baseUrl/api/series/comic/$slug").parseAs<SeriesDetailDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = chapter.url.removePrefix("/comic/")
        val (seriesSlug, chapterSlug) = path.split("/chapter/")

        return client.get("$baseUrl/api/series/comic/$seriesSlug/chapter/$chapterSlug")
            .parseAs<ChapterPagesResponseDto>()
            .toPageList()
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/genres").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()?.map { it.toPair() }

        val filters = mutableListOf<Filter<*>>(
            SelectFilter.Status(
                listOf(
                    "All" to "",
                    "Ongoing" to "ONGOING",
                    "Completed" to "COMPLETED",
                    "Hiatus" to "HIATUS",
                ),
            ),

        )

        if (genres != null) {
            filters += SelectFilter.Genre(listOf("All" to "") + genres)
        }

        filters += listOf(

            SelectFilter.Type(
                listOf(
                    "All" to "",
                    "Manga" to "MANGA",
                    "Manhwa" to "MANHWA",
                    "Manhua" to "MANHUA",
                ),
            ),
            SelectFilter.Colored(
                listOf(
                    "All" to "",
                    "Full Color" to "FULL_COLOR",
                    "B&W" to "BW",
                ),
            ),
            SelectFilter.Format(
                listOf(
                    "All" to "",
                    "Vertical Scroll" to "VERTICAL_SCROLL",
                    "Page" to "PAGE",
                ),
            ),
            TextFilter.Author(),
            TextFilter.Artist(),
            TextFilter.Publisher(),
            SelectFilter.Sort(
                listOf(
                    "Latest Update" to "latest",
                    "Created Date" to "new",
                    "Top Views" to "views",
                    "Top Rate" to "rate",
                    "Top Bookmark" to "bookmark",
                    "Title A-Z" to "az",
                    "Title Z-A" to "za",

                ),
            ),
            SelectFilter.Order(
                listOf(
                    "DESC" to "desc",
                    "ASC" to "asc",
                ),
            ),
        )
        return FilterList(filters)
    }
}
