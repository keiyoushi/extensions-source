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
    override val baseUrl: String = "https://www.8comic.com"

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic/h-$page.html", headers)
    override fun popularMangaNextPageSelector(): String = "div.pager a span.mdi-skip-next"
    override fun popularMangaSelector(): String = ".container .row a.comicpic_col6"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("li.nowraphide")!!.text()
        setUrlWithoutDomain(element.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comic/u-$page.html", headers)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = ".container .row .cat2_list a"
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
        title = document.selectFirst(".item_content_box .h2")!!.text()
        thumbnail_url = document.selectFirst(".item-cover img")!!.attr("abs:src")
        author = document.selectFirst(".item_content_box .item-info-author")?.text()?.substringAfter("作者: ")
        artist = author
        description = document.selectFirst(".item_content_box .item_info_detail")?.text()
        status = when (document.selectFirst(".item_content_box .item-info-status")?.text()) {
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapters

    override fun chapterListSelector(): String = "#chapters a"
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

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val url = response.request.url.toString()
        val script = document.selectFirst("script:containsData(function request)")!!.data()
            .replace("document.location", "\"$url\"")
            .replace("\$(\"#comics-pics\").html(xx);", "")
            .substringBefore("\$(\"#pt,#ptb\")")
        val quickJs = QuickJs.create()
        val variableName = script.substringAfter("img  s=\"").substringBefore("'")
        val images = quickJs.evaluate(nview + script + lazyloadx.format(variableName)) as Array<*>
        quickJs.close()
        return images.mapIndexed { index, it ->
            Page(index, "", it.toString())
        }
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException()
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    companion object {
        // Functions required by script in pageListParse()
        // Taken from https://www.8comic.com/js/j.js?9989588541
        const val nview = """function lc(l){if(l.length!=2 ) return l;var az="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";var a=l.substring(0,1);var b=l.substring(1,2);if(a=="Z") return 8000+az.indexOf(b);else return az.indexOf(a)*52+az.indexOf(b);}
function nn(n){return n<10?'00'+n:n<100?'0'+n:n;}function mm(p){return (parseInt((p-1)/10)%10)+(((p-1)%10)*3)};
function su(a,b,c){var e=(a+'').substring(b,b+c);return (e);}var y=46;"""

        // Modified from https://www.8comic.com/js/lazyloadx.js?9989588541
        const val lazyloadx = """src="%s"
var b=eval(src.substring(0,5));
var c=eval(src.substring(5,10));
var d=eval(src.substring(10,15));
var arr=[];
for(var i=1;i<=ps;i++){
    arr.push('https://img'+su(b,0,1)+'.8comic.com/'+su(b,1,1)+'/' + ti  + '/'+c+'/' + nn(i) + '_' + su(d,mm(i),3) + '.jpg');
}
arr"""
    }
}
