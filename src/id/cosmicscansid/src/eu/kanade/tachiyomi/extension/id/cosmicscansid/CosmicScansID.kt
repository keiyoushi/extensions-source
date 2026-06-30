package eu.kanade.tachiyomi.extension.id.cosmicscansid

import eu.kanade.tachiyomi.network.GET
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
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CosmicScansID : HttpSource() {

    override val supportsLatest = true

    private val apiUrl = "https://cdncid.csmcscns.id/v1/manga"

    private val cursorCache = mutableMapOf<String, String>()

    private val lastPage = mutableMapOf<String, Int>()

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("Referer", "$baseUrl/")

    // URL compat: handle old "/manga/slug" and new "/series/slug"
    private fun SManga.slug(): String = url
        .removePrefix("/manga/")
        .removePrefix("/series/")
        .trimEnd('/')

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val key = "popular"
        lastPage[key] = page
        val url = "$apiUrl/filter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("order_by", "popular")
            .addCursor(key, page)
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response, "popular")

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val key = "update"
        lastPage[key] = page
        val url = "$apiUrl/filter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("order_by", "update")
            .addCursor(key, page)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response, "update")

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.isBlank() && !filters.hasActiveFilters() && page > 1) {
            return Observable.just(MangasPage(emptyList(), false))
        }
        return super.fetchSearchManga(page, query, filters)
            .map { mangasPage ->
                val clientFilters = filters.buildClientFilters()
                if (clientFilters.isEmpty()) {
                    mangasPage
                } else {
                    val filtered = mangasPage.mangas.filter { manga ->
                        clientFilters.all { predicate -> predicate(manga) }
                    }
                    MangasPage(filtered, mangasPage.hasNextPage)
                }
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val endpoint = when {
            query.isNotBlank() -> "search"
            filters.firstInstanceOrNull<ProjectFilter>()?.state == 1 -> "latestProject"
            else -> "filter"
        }
        val key = searchKey(query, filters, endpoint)
        lastPage[key] = page
        val url = "$apiUrl/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query)
                if (endpoint == "filter") addOrderFilter(filters)
                if (endpoint != "search") addCursor(key, page)
            }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.queryParameter("q").orEmpty()
        val path = response.request.url.encodedPath.substringAfterLast('/')
        val key = when (path) {
            "filter", "latestProject" -> searchKeyFromUrl(response, path)
            else -> searchKey(query, FilterList(), "search")
        }
        return parseMangaPage(response, key)
    }

    // Details
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.slug()}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.slug()
        return GET("$apiUrl/mangaDetail/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDetailResponse>().data.toSMangaDetails()

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<MangaDetailResponse>().data.chapters.orEmpty()
        .filter { it.slug?.isNotBlank() == true && it.redirectLink.isNullOrBlank() }
        .map { it.toSChapter() }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url.substringAfterLast('/')}"

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.substringAfterLast('/')
        return GET("$apiUrl/readingPage/$slug", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ReadingPageResponse>().data
        if (!data.redirectLink.isNullOrBlank()) return emptyList()
        return data.toPageList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList() = getCosmicScansIDFilterList()

    // Utilities
    private fun parseMangaPage(response: Response, key: String): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val page = lastPage[key] ?: 1
        if (!result.cursor?.nextCursor.isNullOrBlank()) {
            cursorCache["$key:${page + 1}"] = result.cursor.nextCursor.orEmpty()
        }
        return MangasPage(result.data.map { it.toSManga() }, result.cursor?.hasNext == true)
    }

    private fun Builder.addCursor(key: String, page: Int): Builder = apply {
        if (page > 1) {
            cursorCache["$key:$page"]?.takeIf { it.isNotBlank() }
                ?.let { addQueryParameter("after", it) }
        }
    }

    private fun Builder.addOrderFilter(filters: FilterList): Builder = apply {
        filters.firstInstanceOrNull<OrderFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("order_by", it) }
    }

    private fun FilterList.buildClientFilters(): List<(SManga) -> Boolean> {
        val predicates = mutableListOf<(SManga) -> Boolean>()

        firstInstanceOrNull<StatusFilter>()?.value?.takeIf { it.isNotBlank() }?.let { status ->
            predicates += { manga ->
                manga.status == when (status.lowercase(Locale.ROOT)) {
                    "ongoing" -> SManga.ONGOING
                    "completed", "complete" -> SManga.COMPLETED
                    "hiatus" -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
            }
        }

        firstInstanceOrNull<TypeFilter>()?.value?.takeIf { it.isNotBlank() }?.let { type ->
            predicates += { manga ->
                manga.description?.contains("Type: $type", ignoreCase = true) == true
            }
        }

        val selectedGenres = firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.map { it.genre }
            ?: emptyList()
        if (selectedGenres.isNotEmpty()) {
            predicates += { manga ->
                val mangaGenres = manga.genre.orEmpty()
                selectedGenres.all { selected ->
                    mangaGenres.contains(selected, ignoreCase = true)
                }
            }
        }

        return predicates
    }

    private fun FilterList.hasActiveFilters(): Boolean = firstInstanceOrNull<OrderFilter>()?.state != 0 ||
        firstInstanceOrNull<StatusFilter>()?.state != 0 ||
        firstInstanceOrNull<TypeFilter>()?.state != 0 ||
        firstInstanceOrNull<ProjectFilter>()?.state == 1 ||
        firstInstanceOrNull<GenreFilter>()?.state.orEmpty().any { it.state }

    private fun searchKey(query: String, filters: FilterList, endpoint: String): String = listOf(
        endpoint,
        query,
        filters.firstInstanceOrNull<OrderFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<TypeFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<ProjectFilter>()?.state?.toString().orEmpty(),
        filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter { it.state }.joinToString(",") { it.genre },
    ).joinToString(":")

    private fun searchKeyFromUrl(response: Response, endpoint: String): String {
        val url = response.request.url
        return listOf(
            endpoint,
            "",
            url.queryParameter("order_by").orEmpty(),
            "",
            "",
            if (endpoint == "latestProject") "1" else "0",
            "",
        ).joinToString(":")
    }

    companion object {
        private const val PAGE_SIZE = 24
    }
}
