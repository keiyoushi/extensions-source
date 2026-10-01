package eu.kanade.tachiyomi.extension.tr.juratempest

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class JuraTempest : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 3, period = 1.seconds)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Sec-Fetch-Site", "same-origin")

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/explore/${manga.url.removePrefix("/explore/").trim('/')}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/explore/${chapter.url.removePrefix("/explore/").trim('/')}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "explore") return null

        val slug = segments[1]
        val manga = SManga.create().apply { this.url = slug }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = slug
            }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * SEARCH_PAGE_SIZE
        val response = client.post(
            "$baseUrl/api/rpc/search/manga",
            body = RpcRequest(SearchRequestPayload(q = WILDCARD_QUERY, limit = SEARCH_PAGE_SIZE, offset = offset)).toJsonRequestBody(),
        ).parseAs<RpcResponse<SearchResultDto>>()

        val result = response.json
        val mangas = result.hits.map { it.toSManga() }
        val hasNextPage = (result.offset + result.hits.size) < result.estimatedTotalHits

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val response = client.post(
            "$baseUrl/api/rpc/release/latest",
            body = RpcRequest(EmptyPayload()).toJsonRequestBody(),
        ).parseAs<RpcResponse<List<LatestReleaseDto>>>()

        val mangas = response.json
            .map { it.toSManga() }
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return getPopularManga(page)
        }

        if (trimmed.length < MIN_QUERY_LENGTH) {
            return searchShortQuery(trimmed, page)
        }

        val offset = (page - 1) * SEARCH_PAGE_SIZE
        val response = client.post(
            "$baseUrl/api/rpc/search/manga",
            body = RpcRequest(SearchRequestPayload(q = trimmed, limit = SEARCH_PAGE_SIZE, offset = offset)).toJsonRequestBody(),
        ).parseAs<RpcResponse<SearchResultDto>>()

        val result = response.json
        val mangas = result.hits.map { it.toSManga() }
        val hasNextPage = (result.offset + result.hits.size) < result.estimatedTotalHits

        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun searchShortQuery(query: String, page: Int): MangasPage = coroutineScope {
        val page1 = async { fetchCatalogueBatch(0) }
        val page2 = async { fetchCatalogueBatch(50) }
        val page3 = async { fetchCatalogueBatch(100) }

        val allHits = (page1.await() + page2.await() + page3.await())
            .distinctBy { it.slug }

        val matched = allHits
            .filter { it.matches(query) }
            .map { it.toSManga() }

        val fromIndex = (page - 1) * SEARCH_PAGE_SIZE
        if (fromIndex >= matched.size) {
            return@coroutineScope MangasPage(emptyList(), false)
        }

        val paged = matched.drop(fromIndex).take(SEARCH_PAGE_SIZE)
        val hasNext = matched.size > page * SEARCH_PAGE_SIZE
        MangasPage(paged, hasNext)
    }

    private suspend fun fetchCatalogueBatch(offset: Int): List<MangaDto> = try {
        val response = client.post(
            "$baseUrl/api/rpc/search/manga",
            body = RpcRequest(SearchRequestPayload(q = WILDCARD_QUERY, limit = 50, offset = offset)).toJsonRequestBody(),
        ).parseAs<RpcResponse<SearchResultDto>>()
        response.json.hits
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.removePrefix("/explore/").trim('/')

        val detailsDeferred = if (fetchDetails) {
            async {
                client.post(
                    "$baseUrl/api/rpc/manga/bySlug",
                    body = RpcRequest(MangaSlugPayload(slug)).toJsonRequestBody(),
                ).parseAs<RpcResponse<MangaDto>>().json.toSMangaDetails()
            }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async {
                client.post(
                    "$baseUrl/api/rpc/chapter/byMangaSlug",
                    body = RpcRequest(MangaSlugPayload(slug)).toJsonRequestBody(),
                ).parseAs<RpcResponse<List<ChapterDto>>>().json.map { it.toSChapter(slug) }
            }
        } else {
            null
        }

        SMangaUpdate(
            manga = detailsDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val parts = chapter.url.removePrefix("/explore/").trim('/').split("/")
        if (parts.size < 2) return emptyList()
        val mangaSlug = parts[0]
        val chapterSlug = parts[1]

        val response = client.post(
            "$baseUrl/api/rpc/release/byChapterSlug",
            body = RpcRequest(ChapterReleasePayload(mangaSlug, chapterSlug)).toJsonRequestBody(),
        ).parseAs<RpcResponse<List<ReleaseDto>>>()

        val release = response.json.maxByOrNull { it.pages.size } ?: return emptyList()

        return release.pages
            .sortedBy { it.number }
            .mapIndexedNotNull { index, page ->
                page.url?.let { Page(index, imageUrl = it) }
            }
    }

    companion object {
        private const val SEARCH_PAGE_SIZE = 20
        private const val MIN_QUERY_LENGTH = 3
        private const val WILDCARD_QUERY = "***"
    }
}
