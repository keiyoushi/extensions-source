package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

@Source
abstract class ManGeek : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    private val apiUrl = "http://geekstations.com.br/api/v2/pt".toHttpUrl()

    private val discoverLock = Any()
    private var activeDiscoverTags: List<String>? = null
    private val discoveredIds = mutableListOf<Long>()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = fetchHome().catalogMangas()
        val start = (page.coerceAtLeast(1) - 1) * CATALOG_PAGE_SIZE
        val pageMangas = mangas.drop(start).take(CATALOG_PAGE_SIZE)

        return MangasPage(
            pageMangas.map { it.toSManga() },
            start + pageMangas.size < mangas.size,
        )
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = fetchHome().news
            .map { it.manga }
            .distinctBy { it.id }
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val requestedPage = page.coerceAtLeast(1)
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val includedTags = filters.includedTags()

        return when {
            normalizedQuery.isBlank() && includedTags.isEmpty() -> getPopularManga(requestedPage)
            normalizedQuery.isBlank() -> discoverPage(requestedPage, includedTags)
            else -> searchPage(requestedPage, normalizedQuery, includedTags)
        }
    }

    private suspend fun discoverPage(page: Int, includedTags: List<String>): MangasPage {
        val ignoredIds = synchronized(discoverLock) {
            if (page == 1 || activeDiscoverTags != includedTags) {
                activeDiscoverTags = includedTags
                discoveredIds.clear()
            }

            discoveredIds.take((page - 1) * DISCOVER_PAGE_SIZE)
        }

        val response = client.post(
            signedUrl("discover"),
            DiscoverBody(includedTags, ignoredIds).toJsonRequestBody(),
        )

        val responseMangas = response.parseAs<List<MangaDto>>()
        val ignoredIdSet = ignoredIds.toHashSet()
        val mangas = responseMangas
            .filterNot { it.id in ignoredIdSet }
            .distinctBy { it.id }
            .also { pageMangas ->
                synchronized(discoverLock) {
                    if (activeDiscoverTags == includedTags) {
                        discoveredIds.clear()
                        discoveredIds.addAll(ignoredIds)
                        discoveredIds.addAll(pageMangas.map { it.id })
                    }
                }
            }

        return MangasPage(
            mangas.map { it.toSManga() },
            responseMangas.size == DISCOVER_PAGE_SIZE,
        )
    }

    private suspend fun searchPage(page: Int, query: String, includedTags: List<String>): MangasPage {
        val response = client.post(
            signedUrl("search", query),
            SearchBody(includedTags).toJsonRequestBody(),
        )

        val mangas = response.parseAs<List<MangaDto>>()
        val start = (page - 1) * SEARCH_PAGE_SIZE
        val pageMangas = mangas.drop(start).take(SEARCH_PAGE_SIZE)

        return MangasPage(
            pageMangas.map { it.toSManga() },
            start + pageMangas.size < mangas.size,
        )
    }

    private suspend fun fetchHome(): HomeDto = client.get(signedUrl("home")).parseAs<HomeDto>()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { url.pathSegments.getOrNull(0) == "manga" }
            ?: return null

        val manga = SManga.create().apply { this.url = id }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get(signedUrl("manga", manga.url)).parseAs<MangaDto>()

        return SMangaUpdate(
            manga = dto.toSManga(details = true),
            chapters = if (fetchChapters) {
                dto.chapters
                    .orEmpty()
                    .asReversed()
                    .map { it.toSChapter() }
            } else {
                chapters
            },
        )
    }

    override val supportsRelatedMangas = false

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val primary = runCatching { fetchChapterPages("chapter", chapter.url) }.getOrNull()

        if (!primary.isNullOrEmpty()) {
            return primary
        }

        return fetchChapterPages("mirror", chapter.url)
    }

    private suspend fun fetchChapterPages(route: String, chapterId: String): List<Page> = client.get(signedUrl(route, chapterId)).parseAs<ChapterPagesDto>().toPageList()

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

    companion object {
        private const val CATALOG_PAGE_SIZE = 24
        private const val SEARCH_PAGE_SIZE = 24
        private const val DISCOVER_PAGE_SIZE = 25
    }
}
