package eu.kanade.tachiyomi.extension.en.weebcentral

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class WeebCentral : ParsedHttpSource() {

    override val name = "Weeb Central"

    override val baseUrl = "https://weebcentral.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(
        page,
        "",
        defaultFilterList(SortFilter("Popularity")),
    )

    override fun popularMangaSelector(): String = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String = searchMangaNextPageSelector()

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(
        page,
        "",
        defaultFilterList(SortFilter("Latest Updates")),
    )

    override fun latestUpdatesSelector(): String = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = searchMangaNextPageSelector()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
        val url = "$baseUrl/search/data".toHttpUrl().newBuilder().apply {
            addQueryParameter("text", query)
            filterList.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
            addQueryParameter("limit", FETCH_LIMIT.toString())
            addQueryParameter("offset", ((page - 1) * FETCH_LIMIT).toString())
            addQueryParameter("display_mode", "Full Display")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = "article:has(section)"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
        with(element.selectFirst("div > a")!!) {
            title = text()
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    override fun searchMangaNextPageSelector(): String = "button"

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = defaultFilterList(SortFilter())

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        with(document.select("section[x-data] > section")[0]) {
            thumbnail_url = selectFirst("img")!!.attr("abs:src")
            author = select("ul > li:has(strong:contains(Author)) > span > a").joinToString { it.text() }
            genre = select("ul > li:has(strong:contains(Tag)) > span > a").joinToString { it.text() }
            status = selectFirst("ul > li:has(strong:contains(Status)) > a").parseStatus()
        }

        with(document.select("section[x-data] > section")[1]) {
            title = selectFirst("h1")!!.text()
            description = selectFirst("li:has(strong:contains(Description)) > p")?.text()
                ?.replace("NOTE: ", "\n\nNOTE: ")
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "complete" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val url = (baseUrl + manga.url).toHttpUrl().newBuilder().apply {
            removePathSegment(2)
            addPathSegment("full-chapter-list")
        }.build()

        return GET(url, headers)
    }

    override fun chapterListSelector() = "a[x-data]"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst("span.flex > span")!!.text()
        setUrlWithoutDomain(element.attr("abs:href"))
        element.selectFirst("time[datetime]")?.also {
            date_upload = it.attr("datetime").parseDate()
        }
    }

    private fun String.parseDate(): Long {
        return try {
            dateFormat.parse(this)!!.time
        } catch (_: ParseException) {
            0L
        }
    }
    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val newUrl = (baseUrl + chapter.url)
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("images")
            ?.addQueryParameter("is_prev", "False")
            ?.addQueryParameter("reading_style", "long_strip")
            ?.build()
            ?.toString()
            ?: (baseUrl + chapter.url)
        return GET(newUrl, headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("section[x-data~=scroll] > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    private fun defaultFilterList(sortFilter: SortFilter): FilterList = FilterList(
        sortFilter,
        SortOrderFilter(),
        OfficialTranslationFilter(),
        StatusFilter(),
        TypeFilter(),
        TagFilter(),
    )

    companion object {
        const val FETCH_LIMIT = 24
    }
}
