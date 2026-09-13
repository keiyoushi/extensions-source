package eu.kanade.tachiyomi.extension.id.cosmicscansid

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CosmicScansID : KeiSource() {

    private val apiUrl = "https://cdncid.csmcscns.id/v1/manga"

    private val cursorCache = mutableMapOf<String, String>()

    private val lastPage = mutableMapOf<String, Int>()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3, 1.seconds)

    // URL compat: handle old "/manga/slug" and new "/series/slug"
    private fun SManga.slug(): String = url
        .removePrefix("/manga/")
        .removePrefix("/series/")
        .trimEnd('/')

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val key = "popular"
        if (page > 1 && cursorCache["$key:$page"].isNullOrBlank()) {
            return MangasPage(emptyList(), false)
        }
        lastPage[key] = page
        val url = "$apiUrl/filter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("order_by", "popular")
            .addCursor(key, page)
            .build()
        val response = client.get(url)
        return parseMangaPage(response, key)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val key = "update"
        if (page > 1 && cursorCache["$key:$page"].isNullOrBlank()) {
            return MangasPage(emptyList(), false)
        }
        lastPage[key] = page
        val url = "$apiUrl/filter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("order_by", "update")
            .addCursor(key, page)
            .build()
        val response = client.get(url)
        return parseMangaPage(response, key)
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isNotBlank()) {
            if (page > 1) return MangasPage(emptyList(), false)
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "48")
                .addQueryParameter("q", query)
                .build()
            val response = client.get(url)
            val result = response.parseAs<MangaListResponse>()
            return MangasPage(result.data.map { it.toSManga() }, false)
        }

        val key = searchKey(query, filters, "filter")
        if (page > 1 && cursorCache["$key:$page"].isNullOrBlank()) {
            return MangasPage(emptyList(), false)
        }
        lastPage[key] = page
        val url = "$apiUrl/filter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply {
                addFilters(filters)
                addCursor(key, page)
            }
            .build()

        val response = client.get(url)
        return parseMangaPage(response, key)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val firstSegment = url.pathSegments.firstOrNull()
        if (firstSegment != "series" && firstSegment != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/series/$slug")
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ======================= Details and Chapters ==========================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.url.substringAfterLast('/')}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.slug()
        val response = client.get("$apiUrl/mangaDetail/$slug")
        val data = response.parseAs<MangaDetailResponse>().data
        val parsedChapters = data.chapters.orEmpty()
            .filter { it.slug?.isNotBlank() == true && it.redirectLink.isNullOrBlank() }
            .map { it.toSChapter() }
        return SMangaUpdate(
            manga = data.toSMangaDetails(slug),
            chapters = parsedChapters,
        )
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.substringAfterLast('/')
        val response = client.get("$apiUrl/readingPage/$slug")
        val data = response.parseAs<ReadingPageResponse>().data
        if (!data.redirectLink.isNullOrBlank()) return emptyList()
        val chapterUrl = getChapterUrl(chapter)
        return data.toPageList(chapterUrl)
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?): FilterList = getCosmicScansIDFilterList()

    // ============================= Utilities ==============================
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

    private fun Builder.addFilters(filters: FilterList): Builder = apply {
        filters.firstInstanceOrNull<OrderFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("order_by", it) }

        filters.firstInstanceOrNull<StatusFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("release_status", it) }

        filters.firstInstanceOrNull<TypeFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("type_manga", it) }

        filters.firstInstanceOrNull<ProjectFilter>()?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { addQueryParameter("is_project", it) }

        filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter { it.state }
            .forEach { addQueryParameter("genres_slug", it.slug) }
    }

    private fun searchKey(query: String, filters: FilterList, endpoint: String): String = listOf(
        endpoint,
        query,
        filters.firstInstanceOrNull<OrderFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<TypeFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<ProjectFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter { it.state }.joinToString(",") { it.slug },
    ).joinToString(":")

    companion object {
        private const val PAGE_SIZE = 24
    }
}
