package eu.kanade.tachiyomi.extension.vi.zettruyen

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ZetTruyen : HttpSource() {
    override val name = "ZetTruyen"
    override val lang = "vi"
    override val baseUrl = "https://www.zettruyen.us"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val apiDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = buildSearchRequest(page, "", FilterList(), "rating")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(mangaListSelector()).map { mangaFromElement(it) }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private fun mangaListSelector() = "div.grid a[href*=/truyen-tranh/]"

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("span.line-clamp-2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int) = buildSearchRequest(page, "", FilterList(), "latest")

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ================================
    private fun buildSearchRequest(page: Int, query: String, filters: FilterList, defaultSort: String): Request {
        var sortValue = defaultSort
        var statusValue = "all"
        var typeValue = "all"
        val genreValues = mutableListOf<String>()

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> sortValue = filter.toUriPart()

                is StatusFilter -> statusValue = filter.toUriPart()

                is TypeFilter -> typeValue = filter.toUriPart()

                is GenreFilter -> {
                    filter.state.forEach { genre ->
                        if (genre.state) {
                            genreValues.add(genre.id)
                        }
                    }
                }

                else -> {}
            }
        }

        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .addQueryParameter("genres", genreValues.joinToString(","))
            .addQueryParameter("status", statusValue)
            .addQueryParameter("type", typeValue)
            .addQueryParameter("sort", sortValue)
            .addQueryParameter("chapterRange", "all")
            .addQueryParameter("name", query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = buildSearchRequest(page, query, filters, "latest")

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Filters ===============================
    override fun getFilterList() = getFilters()

    // ============================== Details ===============================
    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("img[src*=/thumb/]")?.absUrl("src")
            author = document.getInfoValue("Tác giả")
            status = parseStatus(document.getInfoValue("Trạng thái"))
            genre = document.getGenres()
            description = document.selectFirst("p.comic-content")?.text()
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
    override fun fetchChapterList(manga: SManga): rx.Observable<List<SChapter>> = rx.Observable.fromCallable {
        val allChapters = mutableListOf<SChapter>()
        val slug = (baseUrl + manga.url).toHttpUrl().pathSegments[1]

        var currentPage = 1
        var lastPage = 1

        do {
            val apiUrl = "$baseUrl/api/comics/$slug/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("page", currentPage.toString())
                .addQueryParameter("per_page", "100")
                .addQueryParameter("order", "desc")
                .build()

            val apiHeaders = headers.newBuilder()
                .add("Accept", "application/json")
                .build()

            val request = GET(apiUrl, apiHeaders)

            val response = client.newCall(request).execute()
            val result = response.parseAs<ChapterListResponse>()
            val data = result.data ?: break

            lastPage = data.lastPage

            data.chapters.forEach { chapter ->
                // API uses 'chapter-X' but website uses 'chuong-X'
                val chapterSlug = chapter.chapterSlug.replace("chapter-", "chuong-")
                allChapters.add(
                    SChapter.create().apply {
                        url = "/truyen-tranh/$slug/$chapterSlug"
                        name = chapter.chapterName
                        date_upload = chapter.updatedAt?.substringBefore(".")
                            ?.let { apiDateFormat.tryParse(it) }
                            ?: 0L
                    },
                )
            }

            currentPage++
        } while (currentPage <= lastPage)

        allChapters
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // ============================== Pages =================================
    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

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
