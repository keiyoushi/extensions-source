package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TeamLanhLung : HttpSource() {

    override val name: String = "Team Lạnh Lùng"

    override val baseUrl: String = "https://lanhlungteam.com"

    override val lang: String = "vi"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-hot?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(".comic-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a[href]")!!.absUrl("href"))
                title = element.selectFirst("h3.comic-title, .comic-title")!!.text()
                thumbnail_url = element.selectFirst("img")?.let {
                    it.absUrl("data-src").ifEmpty { it.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst("li.next:not(.disabled) a") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-moi-cap-nhat?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = when {
        query.startsWith(PREFIX_ID_SEARCH) -> {
            val slug = query.removePrefix(PREFIX_ID_SEARCH).trim()
            fetchMangaDetails(
                SManga.create().apply {
                    url = "/$slug/"
                },
            ).map {
                it.url = "/$slug/"
                MangasPage(listOf(it), false)
            }
        }

        else -> super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h2.info-title, .info-title")!!.text()
            author = document.selectFirst(".comic-info strong:contains(Tác giả) + span")?.text()
            description = document.selectFirst(".intro-container .text-justify, .intro-container")?.text()
                ?.substringBefore("— Xem Thêm —")
                ?.trim()
            genre = document.select("a[href*='/the-loai/']").joinToString { tag ->
                tag.text().split(' ').joinToString(separator = " ") { word ->
                    word.replaceFirstChar { it.titlecase() }
                }
            }
            thumbnail_url = document.selectFirst(".img-thumbnail")?.let {
                it.absUrl("data-src").ifEmpty { it.absUrl("src") }
            }

            val statusString = document.selectFirst(".comic-info strong:contains(Tình trạng) + span")?.text()
            status = when {
                statusString?.contains("Đang tiến hành", ignoreCase = true) == true -> SManga.ONGOING
                statusString?.contains("Trọn bộ", ignoreCase = true) == true -> SManga.COMPLETED
                statusString?.contains("Hoàn thành", ignoreCase = true) == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".chapter-table table tbody tr").mapNotNull { element ->
            parseChapterElement(element)
        }
    }

    private fun parseChapterElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.text-capitalize") ?: return null
        val url = linkElement.attr("href")
        if (url.isBlank()) return null

        return SChapter.create().apply {
            setUrlWithoutDomain(url)
            val fullText = linkElement.selectFirst("span")?.text()
                ?: linkElement.text()
            name = fullText.split("-", "–").lastOrNull()?.trim() ?: fullText

            date_upload = element.selectFirst("td:last-child")?.text()?.let {
                dateFormat.tryParse(it)
            } ?: 0L
        }
    }

    // ============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val images = document.select("#view-chapter img")
            .ifEmpty { document.select(".chapter-content img, .reading-content img, .content-chapter img") }

        return images.mapIndexed { idx, element ->
            val imageUrl = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            Page(idx, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
