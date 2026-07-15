package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class OneReader : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    private val apiBaseUrl = "https://api.onereader.net".toHttpUrl()

    override fun popularMangaRequest(page: Int): Request = searchRequest(
        page = page,
        query = "",
        order = "az",
        genre = "",
        type = "",
        status = "",
    )

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchRequest(
        page = page,
        query = "",
        order = "recent",
        genre = "",
        type = "",
        status = "",
    )

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val order = filters.firstInstanceOrNull<OrderFilter>()?.selected() ?: "az"
        val genre = filters.firstInstanceOrNull<TagFilter>()?.selected().orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected().orEmpty()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected().orEmpty()

        return searchRequest(page, query, order, genre, type, status)
    }

    private fun searchRequest(
        page: Int,
        query: String,
        order: String,
        genre: String,
        type: String,
        status: String,
    ): Request {
        val url = apiUrl("api", "mangas", "search").newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("order", order)
            .addQueryParameter("role", "standard")
            .addQueryParameter("search", query.trim())
            .addQueryParameter("genre", genre)
            .addQueryParameter("type", type)
            .addQueryParameter("status", status)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = response.parseAs<SearchDto>().toMangasPage()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga-details".toHttpUrl().newBuilder()
        .addQueryParameter("id", manga.url)
        .build()
        .toString()

    override fun mangaDetailsRequest(manga: SManga): Request = GET(apiUrl("api", "mangas", manga.url), headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga(details = true)

    override fun chapterListRequest(manga: SManga): Request = GET(apiUrl("api", "chapters", manga.url), headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<Map<String, ChapterDto>>()
        .entries
        .sortedByDescending { it.key.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
        .map { it.value.toSChapter(it.key, baseUrl) }

    override fun getChapterUrl(chapter: SChapter): String = requireNotNull(
        baseUrl.toHttpUrl().resolve(chapter.url),
    ).toString()

    override fun pageListRequest(chapter: SChapter): Request {
        val webUrl = getChapterUrl(chapter).toHttpUrl()
        val mangaKey = requireNotNull(webUrl.queryParameter("id"))
        val chapterNumber = requireNotNull(webUrl.queryParameter("capitulo")).replace('.', '_')
        return GET(apiUrl("api", "chapters", mangaKey, chapterNumber), headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PagesDto>().toPages(apiBaseUrl)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        OrderFilter(),
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        TagFilter(),
    )

    private fun apiUrl(vararg pathSegments: String): HttpUrl = apiBaseUrl.newBuilder()
        .apply { pathSegments.forEach { addPathSegment(it) } }
        .build()

    companion object {
        private const val PAGE_SIZE = 24
    }
}
