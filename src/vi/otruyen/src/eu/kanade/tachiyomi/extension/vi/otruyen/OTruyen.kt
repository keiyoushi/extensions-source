package eu.kanade.tachiyomi.extension.vi.otruyen

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class OTruyen : KeiSource() {
    private val domainName = "otruyen"

    private val domainApi = "${domainName}api.com"

    private val apiUrl = "https://$domainApi/v1/api"

    private val cdnUrl = "https://sv1.${domainName}cdn.com"

    private val imgUrl = "https://img.$domainApi/uploads/comics"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            StatusList().apply { state = 2 }, // Hoàn thành
        ),
    )

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            StatusList().apply { state = 0 }, // Mới nhất
        ),
    )

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val (segments, params) = when {
            query.isNotBlank() -> {
                listOf("tim-kiem") to mapOf("keyword" to query)
            }

            else -> {
                filters.filterIsInstance<GenresFilter>().firstOrNull()?.let { genre ->
                    listOf("the-loai", genre.values[genre.state].slug) to emptyMap()
                } ?: filters.filterIsInstance<StatusList>().firstOrNull()?.let { status ->
                    listOf("danh-sach", status.values[status.state].slug) to emptyMap()
                } ?: (listOf("danh-sach", "dang-phat-hanh") to emptyMap())
            }
        }

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            addQueryParameter("page", page.toString())
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        client.get(url, headers).use { response ->
            return parseMangaPage(response)
        }
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val res = response.parseAs<DataDto<ListingData>>()
        val pagination = res.data.params.pagination
        val totalPages = (pagination.totalItems + pagination.totalItemsPerPage - 1) / pagination.totalItemsPerPage
        val manga = res.data.items.map { it.toSManga(imgUrl) }
        val hasNextPage = pagination.currentPage < totalPages
        return MangasPage(manga, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments.contains("truyen-tranh")) {
            val slug = url.pathSegments.last()
            val manga = SManga.create().apply { this.url = slug }
            return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/truyen-tranh/${manga.url}"
        client.get(url).use { response ->
            val res = response.parseAs<DataDto<EntryData>>()
            val details = parseMangaDetails(res.data.item)
            val chaptersList = parseChapterList(res.data.item)

            return SMangaUpdate(details, chaptersList)
        }
    }

    private fun parseMangaDetails(item: Entry): SManga = item.toSManga(imgUrl)

    private fun parseChapterList(item: Entry): List<SChapter> = item.chapters
        .flatMap { server -> server.serverData.map { it.toSChapter(item.updatedAt, item.slug) } }
        .sortedByDescending { it.chapter_number }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen-tranh/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaUrl = chapter.url.substringAfter(":")
        return "$baseUrl/truyen-tranh/$mangaUrl"
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringBefore(":")
        client.get("$cdnUrl/v1/api/chapter/$chapterId").use { response ->
            val res = response.parseAs<DataDto<PageDto>>()
            return res.data.toPage()
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/the-loai").use {
        it.parseAs<JsonElement>()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data)
}
