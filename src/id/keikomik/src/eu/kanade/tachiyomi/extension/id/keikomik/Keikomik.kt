package eu.kanade.tachiyomi.extension.id.keikomik

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Keikomik : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = fetchList(page, "views", null)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchList(page, "UpdateAt", null)

    // The site's search has no filters and no pages
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = apiUrl("search").addQueryParameter("q", query.trim()).build()
            return MangasPage(client.get(url, Fetch.XHR).parseAs<List<ComicDto>>().map { it.toSManga() }, false)
        }
        val sort = filters.firstInstanceOrNull<SortFilter>()?.field ?: "views"
        return fetchList(page, sort, filters.firstInstanceOrNull<GenreFilter>()?.value)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val slug = when (url.pathSegments[0]) {
            "komik" -> id
            "chapter" -> id.substringBeforeLast("-chapter-")
            else -> return null
        }
        val manga = SManga.create().apply { this.url = "/komik/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = client.get(getMangaUrl(manga), Fetch.DOCUMENT).extractNextJs<SeriesPageDto>()!!
        return SMangaUpdate(series.toSManga(), series.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT)
        .extractNextJs<ChapterPageDto>()!!.subItem.img
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private suspend fun fetchList(page: Int, sort: String, genre: String?): MangasPage {
        val url = apiUrl("list")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "18")
            .addQueryParameter("sort", sort)
            .addQueryParameter("direction", "desc")
            .apply { genre?.let { addQueryParameter("genre", it) } }
            .build()
        val list = client.get(url, Fetch.XHR).parseAs<ListDto>()
        return MangasPage(list.data.map { it.toSManga() }, list.hasNextPage)
    }

    private fun apiUrl(endpoint: String) = baseUrl.toHttpUrl().newBuilder().addPathSegment("api").addPathSegment(endpoint)
}
