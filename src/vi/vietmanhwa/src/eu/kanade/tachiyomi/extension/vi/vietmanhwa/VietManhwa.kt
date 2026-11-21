package eu.kanade.tachiyomi.extension.vi.vietmanhwa

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class VietManhwa : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getListPage(page, "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getListPage(page, "updatedAt")

    private suspend fun getListPage(page: Int, sort: String): MangasPage {
        val url = dataUrl("danh-sach", LIST_ROUTE)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", sort)
            .build()
        return client.get(url, Fetch.XHR).parseRouteData<ListDto>(LIST_ROUTE).toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
        val excluded = genres.filter { it.isExcluded() }.map { it.slug }
        val included = genres.filter { it.isIncluded() }.map { it.slug }
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty()

        val url = dataUrl("tim-kiem/nang-cao", SEARCH_ROUTE).apply {
            addQueryParameter("apply", "1")
            if (excluded.isNotEmpty()) addQueryParameter("excludeGenres", excluded.joinToString(","))
            if (included.isNotEmpty()) addQueryParameter("includeGenres", included.joinToString(","))
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.firstInstanceOrNull<SortFilter>()?.let { addQueryParameter("sort", it.value) }
            if (status.isNotEmpty()) addQueryParameter("status", status)
        }.build()
        return client.get(url, Fetch.XHR).parseRouteData<ListDto>(SEARCH_ROUTE).toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != SERIES_PATH) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty)
        if (url.host != baseUrl.toHttpUrl().host || slug == null) return super.getMangasByUrl(url, page)

        return when (val path = url.pathSegments[0]) {
            "author", "authors" -> {
                val route = "routes/authors.\$slug"
                client.get(dataUrl("authors/$slug", route).build(), Fetch.XHR)
                    .parseRouteData<AuthorDto>(route).toMangasPage()
            }
            "the-loai", "translators", "characters", "doujinshi" -> {
                val route = "routes/$path.\$slug"
                val listUrl = dataUrl("$path/$slug", route).addQueryParameter("page", page.toString()).build()
                client.get(listUrl, Fetch.XHR).parseRouteData<ListDto>(route).toMangasPage()
            }
            else -> super.getMangasByUrl(url, page)
        }
    }

    override fun getMangaUrl(manga: SManga): String = pageUrl("$SERIES_PATH/${manga.url}")

    // Details and chapters come from the same page data
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = dataUrl("$SERIES_PATH/${manga.url}", SERIES_ROUTE).build()
        val data = client.get(url, Fetch.XHR).parseRouteData<DetailsDto>(SERIES_ROUTE)
        return SMangaUpdate(data.toSManga(manga.url), data.toSChapterList(manga.url))
    }

    override fun getChapterUrl(chapter: SChapter): String = pageUrl("$SERIES_PATH/${chapter.url}")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = dataUrl("$SERIES_PATH/${chapter.url}", CHAPTER_ROUTE).build()
        return client.get(url, Fetch.XHR).parseRouteData<ReaderDto>(CHAPTER_ROUTE).toPageList()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(dataUrl("the-loai", GENRES_ROUTE).build(), Fetch.XHR).parseRouteData(GENRES_ROUTE)

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<GenresDto>()?.genres.orEmpty())

    // The data the site's own pages load, without the root loader (layout data, redirects to sorted query parameters)
    private fun dataUrl(path: String, route: String): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("$path.data")
        .addQueryParameter("_routes", route)

    private fun pageUrl(path: String): String = baseUrl.toHttpUrl().newBuilder().addPathSegments(path).build().toString()

    companion object {
        private const val SERIES_PATH = "manhwa-18"
        private const val LIST_ROUTE = "routes/danh-sach"
        private const val SEARCH_ROUTE = "routes/tim-kiem.nang-cao"
        private const val SERIES_ROUTE = "routes/manhwa-18.\$slug"
        private const val CHAPTER_ROUTE = "routes/manhwa-18.\$mangaSlug.\$chapterSlug"
        private const val GENRES_ROUTE = "routes/the-loai._index"
    }
}
