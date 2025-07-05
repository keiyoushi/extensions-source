package eu.kanade.tachiyomi.extension.zh.bilimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class BiliManga : ParsedHttpSource() {

    override val baseUrl = "https://www.bilimanga.net"
    override val lang = "zh"
    override val name = "嗶哩漫畫"
    override val supportsLatest = true
    override val client = super.client.newBuilder().addNetworkInterceptor(MangaDetailInterceptor()).build()
    private val SManga.id get() = MANGA_ID_REGEX.find(url)!!.groups[1]!!.value

    companion object {
        const val PAGE_SIZE = 50
        val META_REGEX = Regex("連載|完結|收藏|推薦|热度")
        val DATE_REGEX = Regex("\\d{4}-\\d{1,2}-\\d{1,2}")
        val MANGA_ID_REGEX = Regex("/detail/(\\d+)\\.html")
        val CHAPTER_ID_REGEX = Regex("/read/(\\d+)/(\\d+)\\.html")
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "zh")
        .add("Accept", "*/*")
        .add("Cookie", "night=0")

    // Popular Page

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top/weekvisit/$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".book-layout").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                // author = it.selectFirst(".book-author")?.text()
                it.selectFirst("img")!!.let {
                    thumbnail_url = it.absUrl("data-src")
                    title = it.attr("alt")
                }
            }
        }
        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    override fun popularMangaSelector() = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/top/lastupdate/$page.html", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    // Search Page

    // override fun getFilterList() = buildFilterList()

    // override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
    //     val url = baseUrl.toHttpUrl().newBuilder()
    //     if (query.isNotBlank()) {
    //         url.addPathSegment("search")
    //             .addQueryParameter("title", query)
    //             .addQueryParameter("page", page.toString())
    //     } else {
    //         url.addPathSegment("manga-list-${filters[1]}-${filters[2]}-${filters[3]}-p$page")
    //     }
    //     return GET(url.build(), headers)
    // }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().addPathSegment("${query}_$page.html")
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("detail")) {
            return MangasPage(listOf(mangaDetailsParse(response)), false)
        }
        return popularMangaParse(response)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    // Manga Detail Page

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val meta = document.selectFirst(".book-meta")!!.text().split("|")
        val extra = meta.filterNot(META_REGEX::containsMatchIn)
        url = document.location()
        title = document.selectFirst(".book-title")!!.text()
        thumbnail_url = document.selectFirst(".book-cover")!!.attr("src")
        description = document.selectFirst("#bookSummary")?.text()
        artist = document.selectFirst(".authorname")?.text()
        author = document.selectFirst(".illname")?.text() ?: artist
        status = when (meta.firstOrNull()) {
            "連載" -> SManga.ONGOING
            "完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = (document.select(".tag-small").map(Element::text) + extra).joinToString()
        initialized = true
    }

    // Catalog Page

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/read/${manga.id}/catalog", headers)

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val info = document.selectFirst(".chapter-sub-title")!!.text()
        val date = DATE_FORMAT.tryParse(DATE_REGEX.find(info)?.value)
        val elements = document.select(".chapter-li-a")
        return elements.mapIndexed { i, e ->
            val url = e.absUrl("href").takeUnless("javascript:cid(1)"::equals)
            SChapter.create().apply {
                name = e.text()
                date_upload = date
                if (url == null) scanlator = "预测章节"
                setUrlWithoutDomain(url ?: predictChapterUrlByContext(i, elements))
            }
        }.reversed()
    }

    // Manga View Page

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select(".imagecontent")
        require(images.size > 0) { document.selectFirst("#acontentz")?.text() ?: "章节地址错误" }
        return images.mapIndexed { i, it ->
            Page(i, document.location(), it.attr("data-src"))
        }
    }

    // Image

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun predictChapterUrlByContext(i: Int, els: Elements): String {
        val e = els.getOrNull(i - 1) ?: els.getOrNull(i + 1)
        if (e != null) {
            val groups = CHAPTER_ID_REGEX.find(e.attr("href"))!!.groups
            return "/read/${groups[1]?.value}/${groups[2]?.value?.toInt()?.plus(1)}.html"
        }
        return "/read/0/0.html"
    }
}
