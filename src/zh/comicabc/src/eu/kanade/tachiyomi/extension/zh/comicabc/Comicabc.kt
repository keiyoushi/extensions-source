package eu.kanade.tachiyomi.extension.zh.comicabc

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Comicabc : ParsedHttpSource() {
    override val name: String = "無限動漫"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://www.comicabc.com"

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic/h-$page.html", headers)
    override fun popularMangaNextPageSelector(): String = "div.pager a span.mdi-skip-next"
    override fun popularMangaSelector(): String = "div.default_row_width > div.col-2"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("li.cat2_list_name")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comic/u-$page.html", headers)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/member/search.aspx?key=$query&page=$page", headers)
    }

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.item-top-content h3.item_name")!!.text()
        thumbnail_url = document.selectFirst("div.item-topbar img.item_cover")!!.attr("abs:src")
        author = document.selectFirst("div.item-top-content > li:nth-of-type(3)")!!.ownText()
        artist = author
        description = document.selectFirst("div.item-top-content > li.item_info_detail")!!.text()
        status = when {
            document.selectFirst("div.item_comic_eps_div")!!.text().contains("連載中") -> SManga.ONGOING
            document.selectFirst("div.item_comic_eps_div")!!.text().contains("已完結") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapters

    override fun chapterListSelector(): String = "div#div_li1 td > a"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val onclick = element.attr("onclick")
        val comicId = onclick.substringAfter("cview('").substringBefore("-")
        val chapterId = onclick.substringAfter("-").substringBefore(".html")
        url = "/online/new-$comicId.html?ch=$chapterId"
        name = element.text()
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val document = response.asJsoup()
        val url = response.request.url.toString()
        val script = document.selectFirst("script:containsData(function request)")!!.data()
            .replace("function ge(e){return document.getElementById(e);}", "")
            .replace("ge\\(.*\\).src".toRegex(), "imageUrl")
            .replace("spp()", "")
        val quickJs = QuickJs.create()
        val totalPage = quickJs.evaluate(nview + script.replace("document.location", "\"$url\"") + "ps") as Int
        for (i in 1..totalPage) {
            val imageUrl = quickJs.evaluate(nview + script.replace("document.location", "\"$url-$i\"") + "imageUrl") as String
            add(Page(i - 1, "", "https:$imageUrl"))
        }
        quickJs.close()
    }

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not Used")
    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")

    companion object {
        // Functions required by script in pageListParse()
        // Taken from https://www.comicabc.com/js/nview.js?20180806
        const val nview = """function lc(l){if(l.length!=2 ) return l;var az="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";var a=l.substring(0,1);var b=l.substring(1,2);if(a=="Z") return 8000+az.indexOf(b);else return az.indexOf(a)*52+az.indexOf(b);}
function nn(n){return n<10?'00'+n:n<100?'0'+n:n;}function mm(p){return (parseInt((p-1)/10)%10)+(((p-1)%10)*3)};"""
    }
}
