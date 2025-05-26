package eu.kanade.tachiyomi.extension.zh.yymanhua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class YYmanhua : ParsedHttpSource() {

    override val baseUrl = "https://www.yymanhua.com"
    override val lang = "zh"
    override val name = "YY漫画"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)
        .add("Cookie", "yymanhua_lang=2")

    override val client = network.cloudflareClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Popular Page

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list-p$page", headers)

    override fun popularMangaSelector() = ".mh-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.select(".mh-item-detali .title a").text()
        element.select("a:nth-child(1)").let {
            thumbnail_url = it.select("img").attr("src")
            url = it.attr("href")
        }
    }

    // override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaNextPageSelector() = ".page-pagination li:has(.active) + li a"

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list-0-0-2-p$page/", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search Page

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/search?title=$query&page=$page")

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga Detail Page

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select(".detail-info-content").text()
            .replace(Regex("\\[\\+展开]|\\[-折叠]"), "").trim()
        val els = document.select(".detail-info-tip > span")
        els[0].select("a").let {
            author = it[0].text()
            artist = it.getOrNull(1)?.text()
        }
        status = when (els[1].select("span")[1].text()) {
            "连载中", "連載中" -> SManga.ONGOING
            "已完结", "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = els[2].select(".item").joinToString { it.text() }
        initialized = true
    }

    // Manga Detail Page / Chapters Page (Separate)

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun chapterListSelector() = "#chapterlistload a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        url = element.attr("href")
        val regex = Regex("第(\\d+(?:\\.\\d+)?)[话話]")
        name = element.text()
        chapter_number = regex.find(name)?.groups?.get(1)?.value?.toFloat() ?: -0F
    }

    // Manga View Page

    override fun pageListParse(document: Document): List<Page> {
        val cid = Regex("\\d+").find(document.location())?.groups?.get(0)?.value
        return List(document.select(".reader-bottom-page-list a").size.takeIf { it > 0 } ?: 1) { i ->
            Page(i, "${document.location()}chapterimage.ashx?cid=$cid&page=${i + 1}")
        }
    }

    // Image

    // override fun imageRequest(page: Page) = GET(page.url, headers)

    override fun imageUrlParse(response: Response): String {
        val (_, pix, pvalue) = Regex("var pix=\"(.*?)\".*?var pvalue=\\[\"(.*?)\"")
            .find(decode(response.body.string()))?.groupValues!!
        return pix + pvalue
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun decode(ciphertext: String): String {
        val groups = Regex("return p;\\}\\('(.*?)',(\\d+),(\\d+),'(.*?)'")
            .find(ciphertext)?.groupValues!!
        val d = mutableMapOf<String, String>()
        val parts = groups[4].split("|")
        val e = { c: Int ->
            val mod = c % groups[2].toInt()
            if (mod > 35) (mod + 29).toChar().toString() else mod.toString(36)
        }
        var counter = groups[3].toInt()
        while (counter-- > 0) {
            val i = e(counter)
            d[i] = parts.getOrNull(counter)?.takeIf(String::isNotEmpty) ?: i
        }
        return Regex("\\b\\w+\\b").replace(groups[1]) { result -> d[result.value] ?: result.value }
    }

    override fun getFilterList() = FilterList()
}
