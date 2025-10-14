package eu.kanade.tachiyomi.extension.zh.comicabc

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Comicabc : ParsedHttpSource() {
    override val name: String = "無限動漫"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://www.8comic.com"
    private val chaptersBaseUrl: String = "https://articles.onemoreplace.tw"

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic/h-$page.html", headers)

    override fun popularMangaNextPageSelector(): String = "div.pager a span.mdi-skip-next"

    override fun popularMangaSelector(): String = ".container .row a.comicpic_col6"

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
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

    override fun mangaDetailsParse(document: Document): SManga =
        SManga.create().apply {
            title = document.selectFirst(".item_content_box .h2")!!.text()
            thumbnail_url = document.selectFirst(".item-cover img")!!.attr("abs:src")
            author =
                document
                    .selectFirst(".item_content_box .item-info-author")
                    ?.text()
                    ?.substringAfter("作者: ")
            artist = author
            description = document.selectFirst(".item_content_box .item_info_detail")?.text()
            status =
                when (document.selectFirst(".item_content_box .item-info-status")?.text()) {
                    "連載中" -> SManga.ONGOING
                    "已完結" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
        }

    // Chapters

    override fun chapterListSelector(): String = "#chapters a, .comic_chapters a"

    override fun chapterFromElement(element: Element): SChapter =
        SChapter.create().apply {
            name = element.text()
            val onclick = element.attr("onclick")

            if (onclick.contains("cview")) {
                // Modern path: Parse onclick attribute and build absolute URL to new domain
                val params = onclick.substringAfter("cview('").substringBefore("'")
                val comicId = params.substringBefore("-")
                val chapterIdWithHtml = params.substringAfter("-")
                val chapterId = chapterIdWithHtml.substringBefore(".html")
                url = "$chaptersBaseUrl/online/new-$comicId.html?ch=$chapterId"
            } else {
                // Fallback path for older manga with direct hrefs
                val href = element.attr("href")
                url =
                    when {
                        // if href is a relative path for the new reader, prepend the new domain
                        href.startsWith("/online/") -> "$chaptersBaseUrl$href"
                        // if href is already an absolute path, use it as is
                        href.startsWith("http") -> href
                        // otherwise, it's a relative path for the main domain, let the framework
                        // handle it
                        else -> href
                    }
            }
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    // Pages Request
    override fun pageListRequest(chapter: SChapter): Request {
        val pageListHeaders = headersBuilder().add("Referer", baseUrl).build()
        return GET(chapter.url, pageListHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageUrl = response.request.url.toString()
        val html = response.body.string()

        // Use Regex to find the correct script block.
        // The target script is the one that contains the logic to build the image HTML
        // and insert it into the "#comics-pics" div. This is a stable identifier.
        val scriptRegex = Regex("""<script language="javascript">([\s\S]*?)</script>""")
        val targetScriptContent = scriptRegex.findAll(html)
            .map { it.groupValues[1] }
            .find { it.contains("""$("#comics-pics").html(xx)""") }
            ?: throw Exception("无法找到包含图片数据的脚本")

        val scriptContent = targetScriptContent
            .replace("document.location", "'$pageUrl'")
            .substringBefore("""$("#comics-pics")""")

        // The regex to capture the URL generation logic inside the 's' attribute
        val urlCreationLogic =
            Regex("""s="'\s*\+\s*(.*?)\s*\+\s*'"\s*draggable""")
                .find(scriptContent)
                ?.groupValues
                ?.get(1) ?: throw Exception("无法捕获URL生成逻辑")

        // Prepare the script to be executed in QuickJs
        val scriptToExecute =
            """
            $J_JS_FUNCTIONS
            $scriptContent

            var urls = [];
            for (var j = 1; j <= ps; j++) {
                var s = 'https:' + unescape($urlCreationLogic);
                urls.push(s);
            }
            urls;
            """
                .trimIndent()

        val quickJs = QuickJs.create()
        try {
            val result = quickJs.evaluate(scriptToExecute)
            if (result is Array<*>) {
                return result.mapIndexed { index, url -> Page(index, "", url.toString()) }
            }
        } finally {
            quickJs.close()
        }
        return emptyList()
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder().add("Referer", chaptersBaseUrl).build()
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> =
        throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    companion object {
        // Core functions from j.js, required for the script to run
        const val J_JS_FUNCTIONS =
            """
            function lc(l){if(l.length!=2)return l;var az="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";var a=l.substring(0,1);var b=l.substring(1,2);if(a=="Z")return 8000+az.indexOf(b);else return az.indexOf(a)*52+az.indexOf(b)}
            function su(a,b,c){var e=(a+'').substring(b,b+c);return e}
            function nn(n){return n<10?'00'+n:n<100?'0'+n:n}
            function mm(p){return(parseInt((p-1)/10)%10)+(((p-1)%10)*3)}
        """
    }
}
