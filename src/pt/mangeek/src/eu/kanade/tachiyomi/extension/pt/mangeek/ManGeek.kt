package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

@Source
abstract class ManGeek : HttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    private val apiUrl = "http://geekstations.com.br/api/v2/pt".toHttpUrl()

    private val discoverLock = Any()
    private var activeDiscoverTags: List<String>? = null
    private val discoveredIds = mutableListOf<Long>()

    override fun popularMangaRequest(page: Int): Request = GET(signedUrl("home"), headers)
        .newBuilder()
        .tag(PageTag::class.java, PageTag(page.coerceAtLeast(1)))
        .build()

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<HomeDto>().catalogMangas()
        val page = requireNotNull(response.request.tag(PageTag::class.java)).page
        val start = (page - 1) * CATALOG_PAGE_SIZE
        val pageMangas = mangas.drop(start).take(CATALOG_PAGE_SIZE)

        return MangasPage(
            pageMangas.map { it.toSManga() },
            start + pageMangas.size < mangas.size,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(signedUrl("home"), headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAs<HomeDto>().news
            .map { it.manga }
            .distinctBy { it.id }
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val requestedPage = page.coerceAtLeast(1)
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val includedTags = filters.includedTags()
        var ignoredIds = emptyList<Long>()

        val (mode, request) = when {
            normalizedQuery.isBlank() && includedTags.isEmpty() ->
                SEARCH_MODE_HOME to
                    GET(signedUrl("home"), headers)
            normalizedQuery.isBlank() -> {
                ignoredIds = synchronized(discoverLock) {
                    if (requestedPage == 1 || activeDiscoverTags != includedTags) {
                        activeDiscoverTags = includedTags
                        discoveredIds.clear()
                    }

                    discoveredIds.take((requestedPage - 1) * DISCOVER_PAGE_SIZE)
                }

                SEARCH_MODE_DISCOVER to
                    post(
                        signedUrl("discover"),
                        DiscoverBody(includedTags, ignoredIds).toJsonRequestBody(),
                    )
            }
            else ->
                SEARCH_MODE_QUERY to
                    post(
                        signedUrl("search", normalizedQuery),
                        SearchBody(includedTags).toJsonRequestBody(),
                    )
        }

        val tag = SearchTag(
            mode = mode,
            page = requestedPage,
            includedTags = includedTags,
            ignoredIds = ignoredIds,
        )

        return request.newBuilder()
            .tag(SearchTag::class.java, tag)
            .build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val tag = requireNotNull(response.request.tag(SearchTag::class.java))

        val responseMangas = if (tag.mode == SEARCH_MODE_HOME) {
            response.parseAs<HomeDto>().catalogMangas()
        } else {
            response.parseAs<List<MangaDto>>()
        }

        val mangas = if (tag.mode == SEARCH_MODE_DISCOVER) {
            val ignoredIds = tag.ignoredIds.toHashSet()
            responseMangas
                .filterNot { it.id in ignoredIds }
                .distinctBy { it.id }
                .also { pageMangas ->
                    synchronized(discoverLock) {
                        if (activeDiscoverTags == tag.includedTags) {
                            discoveredIds.clear()
                            discoveredIds.addAll(tag.ignoredIds)
                            discoveredIds.addAll(pageMangas.map { it.id })
                        }
                    }
                }
        } else {
            responseMangas
        }

        if (tag.mode != SEARCH_MODE_QUERY) {
            return MangasPage(
                mangas.map { it.toSManga() },
                tag.mode == SEARCH_MODE_DISCOVER && responseMangas.size == DISCOVER_PAGE_SIZE,
            )
        }

        val start = (tag.page - 1) * SEARCH_PAGE_SIZE
        val pageMangas = mangas.drop(start).take(SEARCH_PAGE_SIZE)

        return MangasPage(
            pageMangas.map { it.toSManga() },
            start + pageMangas.size < mangas.size,
        )
    }

    override fun getFilterList(): FilterList = getFilters()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(
        signedUrl("manga", manga.url),
        headers,
    )

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga(details = true)

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<MangaDto>().chapters
        .orEmpty()
        .asReversed()
        .map { it.toSChapter() }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val chapterId = chapter.url
        val primary = runCatching { fetchChapterPages("chapter", chapterId) }.getOrNull()

        if (!primary.isNullOrEmpty()) {
            primary
        } else {
            fetchChapterPages("mirror", chapterId)
        }
    }

    private fun fetchChapterPages(route: String, chapterId: String): List<Page> = client.newCall(GET(signedUrl(route, chapterId), headers)).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        response.parseAs<ChapterPagesDto>().toPageList()
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun signedUrl(route: String, input: String? = null): HttpUrl {
        val nonce = System.currentTimeMillis().toString(16).uppercase(Locale.ROOT)
        val signatureInput = input ?: nonce
        val key = md5("M<$signatureInput#MANG33K>D")

        return apiUrl.newBuilder()
            .addPathSegment(route)
            .addPathSegment(nonce)
            .apply { input?.let(::addPathSegment) }
            .addPathSegment(key)
            .build()
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun post(url: HttpUrl, body: RequestBody): Request = Request.Builder()
        .url(url)
        .headers(headers)
        .post(body)
        .build()

    private class PageTag(val page: Int)

    private class SearchTag(
        val mode: String,
        val page: Int,
        val includedTags: List<String>,
        val ignoredIds: List<Long>,
    )

    companion object {
        private const val SEARCH_MODE_HOME = "home"
        private const val SEARCH_MODE_DISCOVER = "discover"
        private const val SEARCH_MODE_QUERY = "query"
        private const val CATALOG_PAGE_SIZE = 24
        private const val SEARCH_PAGE_SIZE = 24
        private const val DISCOVER_PAGE_SIZE = 25
    }
}
