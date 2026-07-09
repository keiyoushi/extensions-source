package eu.kanade.tachiyomi.extension.vi.zettruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class ZetTruyen : HttpSource() {
    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val apiDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 1 }))

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(mangaListSelector()).map(::mangaFromElement)
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private fun mangaListSelector() = "div.grid a[href*=/truyen-tranh/]"

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("span.line-clamp-2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 0 }))

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("name", query)
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> setQueryParameter("sort", filter.toUriPart())
                    is StatusFilter -> setQueryParameter("status", filter.toUriPart())
                    is TypeFilter -> setQueryParameter("type", filter.toUriPart())
                    is ChapterFilter -> setQueryParameter("chapterRange", filter.toUriPart())
                    is GenreFilter -> {
                        val genres = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.id }
                        setQueryParameter("genres", genres)
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Filters ===============================
    override fun getFilterList() = getFilters()

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("img[src*=/thumb/]")?.absUrl("src")
            author = document.getInfoValue("Tác giả")
            status = parseStatus(document.getInfoValue("Trạng thái"))
            genre = document.getGenres()
            description = document.selectFirst("p.comic-content")?.wholeText()?.trim()
        }
    }

    private fun Document.getInfoValue(label: String): String? {
        val element = select("div, span, p").firstOrNull { it.ownText() == label }
            ?: return null
        return element.nextElementSibling()?.text()
    }

    private fun Document.getGenres(): String? {
        val genreLabel = select("div, span").firstOrNull {
            it.ownText() == "Thể loại" && it.closest("header") == null
        } ?: return null
        return genreLabel.nextElementSibling()
            ?.select("a")
            ?.joinToString { it.text() }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn Thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val allChapters = mutableListOf<SChapter>()
        var currentPage = 1
        var lastPage: Int

        do {
            val response = client.newCall(chapterListRequest(manga, currentPage)).execute()
            val result = response.parseAs<ChapterListResponse>()

            allChapters.addAll(chapterListParse(response, result))
            lastPage = result.data?.lastPage ?: 1
            currentPage++
        } while (currentPage <= lastPage)

        allChapters
    }

    override fun chapterListRequest(manga: SManga) = chapterListRequest(manga, 1)

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val slug = manga.url.substringAfterLast("/")
        val apiUrl = "$baseUrl/api/comics/$slug/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "100")
            .addQueryParameter("order", "desc")
            .build()
        val apiHeaders = headers.newBuilder()
            .add("Accept", "application/json")
            .build()

        return GET(apiUrl, apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterListResponse>()
        return chapterListParse(response, result)
    }

    private fun chapterListParse(response: Response, result: ChapterListResponse): List<SChapter> {
        val data = result.data ?: return emptyList()
        val slug = response.request.url.pathSegments.let { it[it.size - 2] }

        return data.chapters.map { chapter ->
            val chapterSlug = chapter.chapterSlug.replace("chapter-", "chuong-")
            SChapter.create().apply {
                url = "/truyen-tranh/$slug/$chapterSlug"
                name = chapter.chapterName
                date_upload = chapter.updatedAt?.substringBefore(".")
                    ?.let { apiDateFormat.tryParse(it) }
                    ?: 0L
            }
        }
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.center img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }.ifEmpty {
            document.select("div.w-full.mx-auto.center img").mapIndexed { index, element ->
                Page(index, imageUrl = element.absUrl("src"))
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }
}
