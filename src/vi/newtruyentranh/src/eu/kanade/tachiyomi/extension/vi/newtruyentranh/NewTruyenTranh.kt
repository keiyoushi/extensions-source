package eu.kanade.tachiyomi.extension.vi.newtruyentranh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class NewTruyenTranh : HttpSource() {
    override val name = "NewTruyenTranh"
    override val lang = "vi"
    override val baseUrl = "https://newtruyentranh5.com"
    override val supportsLatest = true

    private val apiUrl = "https://newtruyenhot.4share.me"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = buildSearchRequest(page, "", FilterList(), "10") // Top all

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.channels.map { it.toSManga() }
        val hasNextPage = result.loadMore?.pageInfo?.let {
            it.currentPage < it.lastPage
        } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int) = buildSearchRequest(page, "", FilterList(), "0") // Newest

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ================================
    private fun buildSearchRequest(page: Int, query: String, filters: FilterList, defaultSort: String): Request {
        var genreSlug: String? = null
        var sortValue: String? = null
        var statusValue: String? = null

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.state != 0) {
                        genreSlug = filter.toUriPart()
                    }
                }

                is SortFilter -> {
                    if (filter.state != 0) {
                        sortValue = filter.toUriPart()
                    }
                }

                is StatusFilter -> {
                    if (filter.state != 0) {
                        statusValue = filter.toUriPart()
                    }
                }

                else -> {}
            }
        }

        if (genreSlug != null && query.isBlank()) {
            val url = "$apiUrl/page/$genreSlug".toHttpUrl().newBuilder()
                .addQueryParameter("p", page.toString())
                .build()
            return GET(url, headers)
        }

        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("sort", sortValue ?: defaultSort)
            if (statusValue != null) {
                addQueryParameter("status", statusValue)
            }
            addQueryParameter("p", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = buildSearchRequest(page, query, filters, "0")

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        SortFilter(),
        StatusFilter(),
    )

    // ============================== Details ===============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = manga.url
        if (url.startsWith("/detail/")) throw Exception("Migrate from $name to $name")
        val id = manga.url.substringBefore(":")
        val slug = manga.url.substringAfter(":")
        return GET("$baseUrl/truyen-tranh/$slug-$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            genre = document.select(".tr-theloai").joinToString { it.text() }
            author = document.select("p:contains(Tác giả) + p").text()
            status = parseStatus(document.select("p:contains(Tình trạng) + p").text())
        }
    }

    private fun parseStatus(status: String?): Int {
        val ongoingWords = listOf("Đang Cập Nhật", "Đang Tiến Hành")
        val completedWords = listOf("Hoàn Thành", "Đã Hoàn Thành")
        val hiatusWords = listOf("Tạm ngưng", "Tạm hoãn")
        return when {
            status == null -> SManga.UNKNOWN
            ongoingWords.any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
            completedWords.any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
            hiatusWords.any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val id = manga.url.substringBefore(":")
        val slug = manga.url.substringAfter(":")
        return "$baseUrl/truyen-tranh/$slug-$id"
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val url = manga.url
        if (url.startsWith("/detail/")) throw Exception("Migrate from $name to $name")
        val id = manga.url.substringBefore(":")
        val slug = manga.url.substringAfter(":")
        return GET("$apiUrl/detail/$id#$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailResponse>()
        val slug = response.request.url.toString().substringAfterLast("#")
        val chapters = result.sources.first().contents.flatMap { scm ->
            scm.streams.map { it.toSChapter(slug) }
        }.sortedByDescending { it.chapter_number }

        return chapters
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterId = chapter.url.substringBefore(":")
        val slug = chapter.url.substringAfter(":").substringBefore("#")
        val index = chapter.url.substringAfter("#")
        return "$baseUrl/truyen-tranh/$slug/chapter-$index/$chapterId"
    }

    // ============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val url = chapter.url
        if (url.startsWith("/detail/")) throw Exception("Migrate from $name to $name")
        val chapterId = chapter.url.substringBefore(":")
        return GET("$apiUrl/chapter/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageListResponse>()
        return result.files.mapIndexed { index, file ->
            Page(index, imageUrl = file.url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
