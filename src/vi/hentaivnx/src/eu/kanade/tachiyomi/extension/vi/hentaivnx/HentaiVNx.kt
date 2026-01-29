package eu.kanade.tachiyomi.extension.vi.hentaivnx

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class HentaiVNx : HttpSource() {

    override val name = "HentaiVNx"

    override val baseUrl = "https://www.hentaivnx.com"

    override val lang = "vi"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/tim-truyen-nang-cao?genres=&notgenres=&minchapter=0&sort=10&contain=&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(".items .item").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("h3 a")
                    ?: element.selectFirst("a.jtip")
                    ?: element.selectFirst(".image a")!!
                setUrlWithoutDomain(linkElement.absUrl("href"))
                title = linkElement.attr("title").ifEmpty { linkElement.text() }
                thumbnail_url = element.selectFirst("img")?.let {
                    it.absUrl("data-original")
                        .ifEmpty { it.absUrl("data-src") }
                        .ifEmpty { it.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination li:last-child:not(.disabled) a") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val slug = query.removePrefix(PREFIX_ID_SEARCH).trim()
                fetchMangaDetails(
                    SManga.create().apply {
                        url = "/truyen-hentai/$slug"
                    },
                ).map {
                    it.url = "/truyen-hentai/$slug"
                    MangasPage(listOf(it), false)
                }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/tim-truyen".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("page", page.toString())
                .build()
        } else {
            var genreSlug = ""
            filters.forEach { filter ->
                if (filter is GenreFilter) {
                    val genres = getGenreList()
                    genreSlug = genres[filter.state].second
                }
            }
            if (genreSlug.isNotEmpty()) {
                "$baseUrl/tim-truyen/$genreSlug?page=$page".toHttpUrl()
            } else {
                "$baseUrl/tim-truyen?page=$page".toHttpUrl()
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(getGenreList()),
    )

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.title-detail")!!.text()
            author = document.selectFirst("li.author .col-xs-8")?.text()?.trim()
            description = document.selectFirst(".detail-content")?.text()?.trim()
            genre = document.select("li.kind .col-xs-8 a").joinToString { it.text().trim() }
            thumbnail_url = document.selectFirst(".detail-info .col-image img")?.let {
                it.absUrl("data-original")
                    .ifEmpty { it.absUrl("data-src") }
                    .ifEmpty { it.absUrl("src") }
            }

            val statusText = document.selectFirst("li.status .col-xs-8")?.text()
            status = when {
                statusText?.contains("Đang tiến hành", ignoreCase = true) == true -> SManga.ONGOING
                statusText?.contains("Hoàn thành", ignoreCase = true) == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("#nt_listchapter ul li.row, .list-chapter ul li.row").mapNotNull { element ->
            parseChapterElement(element)
        }
    }

    private fun parseChapterElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("div.chapter a") ?: return null
        val url = linkElement.attr("href")
        if (url.isBlank()) return null

        return SChapter.create().apply {
            setUrlWithoutDomain(url)
            name = linkElement.text().trim()
            date_upload = element.selectFirst("div.col-xs-4")?.text().toDate()
        }
    }

    private fun String?.toDate(): Long {
        this ?: return 0L

        if (!this.contains("trước", ignoreCase = true)) {
            return 0L
        }

        return try {
            val calendar = Calendar.getInstance()

            val patterns = listOf(
                Regex("""(\d+)\s*giờ""", RegexOption.IGNORE_CASE) to Calendar.HOUR_OF_DAY,
                Regex("""(\d+)\s*ngày""", RegexOption.IGNORE_CASE) to Calendar.DAY_OF_MONTH,
                Regex("""(\d+)\s*tuần""", RegexOption.IGNORE_CASE) to Calendar.WEEK_OF_YEAR,
                Regex("""(\d+)\s*tháng""", RegexOption.IGNORE_CASE) to Calendar.MONTH,
                Regex("""(\d+)\s*năm""", RegexOption.IGNORE_CASE) to Calendar.YEAR,
                Regex("""(\d+)\s*phút""", RegexOption.IGNORE_CASE) to Calendar.MINUTE,
                Regex("""(\d+)\s*giây""", RegexOption.IGNORE_CASE) to Calendar.SECOND,
            )

            for ((pattern, field) in patterns) {
                pattern.find(this)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
                    calendar.add(field, -number)
                    return calendar.timeInMillis
                }
            }

            0L
        } catch (_: Exception) {
            0L
        }
    }

    // ============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val images = document.select(".reading-detail img, .page-chapter img")
            .ifEmpty { document.select(".chapter-content img") }

        return images.mapIndexed { idx, element ->
            val imageUrl = element.attr("data-src").ifEmpty {
                element.attr("data-original").ifEmpty {
                    element.attr("src")
                }
            }
            Page(idx, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
