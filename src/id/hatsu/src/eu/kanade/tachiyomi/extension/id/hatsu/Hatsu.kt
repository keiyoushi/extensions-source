package eu.kanade.tachiyomi.extension.id.hatsu

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
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class Hatsu : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override suspend fun getPopularManga(page: Int): MangasPage = searchLibrary(page) {
        addQueryParameter("sortBy", "popular")
        addQueryParameter("sort", "desc")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchLibrary(page) {
        addQueryParameter("sortBy", "updated")
        addQueryParameter("sort", "desc")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // the project listing is a separate endpoint that takes none of the other filters
        if (filters.firstInstanceOrNull<ProjectFilter>()?.isProject == true) {
            val url = "$apiUrl/public/manga/project".toHttpUrl().newBuilder()
                .addQueryParameter("layout", "vertical")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", PAGE_SIZE.toString())
                .build()
            val data = client.get(url, Fetch.XHR).parseData<ProjectDto>()
            return MangasPage(data.project.map { it.toSManga() }, page * PAGE_SIZE < data.total)
        }

        return searchLibrary(page) {
            if (query.isNotBlank()) addQueryParameter("query", query.trim())
            filters.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }
        }
    }

    private suspend fun searchLibrary(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$apiUrl/public/library/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply(params)
            .build()
        val data = client.get(url, Fetch.XHR).parseData<SearchDto>()
        return MangasPage(data.mangas.map { it.toSManga() }, page * PAGE_SIZE < data.total)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val domain = baseUrl.toHttpUrl().topPrivateDomain()
        if (url.host != domain && !url.host.endsWith(".$domain")) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("manga")
        .addPathSegment(manga.url)
        .build()
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/public/manga".toHttpUrl().newBuilder()
            .addPathSegment(manga.url)
            .build()
        val detail = client.get(url, Fetch.XHR).parseData<MangaDetailDto>()
        return SMangaUpdate(
            manga = detail.toSManga(),
            chapters = detail.chapters.chapters.map { it.toSChapter(manga.url) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.substringAfter("/manga/").substringBeforeLast("/chapter-")
        val number = chapter.url.substringAfterLast("/chapter-")
        val url = "$apiUrl/public/chapter".toHttpUrl().newBuilder()
            .addPathSegment(number)
            .addQueryParameter("mangaSlug", slug)
            .build()
        return client.get(url, Fetch.XHR.from(getChapterUrl(chapter)))
            .parseData<ChapterPagesDto>()
            .toPageList()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/user/genres", Fetch.XHR)
        .parseData<GenreListDto>()
        .allGenres
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreDto>>())

    // failures come back as HTTP 200 with success = false
    private inline fun <reified T> Response.parseData(): T {
        val response = parseAs<ResponseDto<T>>()
        val data = response.data
        if (!response.success || data == null) {
            throw Exception(response.message?.takeIf { it.isNotBlank() } ?: "Request failed")
        }
        return data
    }

    companion object {
        private const val PAGE_SIZE = 24
    }
}
