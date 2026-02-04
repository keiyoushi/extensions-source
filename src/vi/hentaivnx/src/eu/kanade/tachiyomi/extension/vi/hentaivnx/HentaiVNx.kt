package eu.kanade.tachiyomi.extension.vi.hentaivnx

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class HentaiVNx : ParsedHttpSource() {

    override val name = "HentaiVNx"

    override val baseUrl = "https://www.hentaivnx.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList(SortByList(1)))
    }

    override fun popularMangaSelector(): String = ".items .item"

    override fun popularMangaNextPageSelector(): String = "ul.pagination li:last-child:not(.disabled) a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("h3 a")
            ?: element.selectFirst("a.jtip")
            ?: element.selectFirst(".image a")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = linkElement.attr("title").ifEmpty { linkElement.text() }
        thumbnail_url = element.selectFirst("img")?.let { element ->
            imageElement(element)
        }
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?page=$page", headers)
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // ============================== Search ===============================

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val slug = query.removePrefix(PREFIX_ID_SEARCH).trim()
                val mangaUrl = "/truyen-hentai/$slug"
                fetchMangaDetails(
                    SManga.create().apply { url = mangaUrl },
                ).map { MangasPage(listOf(it), false) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegments("tim-truyen")
                addQueryParameter("keyword", query)
                addQueryParameter("page", page.toString())
            } else {
                addPathSegment("tim-truyen-nang-cao")
                (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                    when (filter) {
                        is GenreFilter -> filter.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_INCLUDE -> addQueryParameter("genres", genre.genre)
                                Filter.TriState.STATE_EXCLUDE -> addQueryParameter("notgenres", genre.genre)
                            }
                        }
                        is ChapterCountList -> addQueryParameter("minchapter", filter.values[filter.state].genre)
                        is SortByList -> addQueryParameter("sort", filter.values[filter.state].genre)
                        is TextField -> addQueryParameter("contain", filter.state)
                        else -> {}
                    }
                }
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(getGenreList()),
        SortByList(0),
        ChapterCountList(),
        TextField(),
    )

    // ============================== Details ===============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.title-detail")!!.text()
        author = document.selectFirst("li.author .col-xs-8")?.text()?.trim()
        description = document.select(".detail-content").joinToString { it.wholeText().trim() }
        genre = document.select("li.kind .col-xs-8 a").joinToString { it.text().trim() }
        thumbnail_url = document.selectFirst(".detail-info .col-image img")?.let { element ->
            imageElement(element)
        }

        val statusText = document.selectFirst("li.status .col-xs-8")?.text()
        status = when {
            statusText == null -> SManga.UNKNOWN
            statusText.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
            statusText.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ===============================

    override fun chapterListSelector(): String = "#nt_listchapter.list-chapter ul li.row"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("div.chapter a")!!.absUrl("href"))
        name = element.selectFirst("div.chapter a")!!.text()
        date_upload = element.selectFirst("div.col-xs-4")?.text().toDate()
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

    private fun imageElement(element: Element): String? {
        return when {
            element.hasAttr("data-original") -> element.attr("abs:data-original")
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            else -> element.attr("abs:src")
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select(".reading-detail img, .page-chapter img")
            .ifEmpty { document.select(".chapter-content img") }

        return images.mapIndexed { idx, element ->
            Page(idx, imageUrl = imageElement(element))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
