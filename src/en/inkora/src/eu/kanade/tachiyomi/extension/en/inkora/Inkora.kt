package eu.kanade.tachiyomi.extension.en.inkora

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

@Source
abstract class Inkora : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$API_URL/latest?page=$page", Fetch.XHR).parseAs<MangaListDto>().toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$API_URL/advanced-search".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "24")
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.firstInstanceOrNull<TypeFilter>()?.checked?.let { addQueryParameter("types", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.checked?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<DemographicFilter>()?.checked?.let { addQueryParameter("demographics", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.checked?.let {
                addQueryParameter("genres", it)
                addQueryParameter("genres_mode", "and")
            }
            addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.selected ?: "follows")
            addQueryParameter("order", "desc")
        }.build()
        return client.get(url, Fetch.XHR).parseAs<MangaListDto>().toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() }
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] !in LINK_PATHS || id == null) return null
        return getMangaUpdate(SManga.create().apply { this.url = id }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/title/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = API_URL.toHttpUrl().newBuilder().addPathSegment("info").addPathSegment(manga.url).build()
        val info = client.get(url, Fetch.XHR).parseAs<InfoDto>()
        return SMangaUpdate(info.toSManga(), info.toSChapters())
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("manhwa")
        .addPathSegment(chapter.url.substringBefore('/'))
        .addPathSegment("read")
        .addPathSegment(chapter.url.substringAfter('/'))
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = API_URL.toHttpUrl().newBuilder().addPathSegment("read").addPathSegment(chapter.url.substringAfter('/')).build()
        return client.get(url, Fetch.XHR).parseAs<PageListDto>().toPages()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$API_URL/genres", Fetch.XHR).parseAs<GenreListDto>().genres.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            DemographicFilter(),
            data?.let { GenreFilter(it.parseAs()) },
        ),
    )

    companion object {
        private const val API_URL = "https://api.spacely.tech/manhwa/index"
        private val LINK_PATHS = setOf("title", "manhwa")
    }
}
