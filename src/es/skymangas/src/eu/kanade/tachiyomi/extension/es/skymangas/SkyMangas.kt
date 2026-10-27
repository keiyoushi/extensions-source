package eu.kanade.tachiyomi.extension.es.skymangas

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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

@Source
abstract class SkyMangas : KeiSource() {

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    // API allows 100 requests per minute
    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::reissuePageToken)
        .rateLimit(permits = 90, period = 1.minutes) {
            it.host == API_ROOT.host && it.encodedPath.startsWith("/api/v1/") && !it.encodedPath.startsWith("/api/v1/images/")
        }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(), AvailabilityFilter()))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.UPDATED), AvailabilityFilter()))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrl("manhuas", "search").apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<UrlFilter>().forEach { it.addTo(this) }
        }
        return fetchMangaList(url, page)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments
        val name = segments.getOrNull(1).orEmpty()
        val listUrl = when {
            url.host != baseUrl.toHttpUrl().host || name.isEmpty() -> null
            segments[0] == "autores" -> apiUrl("manhuas", "author", name).addQueryParameter("role", "author")
            segments[0] == "artistas" -> apiUrl("manhuas", "author", name).addQueryParameter("role", "artist")
            segments[0] == "generos" || segments[0] == "genero" -> apiUrl("manhuas", "genre", name)
            segments[0] == "tags" -> apiUrl("manhuas", "tag", name)
            else -> null
        }
        return listUrl?.let { fetchMangaList(it, page) } ?: super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] !in MANGA_PATHS || segments[1].isEmpty()) return null
        return fetchDetails(segments[1]).toSManga().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manhua/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val storedId = manga.memo[MEMO_ID]?.stringOrNull
        val details = async { if (fetchDetails || storedId == null) fetchDetails(manga.url) else null }
        val chapterList = async {
            if (fetchChapters) fetchChapters(storedId ?: details.await()!!.id, manga.url) else chapters
        }
        SMangaUpdate(details.await()?.toSManga() ?: manga, chapterList.await())
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = apiUrl("manhuas", manga.url, "similar").addQueryParameter("limit", "8").build()
        return client.get(url).parseAs<ApiResponse<List<MangaItem>>>().data.map { it.toSManga() }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/leer/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, number) = chapter.url.split('/', limit = 2)
        return client.get(apiUrl("chapters", slug, number).build())
            .parseAs<ApiResponse<ChapterPages>>().data.pages
            .mapIndexed { index, page -> Page(index, imageUrl = imageUrl(page.imageUrl)) }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get(apiUrl("manhuas", "filters").build())
        .parseAs<ApiResponse<FilterData>>().data
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = data?.parseAs<FilterData>()
        return FilterList(
            buildList {
                add(SortFilter())
                add(AvailabilityFilter())
                if (filters != null) {
                    add(SelectFilter("Estado", "statusId", filters.statuses))
                    add(SelectFilter("Origen", "originationId", filters.originations))
                    add(SelectFilter("Demografía", "demographicId", filters.demographics))
                    add(SelectFilter("Traducción", "translationStatusId", filters.translationStatuses))
                    add(CheckBoxGroup("Géneros", "genres", filters.genres))
                    add(CheckBoxGroup("Etiquetas", "tags", filters.tags))
                }
                add(NumberFilter("Año desde", "yearFrom"))
                add(NumberFilter("Año hasta", "yearTo"))
                add(NumberFilter("Mínimo de capítulos", "chapterCountMin"))
            },
        )
    }

    private suspend fun fetchMangaList(url: HttpUrl.Builder, page: Int): MangasPage {
        url.addQueryParameter("page", page.toString())
        url.addQueryParameter("perPage", "24")
        val response = client.get(url.build()).parseAs<PagedResponse<MangaItem>>()
        return MangasPage(response.data.map { it.toSManga() }, response.meta.page < response.meta.totalPages)
    }

    private suspend fun fetchDetails(slug: String): MangaDetails = client.get(apiUrl("manhuas", slug).build())
        .parseAs<ApiResponse<MangaDetails>>().data

    private suspend fun fetchChapters(id: String, slug: String): List<SChapter> = coroutineScope {
        val first = fetchChapterPage(id, 1)
        val rest = (2..first.meta.totalPages).map { page -> async { fetchChapterPage(id, page) } }.awaitAll()
        (listOf(first) + rest).flatMap { it.data }.map { it.toSChapter(slug) }
    }

    private suspend fun fetchChapterPage(id: String, page: Int): PagedResponse<ChapterItem> {
        val url = apiUrl("chapters", "manhua", id)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "100")
            .addQueryParameter("sort", "desc")
            .build()
        return client.get(url).parseAs()
    }

    // The apps keep cached page urls, so a refused token is reissued from its release
    private fun reissuePageToken(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url
        if (response.code != 403 || url.host != API_ROOT.host || url.encodedPath != PAGE_IMAGE_PATH) return response
        val release = url.queryParameter("release") ?: return response
        val page = url.queryParameter("page")?.toIntOrNull() ?: return response
        response.close()

        val pages = releasePages(chain, release)
        val fresh = pages.firstOrNull { it.pageNumber == page } ?: throw IOException("Page $page not in release $release")
        val retry = chain.proceed(request.newBuilder().url(imageUrl(fresh.imageUrl)).build())
        if (retry.code == 403) synchronized(reissueLock) { if (lastReissue?.pages === pages) lastReissue = null }
        return retry
    }

    private fun releasePages(chain: Interceptor.Chain, release: String): List<PageImage> = synchronized(reissueLock) {
        lastReissue?.takeIf { it.release == release && System.currentTimeMillis() - it.time < REISSUE_REUSE.inWholeMilliseconds }?.let {
            return it.pages
        }
        val request = Request.Builder()
            .url(apiUrl("releases", release, "pages").build())
            .headers(headers)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        val pages = try {
            response.parseAs<ApiResponse<List<PageImage>>>().data
        } catch (e: SerializationException) {
            throw IOException("Unexpected page list", e)
        }
        lastReissue = Reissue(release, pages, System.currentTimeMillis())
        pages
    }

    private class Reissue(val release: String, val pages: List<PageImage>, val time: Long)

    private val reissueLock = Any()

    // One chapter's fresh page urls, so its other refused pages skip the refetch
    private var lastReissue: Reissue? = null

    private fun apiUrl(vararg segments: String): HttpUrl.Builder = API_ROOT.newBuilder().addPathSegments("api/v1").apply {
        segments.forEach { addPathSegment(it) }
    }

    companion object {
        private val MANGA_PATHS = setOf("manhua", "leer")
        private const val PAGE_IMAGE_PATH = "/api/v1/images/release-page"
        private val REISSUE_REUSE = 10.minutes
    }
}
