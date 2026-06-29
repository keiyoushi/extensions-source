package eu.kanade.tachiyomi.extension.vi.fastscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FastScan : HttpSource() {
    override val name = "FastScan"
    override val lang = "vi"
    override val baseUrl = "https://fastscan.org"
    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 4 }))

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 0 }))

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("tim-kiem")
                addQueryParameter("q", query)
            } else {
                addPathSegment("tim-kiem-nang-cao")
                val filterList = filters.ifEmpty { getFilterList() }

                addQueryParameter("category", filterList.firstInstanceOrNull<GenreFilter>()?.selected ?: "")
                addQueryParameter("notcategory", "")
                addQueryParameter("status", filterList.firstInstanceOrNull<StatusFilter>()?.value ?: "0")
                addQueryParameter("minchapter", filterList.firstInstanceOrNull<MinChapterFilter>()?.value ?: "0")
                addQueryParameter("sort", filterList.firstInstanceOrNull<SortFilter>()?.value ?: "0")
            }
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    private fun parseMangaListPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.list_grid.grid > li").mapNotNull { element ->
            val mangaLink = element.selectFirst(".book_avatar a, .book_name a") ?: return@mapNotNull null
            val mangaTitle = element.selectFirst(".book_name a")?.text() ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(mangaLink.absUrl("href"))
                title = mangaTitle
                thumbnail_url = element.selectFirst("img")?.let { img ->
                    img.absUrl("data-src").ifEmpty { img.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst(".page_redirect a:contains(›)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".book_detail .book_other h1")!!.text()
            author = document.selectFirst("P:contains(Tác giả) + p")?.text()
            genre = document.select(".book_other ul.list01 a").joinToString { it.text() }
            description = document.select(".story-detail-info")
                .joinToString("\n\n") { container ->
                    val blocks = container.select("p")
                    if (blocks.isNotEmpty()) blocks.joinToString("\n\n") { it.wholeText().trim() } else container.wholeText().trim()
                }
            status = parseStatus(document.selectFirst("li.status p.col-xs-9")?.text())
            thumbnail_url = document.selectFirst(".book_info .book_avatar img")?.let { img ->
                img.absUrl("data-src").ifEmpty { img.absUrl("src") }
            }
        }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("Đang Cập Nhật", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".list_chapter .works-chapter-item").map { element ->
            chapterFromElement(element)
        }
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val chapterLink = element.selectFirst(".name-chap a[href]")!!
        setUrlWithoutDomain(chapterLink.absUrl("href"))
        name = chapterLink.text()
        date_upload = dateFormat.tryParse(
            element.selectFirst(".time-chap")?.text(),
        )
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#chapter_content img, .page-chapter img, img.lozad")
            .mapNotNull { element ->
                val url = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
                url.takeIf { it.isNotBlank() && !it.startsWith("data:") }
            }
            .distinct()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
