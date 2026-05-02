package eu.kanade.tachiyomi.extension.en.todaymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

open class TodayManga : HttpSource() {

    override val name = "TodayManga"

    override val baseUrl = "https://todaymanga.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category/most-popular".addPage(page), headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("section div.serie").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(".pagination > ul > li.active + li:has(a)") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[href]")!!.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
        title = element.selectFirst("h2")!!.text()
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/recent".addPage(page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("ul.series > li").map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst(".pagination > ul > li.active + li:has(a)") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        with(element.selectFirst("a[title][href]")!!) {
            setUrlWithoutDomain(attr("abs:href"))
            title = attr("title")
        }
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val categoryFilter = filterList.firstInstance<CategoryFilter>()
        val genreFilter = filterList.firstInstance<GenreFilter>()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            when {
                categoryFilter.state != 0 -> {
                    addPathSegment("category")
                    addPathSegments(categoryFilter.toUriPart())
                }
                genreFilter.state != 0 -> {
                    addPathSegment("genre")
                    addPathSegment(genreFilter.toUriPart())
                }
                query.isNotBlank() -> {
                    addPathSegment("search")
                    addQueryParameter("q", query)
                }
                else -> {
                    addPathSegments("category/most-popular")
                }
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("section div.serie")
            .map { popularMangaFromElement(it) }
            .ifEmpty {
                document.select("ul.series > li")
                    .map { latestUpdatesFromElement(it) }
            }

        val hasNextPage = document.selectFirst(".pagination > ul > li.active + li:has(a)") != null
        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Ignored when using text search"),
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        CategoryFilter(),
        GenreFilter(),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            with(document.selectFirst(".serie")!!) {
                title = selectFirst("h1")!!.text()
                thumbnail_url = selectFirst("img")!!.imgAttr()
                genre = select(".serie-info-head .tags > .tag-item").joinToString { it.text() }
                author = select(".authors a").joinToString { it.text() }
                status = selectFirst("li:contains(status) span").parseStatus()
            }

            description = buildString {
                val summary = document.selectFirst(".serie-summary")!!
                summary.childNodes().forEach { node ->
                    if (node is TextNode) append(node.text())
                    if (node.nodeName() == "br") appendLine()
                }
                summary.selectFirst("div[style]")?.also {
                    append("\n\n")
                    append(it.text())
                }
            }.trim()
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "complete" -> SManga.COMPLETED
        "on going" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val chapterHeaders = headersBuilder().apply {
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            set("Referer", baseUrl + manga.url)
        }.build()
        return GET("$baseUrl${manga.url}/chapter-list", chapterHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("ul.chapters-list > li").map { chapterFromElement(it) }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        with(element.selectFirst("a")!!) {
            name = text()
            setUrlWithoutDomain(attr("abs:href"))
        }

        val dateText = element.selectFirst(".subtitle")?.text()
        date_upload = when {
            dateText == null -> 0L
            dateText.contains("ago") -> dateText.parseRelativeDate()
            else -> dateFormat.tryParse(dateText)
        }
    }

    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val relativeDate = this.split(" ").firstOrNull()
            ?.replace("one", "1")
            ?.replace("a", "1")
            ?.toIntOrNull()
            ?: return 0L

        when {
            "second" in this -> now.add(Calendar.SECOND, -relativeDate)
            "minute" in this -> now.add(Calendar.MINUTE, -relativeDate)
            "hour" in this -> now.add(Calendar.HOUR, -relativeDate)
            "day" in this -> now.add(Calendar.DAY_OF_YEAR, -relativeDate)
            "week" in this -> now.add(Calendar.WEEK_OF_YEAR, -relativeDate)
            "month" in this -> now.add(Calendar.MONTH, -relativeDate)
            "year" in this -> now.add(Calendar.YEAR, -relativeDate)
        }
        return now.timeInMillis
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select(".chapter-content > img[data-index]").map { img ->
        Page(img.attr("data-index").toInt(), imageUrl = img.imgAttr())
    }.sortedBy { it.index }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    private fun String.addPage(page: Int): HttpUrl = toHttpUrl().newBuilder().apply {
        if (page > 1) addQueryParameter("page", page.toString())
    }.build()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
