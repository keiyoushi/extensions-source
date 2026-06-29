package eu.kanade.tachiyomi.extension.en.comicland

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class ComicLand : HttpSource() {

    override val name = "ComicLand"

    override val baseUrl = "https://comicland.org"

    override val lang = "en"

    override val supportsLatest = true

    private val apiUrl = "https://api.comicland.org/api"

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 20
        return GET("$apiUrl/comics/popular?offset=$offset&limit=20", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<ApiResponse<PageData>>()
        val data = res.data ?: return MangasPage(emptyList(), false)

        return MangasPage(data.comics.map { it.toSManga() }, data.hasNextPage)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * 20
        return GET("$apiUrl/comics?offset=$offset&limit=20&status=ongoing", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val offset = (page - 1) * 20

        if (query.isNotBlank()) {
            val url = "$apiUrl/comic/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", "20")
                .build()

            return GET(url, headers)
        }

        val categoryFilter = filters.firstInstanceOrNull<Filters>()
        val endpoint = categoryFilter?.selectedEndpoint ?: "/comics"
        val status = categoryFilter?.selectedStatus

        val url = "$apiUrl$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "20")

        if (status != null) {
            url.addQueryParameter("status", status)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/comic/detail?slug=${manga.url}", headers)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<ApiResponse<ComicDetailDto>>()
        val data = res.data ?: throw Exception("Failed to parse manga details")

        return data.toSManga()
    }

    // ============================= Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.parseAs<ApiResponse<ComicDetailDto>>()
        val data = res.data ?: throw Exception("Failed to parse chapters")
        val slug = data.slug

        return data.chapters?.map { it.toSChapter(slug) }?.reversed() ?: emptyList()
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.substringAfter("/comic/").substringBefore("/chapter/")
        val index = chapter.url.substringAfter("/chapter/")

        return GET("$apiUrl/chapter/pages_by_index?slug=$slug&index=$index", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res = response.parseAs<ApiResponse<PagesData>>()
        val pages = res.data?.pages ?: emptyList()

        return pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        Filter.Header("Text search ignores Category filter"),
        Filters(),
    )
}
