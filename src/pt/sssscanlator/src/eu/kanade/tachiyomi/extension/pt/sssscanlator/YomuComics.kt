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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class YomuComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(5) { it.host == baseUrl.toHttpUrl().host }

    private val rscHeaders: Headers get() = headersBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, sort = "newest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page, query, filters)

    private suspend fun getMangaList(
        page: Int,
        query: String = "",
        filters: FilterList = FilterList(),
        sort: String? = null,
    ): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
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

        val series = client.get(url, rscHeaders).extractNextJs<JsonArray>(::isSeriesList)
            ?: throw Exception("Não foi possível ler a lista de obras")

        val mangas = series.filter(::isSeriesEntry).map { it.parseAs<LibraryMangaDto>().toSManga() }

        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
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
        val body = client.get(baseUrl + manga.url, rscHeaders).use { it.body.string() }
        val series = body.parseSeriesPage()

        return SMangaUpdate(manga = series.manga, chapters = series.chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val payload = client.get(getChapterUrl(chapter), rscHeaders)
            .extractNextJs<ChapterPayloadDto>()
            ?: throw Exception("Não foi possível ler as páginas do capítulo")

        return payload.pages
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/genres")
        .parseAs<List<String>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>()
            ?: return FilterList(SortFilter(), TypeFilter(), StatusFilter())

        return FilterList(SortFilter(), TypeFilter(), StatusFilter(), GenreFilter(genres))
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]?.stringOrNull
        val number = chapter.memo["number"]?.stringOrNull
        if (slug == null || number == null) throw Exception("Atualize a lista de capítulos")

        return "$baseUrl/ler/$slug/$number"
    }

    companion object {
        private const val PAGE_SIZE = 30
        private val MANGA_PATH_SEGMENTS = listOf("obra", "ler")
    }
}
