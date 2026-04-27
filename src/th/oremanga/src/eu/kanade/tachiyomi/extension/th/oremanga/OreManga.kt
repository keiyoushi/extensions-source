package eu.kanade.tachiyomi.extension.th.oremanga

import eu.kanade.tachiyomi.network.GET
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
import java.text.SimpleDateFormat
import java.util.Locale

class OreManga : HttpSource() {

    override val name = "OreManga"

    override val baseUrl = "https://www.oremanga.net"

    override val lang = "th"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)

    private val thaiMonths = listOf(
        "มกราคม" to "January",
        "กุมภาพันธ์" to "February",
        "มีนาคม" to "March",
        "เมษายน" to "April",
        "พฤษภาคม" to "May",
        "มิถุนายน" to "June",
        "กรกฎาคม" to "July",
        "สิงหาคม" to "August",
        "กันยายน" to "September",
        "ตุลาคม" to "October",
        "พฤศจิกายน" to "November",
        "ธันวาคม" to "December",
    )

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/advance-search/page/$page/?order=popular", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".flexbox2-item").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                title = element.selectFirst(".flexbox2-title span")?.text() ?: return@mapNotNull null
                thumbnail_url = element.selectFirst(".flexbox2-thumb img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst(".pagination a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/advance-search/page/$page/?order=update", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/advance-search/page/$page/".toHttpUrl().newBuilder().apply {
            addQueryParameter("title", query)

            filters.firstInstanceOrNull<AuthorFilter>()?.state?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("author", it)
            }
            filters.firstInstanceOrNull<YearFilter>()?.state?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("yearx", it)
            }
            filters.firstInstanceOrNull<StatusFilter>()?.let {
                addQueryParameter("status", it.toUriPart())
            }
            filters.firstInstanceOrNull<TypeFilter>()?.let {
                addQueryParameter("type", it.toUriPart())
            }
            filters.firstInstanceOrNull<OrderFilter>()?.let {
                addQueryParameter("order", it.toUriPart())
            }
            filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.forEach {
                addQueryParameter("genre[]", it.id)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".series-title h2")?.text()
                ?: throw Exception("Failed to parse title")

            thumbnail_url = document.selectFirst(".series-thumb img")?.absUrl("src")
            description = document.select(".series-synops p").text()
            genre = document.select(".series-genres a").joinToString { it.text() }
            author = document.selectFirst(".series-infolist li:has(b:contains(Author)) span")?.text()

            val statusText = document.selectFirst(".series-infoz.block .status")?.text()
            status = when (statusText?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".series-chapterlist li").mapNotNull { element ->
            val a = element.selectFirst(".flexch-infoz a") ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                name = a.selectFirst("span")?.ownText() ?: a.text()

                val dateStr = element.selectFirst(".date")?.text()
                date_upload = if (!dateStr.isNullOrEmpty()) parseChapterDate(dateStr) else 0L
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".reader-area-main img, .reader-area-main canvas").mapIndexed { i, element ->
            val url = if (element.tagName() == "canvas") {
                element.absUrl("data-url")
            } else {
                element.absUrl("src")
            }
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        AuthorFilter(),
        YearFilter(),
        StatusFilter(),
        TypeFilter(),
        OrderFilter(),
        GenreFilter(getGenreList()),
    )

    private fun parseChapterDate(dateStr: String): Long {
        var dateEn = dateStr
        for ((thai, eng) in thaiMonths) {
            if (dateEn.contains(thai)) {
                dateEn = dateEn.replace(thai, eng)
                break
            }
        }
        return dateFormat.tryParse(dateEn)
    }
}
