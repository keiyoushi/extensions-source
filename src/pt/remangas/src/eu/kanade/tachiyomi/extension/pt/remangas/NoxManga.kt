package eu.kanade.tachiyomi.extension.pt.remangas

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class NoxManga : KeiSource() {

    private val apiUrl get() = "$baseUrl/api/v1"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(SignatureInterceptor { baseUrl })
        rateLimit(3, 1.seconds) { it.encodedPath.startsWith("/api/") }
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Sec-Fetch-Site", "same-origin")

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/chapters/recent".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter("unique", "true")
            .addQueryParameter("sort", "new")
            .build()

        val result = client.get(url).parseAs<ChapterUpdateListDto>()
        return MangasPage(result.mangas, result.hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page, query, filters)

    private suspend fun getMangaList(page: Int, query: String = "", filters: FilterList = FilterList()): MangasPage {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("q", query)
                }
                filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            }
            .build()

        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.comics.map(MangaDto::toSManga), result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in MANGA_PATH_SEGMENTS) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.slug

        val details = async {
            if (fetchDetails) {
                client.get("$apiUrl/comics/slug/$slug").parseAs<MangaDto>().toSManga()
            } else {
                manga
            }
        }

        val chapterList = async {
            if (fetchChapters) {
                val url = "$apiUrl/comics/slug/$slug/chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("page", "1")
                    .addQueryParameter("per_page", CHAPTER_PAGE_SIZE.toString())
                    .addQueryParameter("sort", "newest")
                    .build()

                client.get(url).parseAs<ChapterListDto>().chapters.map { it.toSChapter(slug) }
            } else {
                chapters
            }
        }

        SMangaUpdate(manga = details.await(), chapters = chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/chapters/${chapter.url}?skip_view=true")
        .parseAs<ChapterPagesDto>()
        .toPageList()

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres")
        .parseAs<GenreListDto>()
        .data
        .filter { it.slug.isNotEmpty() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()
            ?: return FilterList(SortFilter(), TypeFilter(), StatusFilter(), DemographicFilter(), ContentFilter())

        return FilterList(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            DemographicFilter(),
            ContentFilter(),
            GenreFilter(genres),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaSlug = chapter.memo["mangaSlug"]?.stringOrNull
        val slug = chapter.memo["slug"]?.stringOrNull
        if (mangaSlug == null || slug == null) throw Exception("Atualize a lista de capítulos")

        return "$baseUrl/read/$mangaSlug/$slug"
    }

    private val SManga.slug get() = url.substringAfterLast('/')

    companion object {
        private const val PAGE_SIZE = 24
        private const val CHAPTER_PAGE_SIZE = 10000
        private val MANGA_PATH_SEGMENTS = listOf("manga", "read", "ler")
    }
}
