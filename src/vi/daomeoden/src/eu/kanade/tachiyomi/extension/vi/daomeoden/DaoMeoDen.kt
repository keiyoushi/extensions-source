package eu.kanade.tachiyomi.extension.vi.daomeoden

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Source
abstract class DaoMeoDen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    private val apiUrl
        get() = baseUrl.toHttpUrl().host.let { host ->
            val parts = host.split('.')
            if (parts.size >= 2) "https://api.${parts.takeLast(2).joinToString(".")}" else "https://api.$host"
        }

    private fun searchUrl(page: Int): HttpUrl.Builder = "$apiUrl/api/search/advanced".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("limit", "24")

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = searchUrl(page)
            .addQueryParameter("sort", "views")
            .build()
        return parseSearchPage(client.get(url, Fetch.XHR.from(baseUrl)))
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = searchUrl(page)
            .addQueryParameter("sort", "latest")
            .build()
        return parseSearchPage(client.get(url, Fetch.XHR.from(baseUrl)))
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val builder = searchUrl(page)

        if (query.isNotEmpty()) {
            builder.addQueryParameter("q", query)
        }

        filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
            ?.takeIf { it.isNotEmpty() }
            ?.let { builder.addQueryParameter("status", it) }

        filters.firstInstanceOrNull<AdultFilter>()?.toUriPart()
            ?.takeIf { it.isNotEmpty() }
            ?.let { builder.addQueryParameter("adult", it) }

        filters.firstInstanceOrNull<SortFilter>()?.toUriPart()
            ?.takeIf { it.isNotEmpty() }
            ?.let { builder.addQueryParameter("sort", it) }

        filters.firstInstanceOrNull<GenreFilter>()?.included()
            ?.takeIf { it.isNotEmpty() }
            ?.let { builder.addQueryParameter("genres", it.joinToString(",")) }

        filters.firstInstanceOrNull<TagFilter>()?.included()
            ?.takeIf { it.isNotEmpty() }
            ?.let { builder.addQueryParameter("tags", it.joinToString(",")) }

        return parseSearchPage(client.get(builder.build(), Fetch.XHR.from(baseUrl)))
    }

    private fun parseSearchPage(response: Response): MangasPage {
        val payload = response.parseAs<SearchResponse>()
        val mangas = payload.data.map { it.toSManga() }
        return MangasPage(mangas, payload.pagination.hasNext)
    }

    // ============================== Details ===============================

    private fun mangaId(manga: SManga): Int? = manga.url
        .substringAfterLast('-')
        .toIntOrNull()

    private fun chapterId(chapter: SChapter): Int? = chapter.url
        .substringAfterLast('/')
        .substringAfter("chuong-")
        .split('-')
        .getOrNull(1)
        ?.toIntOrNull()

    private suspend fun fetchMangaDetail(manga: SManga): MangaDetailDto {
        val id = mangaId(manga) ?: return MangaDetailDto()
        return client.get("$apiUrl/api/manga/$id", Fetch.XHR.from(getMangaUrl(manga)))
            .parseAs<MangaDetailResponse>()
            .data
    }

    private suspend fun fetchRelatedFromApi(manga: SManga): List<SManga> {
        if (mangaId(manga) == null) return emptyList()
        return fetchMangaDetail(manga)
            .recommendations
            .let { it as? JsonArray }
            ?.map { it.parseAs<BookDto>().toSManga() }
            .orEmpty()
            .filterNot { it.url == manga.url }
            .distinctBy { it.url }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val mangaPath = when (url.pathSegments.firstOrNull()) {
            "truyen-tranh" -> url.encodedPath
            "doc-truyen-tranh" -> {
                val mangaSlug = url.pathSegments.getOrNull(1) ?: return null
                "/truyen-tranh/$mangaSlug"
            }
            else -> return null
        }
        val manga = SManga.create().apply { setUrlWithoutDomain(mangaPath) }

        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = mangaId(manga)
            ?: return SMangaUpdate(manga = manga, chapters = chapters)

        return coroutineScope {
            val detailDeferred = async {
                if (fetchDetails) fetchMangaDetail(manga).toSManga(manga.url) else manga
            }
            val chaptersDeferred = async {
                if (fetchChapters) {
                    client.get("$apiUrl/api/manga/$id/chapters", Fetch.XHR.from(getMangaUrl(manga)))
                        .parseAs<ChapterListResponse>()
                        .data
                        .map { it.toSChapter() }
                        .sortedByDescending { it.chapter_number }
                } else {
                    chapters
                }
            }
            SMangaUpdate(manga = detailDeferred.await(), chapters = chaptersDeferred.await())
        }
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapterId(chapter) ?: return emptyList()
        val chapterUrl = getChapterUrl(chapter)

        val images = client.get("$apiUrl/api/chapters/$id", Fetch.XHR.from(chapterUrl))
            .parseAs<ChapterDetailResponse>()
            .data
            .images

        return images.mapIndexed { index, imageUrl ->
            Page(index, url = chapterUrl, imageUrl = imageUrl)
        }
    }

    override fun getImageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url.ifEmpty { "$baseUrl/" })
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/api/search/filters", Fetch.XHR.from(baseUrl))
        .parseAs<FiltersResponse>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val parsed = data?.parseAs<FiltersResponse>()?.data
        return getFilters(
            genres = parsed?.categories?.map { FilterOption(it.name, it.slug) },
            tags = parsed?.tags?.map { FilterOption(it.name, it.slug) },
        )
    }

    // ============================== Related ===============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = fetchRelatedFromApi(manga)
}
