package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class YomuComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(5) { it.host == API_URL.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, sort = "newest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page, query, filters)

    private suspend fun getMangaList(
        page: Int,
        query: String = "",
        filters: FilterList = FilterList(),
        sort: String? = null,
    ): MangasPage {
        val url = "$API_URL/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                sort?.let { addQueryParameter("sort", it) }
                if (query.isNotBlank()) {
                    addQueryParameter("q", query)
                }
                filters.filterIsInstance<UrlFilter>()
                    .filterNot { sort != null && it is SortFilter }
                    .forEach { it.addToUrl(this) }
            }
            .build()

        val result = client.get(url).parseAs<ListDto>()

        return MangasPage(result.series.map(SeriesDto::toSManga), result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in MANGA_PATH_SEGMENTS) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/obra/$slug" }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast('/')
        val details = client.get("$API_URL/manga/$slug").parseAs<MangaDto>()

        return SMangaUpdate(manga = details.toSManga(), chapters = details.chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, number) = chapter.location()

        return client.get("$API_URL/chapter/$slug/$number").parseAs<PagesDto>().toPageList()
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$API_URL/genres")
        .parseAs<List<String>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>()
            ?: return FilterList(SortFilter(), TypeFilter(), StatusFilter())

        return FilterList(SortFilter(), TypeFilter(), StatusFilter(), GenreFilter(genres))
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, number) = chapter.location()

        return "$baseUrl/ler/$slug/$number"
    }

    private fun SChapter.location(): Pair<String, String> {
        val slug = memo["slug"]?.stringOrNull
        val number = memo["number"]?.stringOrNull
        if (slug == null || number == null) throw Exception("Atualize a lista de capítulos")

        return slug to number
    }

    companion object {
        private const val API_URL = "https://yomu.tauruus.com"
        private val MANGA_PATH_SEGMENTS = listOf("obra", "ler")
    }
}
