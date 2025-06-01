package eu.kanade.tachiyomi.extension.zh.yymanhua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class YYmanhua : ParsedHttpSource() {

    override val baseUrl = "https://www.yymanhua.com"
    override val lang = "zh"
    override val name = "YY漫画"
    override val supportsLatest = true

    companion object {
        val DESC_REGEX = Regex("\\[\\+展开]|\\[-折叠]")
        val CHAPTER_REGEX = Regex("第(\\d+(?:\\.\\d+)?)[话話]")
        val NUM_REGEX = Regex("\\d+")
        val IMG_REGEX = Regex("var pix=\"(.*?)\".*?var pvalue=\\[\"(.*?)\"")
        val DECODE_REGEX1 = Regex("return p;\\}\\('(.*?)',(\\d+),(\\d+),'(.*?)'")
        val DECODE_REGEX2 = Regex("\\b\\w+\\b")
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edg/136.0.0.0")
        .add("Referer", "$baseUrl/")
        .add("Cookie", "yymanhua_lang=2")

    // Popular Page

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list-p$page", headers)

    override fun popularMangaSelector() = ".mh-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(".mh-item-detali .title a")!!.text()
        element.selectFirst("a:nth-child(1)")!!.let {
            thumbnail_url = it.selectFirst("img")?.absUrl("src")
            this.setUrlWithoutDomain(it.absUrl("href"))
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

    override fun getFilterList() = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegment("search")
                .addQueryParameter("title", query)
                .addQueryParameter("page", page.toString())
        } else {
            url.addPathSegment("manga-list-${filters[1]}-${filters[2]}-${filters[3]}-p$page")
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga Detail Page

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.selectFirst(".detail-info-content")?.text()
            ?.replace(DESC_REGEX, "")?.trim()
        val els = document.select(".detail-info-tip > span")
        els[0].select("a").let {
            author = it[0].text()
            artist = it.getOrNull(1)?.text()
        }
        status = when (els[1].selectFirst("span > span")?.text()) {
            "连载中", "連載中" -> SManga.ONGOING
            "已完结", "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = els[2].select(".item").joinToString { it.text() }
        initialized = true
    }

    // Manga Detail Page / Chapters Page (Separate)

    // override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun chapterListSelector() = "#chapterlistload a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        this.setUrlWithoutDomain(element.absUrl("href"))
        name = element.text()
        chapter_number = CHAPTER_REGEX.find(name)?.groups?.get(1)?.value?.toFloat() ?: -1F
    }

    // Manga View Page

    override fun pageListParse(document: Document): List<Page> {
        val cid = NUM_REGEX.find(document.location())?.groups?.get(0)?.value
        return List(document.select(".reader-bottom-page-list a").size.takeIf { it > 0 } ?: 1) { i ->
            Page(i, "${document.location()}chapterimage.ashx?cid=$cid&page=${i + 1}")
        }
    }

    // Image

    // override fun imageRequest(page: Page) = GET(page.url, headers)

    override fun imageUrlParse(response: Response): String {
        val (_, pix, pvalue) = IMG_REGEX.find(decode(response.body.string()))?.groupValues!!
        return pix + pvalue
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun decode(ciphertext: String): String {
        val groups = DECODE_REGEX1.find(ciphertext)?.groupValues!!
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
        return DECODE_REGEX2.replace(groups[1]) { result -> d[result.value] ?: result.value }
    }
}
