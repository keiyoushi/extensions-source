package eu.kanade.tachiyomi.extension.vi.otakusic

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
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Otakusic : HttpSource() {
    override val name = "Otakusic"
    override val lang = "vi"
    override val baseUrl = "https://otakusic.com"
    override val supportsLatest = true

    private val imgBaseUrl = baseUrl.replace("://", "://img.")

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun apiHeaders() = headers.newBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .add("Accept", "application/json")
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "views")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "updated")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        var sort = "updated"

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> sort = filter.toUriPart()
                is StatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                is GenreFilter -> filter.toUriPart()?.let { url.addQueryParameter("category", it) }
                else -> {}
            }
        }

        url.addQueryParameter("sort", sort)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    override fun getFilterList(): FilterList = getFilters()

    // =============================== Listing ==============================

    private fun parseMangaListPage(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("a[href*=/chi-tiet/]")
            .filter { it.selectFirst("img") != null }
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    title = element.selectFirst("img")!!.attr("alt")
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                }
            }
            .distinctBy { it.url }

        val hasNextPage = document.selectFirst("a.pagination-btn:contains(Sau)") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()

            author = document.select("h2:contains(Tác giả) + div a, h2:contains(Tác giả) ~ a")
                .joinToString { it.text() }
                .ifEmpty {
                    document.selectFirst("h2:contains(Tác giả)")
                        ?.parent()
                        ?.ownText()
                        ?.replace(":", "")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && it != "Đang cập nhật" }
                }

            genre = document.select("div.flex.flex-wrap.gap-2 a")
                .joinToString { it.text() }

            description = document.selectFirst("#description")?.text()

            thumbnail_url = document.selectFirst("img[alt]")?.absUrl("src")

            status = when {
                document.selectFirst("a[href*='status=ongoing']") != null -> SManga.ONGOING
                document.selectFirst("a[href*='status=completed']") != null -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/chi-tiet/").trimEnd('/')
        return GET("$baseUrl/api/v1/manga/chapters/$slug", apiHeaders())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<ChaptersResponse>().data
        val mangaSlug = response.request.url.pathSegments.last()

        return chapters
            .filter { it.status != "inactive" }
            .map { dto ->
                SChapter.create().apply {
                    // Store manga slug and chapter info for page list retrieval
                    url = "$CHAPTER_URL_PREFIX$mangaSlug/${dto.chapterOriginalSlug}/${dto.chapterSlug}"
                    name = "Chương ${dto.chapterName.content}"
                    date_upload = dto.updatedAt?.let { dateFormat.tryParse(it) } ?: 0L
                }
            }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.removePrefix(CHAPTER_URL_PREFIX).split("/")
        return "$baseUrl/doc-truyen/${parts[0]}/${parts[2]}"
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun fetchPageList(chapter: SChapter): rx.Observable<List<Page>> {
        val parts = chapter.url.removePrefix(CHAPTER_URL_PREFIX).split("/")
        val mangaSlug = parts[0]
        val chapterOriginalSlug = parts[1]

        val request = GET("$baseUrl/api/v1/manga/chapters/$mangaSlug", apiHeaders())
        val response = client.newCall(request).execute()
        val chapters = response.parseAs<ChaptersResponse>().data

        val chapterDto = chapters.firstOrNull { it.chapterOriginalSlug == chapterOriginalSlug }
            ?: throw Exception("Chapter not found")

        val apiUrl = chapterDto.apiUrl
            ?: throw Exception("No image data available for this chapter")

        val imageFilenames: List<String> = json.decodeFromString(apiUrl)

        val pages = imageFilenames.mapIndexed { index, filename ->
            val imageUrl = "$imgBaseUrl/manga/uploads/chapter/$mangaSlug/$chapterOriginalSlug/$filename"
            Page(index, imageUrl = imageUrl)
        }

        return rx.Observable.just(pages)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers ================================

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    companion object {
        private const val CHAPTER_URL_PREFIX = "/api/chapter/"
    }
}
