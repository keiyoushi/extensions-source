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

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(searchUrl(page, sort = "latest"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(searchUrl(page, query, filters = filters))

    private fun searchUrl(page: Int, query: String = "", sort: String? = null, filters: FilterList? = null) = baseUrl.toHttpUrl()
        .newBuilder()
        .addPathSegments("api/search")
        .apply {
            addQueryParameter("type", "COMIC")
            addQueryParameter("limit", "20")
            addQueryParameter("page", page.toString())
            addQueryParameter("q", query)

            filters?.forEach { filter ->
                when (filter) {
                    is SelectFilter.Status -> addQueryParameter("status", filter.selected)
                    is SelectFilter.Genre -> addQueryParameter("genre", filter.selected)
                    is SelectFilter.Type -> addQueryParameter("comic_type", filter.selected)
                    is SelectFilter.Colored -> addQueryParameter("color_format", filter.selected)
                    is SelectFilter.Format -> addQueryParameter("reading_format", filter.selected)
                    is TextFilter.Author -> addQueryParameter("author", filter.state)
                    is TextFilter.Artist -> addQueryParameter("artist", filter.state)
                    is TextFilter.Publisher -> addQueryParameter("publisher", filter.state)
                    is SelectFilter.Sort -> addQueryParameter("sort", filter.selected)
                    is SelectFilter.Order -> addQueryParameter("order", filter.selected)
                    else -> {
                    }
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
            filters += SelectFilter.Genre(genres)
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
