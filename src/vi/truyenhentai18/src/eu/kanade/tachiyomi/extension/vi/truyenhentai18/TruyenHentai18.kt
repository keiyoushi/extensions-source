package eu.kanade.tachiyomi.extension.vi.truyenhentai18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class TruyenHentai18 : ParsedHttpSource() {

    override val name = "Truyện Hentai 18+"

    override val baseUrl = "https://truyenhentai18.pro"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/truyen-de-xuat" + if (page > 1) "/page/$page" else "", headers)

    override fun popularMangaSelector() = "div.row > div[class^=item-] > div.card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.item-title")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }

        thumbnail_url = element.selectFirst("a.item-cover img")?.absUrl("data-src")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination li.page-item.active:not(:last-child)"

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/truyen-moi" + if (page > 1) "/page/$page" else "", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            val url = "/$slug"

            fetchMangaDetails(SManga.create().apply { this.url = url })
                .map { MangasPage(listOf(it.apply { this.url = url }), false) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }

            addQueryParameter("s", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div[data-id] > div.card"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val statusClassName = document.selectFirst("em.eflag.item-flag")!!.className()

        title = document.selectFirst("span[itemprop=name]")!!.text()
        author = document.select("div.attr-item b:contains(Tác giả) ~ span a, span[itemprop=author]").joinToString { it.text() }
        description = document.selectFirst("div[itemprop=about]")?.text()
        genre = document.select("ul.post-categories li a").joinToString { it.text() }
        thumbnail_url = document.selectFirst("div.attr-cover img")?.absUrl("src")
        status = when {
            statusClassName.contains("flag-completed") -> SManga.COMPLETED
            statusClassName.contains("flag-ongoing") -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = "#chaptersbox > div"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            name = it.selectFirst("b")!!.text()
        }

        date_upload = element.selectFirst("div.extra > i.ps-3")
            ?.text()
            ?.let { parseRelativeDate(it) }
            ?: 0L
    }

    override fun pageListParse(document: Document) =
        document.select("#viewer img").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun parseRelativeDate(date: String): Long {
        val (valueString, unit) = date.substringBefore(" trước").split(" ")
        val value = valueString.toInt()

        val calendar = Calendar.getInstance().apply {
            when (unit) {
                "giây" -> add(Calendar.SECOND, -value)
                "phút" -> add(Calendar.MINUTE, -value)
                "giờ" -> add(Calendar.HOUR_OF_DAY, -value)
                "ngày" -> add(Calendar.DAY_OF_MONTH, -value)
                "tuần" -> add(Calendar.WEEK_OF_MONTH, -value)
                "tháng" -> add(Calendar.MONTH, -value)
                "năm" -> add(Calendar.YEAR, -value)
            }
        }

        return calendar.timeInMillis
    }

    companion object {
        internal const val PREFIX_SLUG_SEARCH = "slug:"
    }
}
