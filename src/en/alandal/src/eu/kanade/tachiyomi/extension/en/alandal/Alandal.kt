package eu.kanade.tachiyomi.extension.en.alandal

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Alandal : HttpSource() {

    override val name = "Alandal"

    override val baseUrl = "https://alandal.com"
    private val apiUrl = "https://qq.alandal.com/api"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Referer", "$baseUrl/")
    }

    private val apiHeaders by lazy { apiHeadersBuilder.build() }

    private val apiHeadersBuilder = headersBuilder().apply {
        add("Accept", "application/json")
        add("Host", apiUrl.toHttpUrl().host)
        add("Origin", baseUrl)
        add("Sec-Fetch-Dest", "empty")
        add("Sec-Fetch-Mode", "cors")
        add("Sec-Fetch-Site", "same-origin")
    }

    private val json: Json by injectLazy()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(SortFilter("popular")))

    override fun popularMangaParse(response: Response): MangasPage =
        searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(page, "", FilterList(SortFilter("new")))

    override fun latestUpdatesParse(response: Response): MangasPage =
        searchMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            if (query.isNotBlank()) {
                addQueryParameter("name", query)
            }
            addQueryParameter("type", "comic")

            val filterList = filters.ifEmpty { getFilterList() }
            filterList.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }

            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<ResponseDto<SearchSeriesDto>>().data.series
        val mangaList = data.data.map { it.toSManga() }
        val hasNextPage = data.currentPage < data.lastPage
        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        SortFilter(),
        StatusFilter(),
    )

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String =
        baseUrl + manga.url.replace("series/", "series/comic-")

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments(manga.url.substringAfter("/"))
            addQueryParameter("type", "comic")
        }.build()

        return GET(url, apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<ResponseDto<MangaDetailsDto>>().data.series.toSManga()

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
            .replace("series/", "chapter/comic-")
            .replace("chapters/", "")
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl${manga.url}".toHttpUrl().newBuilder().apply {
            addPathSegment("chapters")
            addQueryParameter("type", "comic")
            addQueryParameter("from", "0")
            addQueryParameter("to", "999")
        }.build()

        return GET(url, apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.newBuilder()
            .query(null)
            .removePathSegment(0) // Remove /api
            .build()
            .encodedPath

        return response.parseAs<ChapterResponseDto>().data.map {
            it.toSChapter(slug)
        }.reversed()
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.name.startsWith("[LOCKED]")) {
            throw Exception("Log in and unlock chapter in webview, then refresh chapter list")
        }

        val url = "$apiUrl${chapter.url}".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "comic")
            addQueryParameter("traveler", "0")
        }.build()

        return GET(url, apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PagesResponseDto>().data.chapter.chapter

        return data.pages.mapIndexed { index, s ->
            Page(index, imageUrl = s)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val pageHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, pageHeaders)
    }

    // ============================= Utilities ==============================

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }
}
