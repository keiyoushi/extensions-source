package eu.kanade.tachiyomi.extension.vi.truyenhentaivn

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Locale
import java.util.TimeZone

class TruyenHentaivn : HttpSource() {
    override val name = "TruyenHentaivn"
    override val lang = "vi"
    override val baseUrl = "https://truyenhentaivn.club"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = listPageRequest("/top-de-cu", page)

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = listPageRequest("/danh-sach", page)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()

        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem-truyen/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        }

        if (genrePath != null) {
            return listPageRequest(genrePath, page)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response)

    private fun listPageRequest(path: String, page: Int): Request {
        val url = "$baseUrl$path".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.entry.text-center").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".z-pagination a.page-numbers[title=Next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("a.name")!!
        setUrlWithoutDomain(titleElement.absUrl("href"))
        title = titleElement.attr("title").ifEmpty { titleElement.text() }
        thumbnail_url = element.selectFirst("a.s-thumb img")?.absUrl("src")
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".comic-info .info h1.name")!!.text()
            author = document.selectFirst(".meta-data .author i")
                ?.text()
                ?.ifEmpty { null }
            genre = document.select(".meta-data .genre a")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst(".comic-description .inner")
                ?.text()
                ?.ifEmpty { null }
            status = parseStatus(
                document.select(".tsinfo .imptdt")
                    .firstOrNull { it.text().contains("Tình trạng") }
                    ?.selectFirst("i")
                    ?.text(),
            )
            thumbnail_url = document.selectFirst(".comic-info .book img")?.absUrl("src")
        }
    }

    private fun parseStatus(statusText: String?): Int {
        val normalized = statusText?.lowercase(Locale.ROOT)

        return when {
            normalized == null -> SManga.UNKNOWN
            "hoàn thành" in normalized -> SManga.COMPLETED
            "đang tiến hành" in normalized || "đang cập nhật" in normalized -> SManga.ONGOING
            "tạm ngưng" in normalized || "tạm dừng" in normalized || "hiatus" in normalized -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".chap-list a.d-flex.justify-content-between")
        .map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("span.name")!!.text()
                date_upload = DATE_FORMAT.tryParse(element.select("span").getOrNull(1)?.text())
            }
        }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document.select(".chapter-content img")
            .map { imageElement ->
                imageElement.absUrl("data-src").ifEmpty { imageElement.absUrl("src") }
            }
            .filter { imageUrl ->
                imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
            }
            .ifEmpty {
                document.select(".content-text img")
                    .map { imageElement ->
                        imageElement.absUrl("data-src").ifEmpty { imageElement.absUrl("src") }
                    }
                    .filter { imageUrl ->
                        imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
                    }
            }
            .distinct()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_FORMAT = java.text.SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
