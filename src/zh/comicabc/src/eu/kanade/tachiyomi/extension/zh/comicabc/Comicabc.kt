package eu.kanade.tachiyomi.extension.zh.comicabc

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Comicabc : HttpSource() {
    override val supportsLatest: Boolean = true
    private val chaptersBaseUrl: String = "https://articles.onemoreplace.tw"

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/comic/h-$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container .row a.comicpic_col6").map { element ->
            SManga.create().apply {
                title = element.selectFirst("li.nowraphide")!!.text()
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.pager a span.mdi-skip-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comic/u-$page.html", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container .row .cat2_list a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("li.nowraphide")!!.text()
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.pager a span.mdi-skip-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/member/search.aspx".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst(".item_content_box .h2")!!.text()
        thumbnail_url = document.selectFirst(".item-cover img")?.absUrl("src")
        author = document.selectFirst(".item_content_box .item-info-author")?.text()?.substringAfter("作者: ")
        artist = author
        description = document.selectFirst(".item_content_box .item_info_detail")?.text()
        status = when (document.selectFirst(".item_content_box .item-info-status")?.text()) {
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapters a, .comic_chapters a").map { element ->
            SChapter.create().apply {
                name = element.text()
                val onclick = element.attr("onclick")

                if (onclick.contains("cview")) {
                    val params = onclick.substringAfter("cview('").substringBefore("'")
                    val comicId = params.substringBefore("-")
                    val chapterIdWithHtml = params.substringAfter("-")
                    val chapterId = chapterIdWithHtml.substringBefore(".html")
                    url = "$chaptersBaseUrl/online/new-$comicId.html?ch=$chapterId"
                } else {
                    val href = element.attr("href")
                    url = when {
                        href.startsWith("/online/") -> "$chaptersBaseUrl$href"
                        href.startsWith("http") -> href
                        else -> element.absUrl("href")
                    }
                }
            }
        }.reversed()
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val pageListHeaders = headersBuilder().add("Referer", "$baseUrl/").build()
        return GET(chapter.url, pageListHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageUrl = response.request.url.toString()
        val html = response.body.string()

        val targetScriptContent = scriptRegex.findAll(html)
            .map { it.groupValues[1] }
            .find { it.contains("""$("#comics-pics").html(xx)""") }
            ?: throw Exception("无法找到包含图片数据的脚本")

        val scriptContent = targetScriptContent
            .replace("document.location", "'$pageUrl'")
            .substringBefore("""$("#comics-pics")""")

        val urlCreationLogic = urlCreationLogicRegex.find(scriptContent)
            ?.groupValues
            ?.get(1) ?: throw Exception("无法捕获URL生成逻辑")

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
            """.trimIndent()

        val quickJs = QuickJs.create()
        quickJs.use { quickJs ->
            val result = quickJs.evaluate(scriptToExecute)
            if (result is Array<*>) {
                return result.mapIndexed { index, url -> Page(index, imageUrl = url.toString()) }
            }
        }
        return emptyList()
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder().add("Referer", "$chaptersBaseUrl/").build()
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val scriptRegex = Regex("""<script language="javascript">([\s\S]*?)</script>""")
        private val urlCreationLogicRegex = Regex("""s="'\s*\+\s*(.*?)\s*\+\s*'"\s*draggable""")

        // Core functions from j.js, required for the script to run
        private const val J_JS_FUNCTIONS =
            """
            function lc(l){if(l.length!=2)return l;var az="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";var a=l.substring(0,1);var b=l.substring(1,2);if(a=="Z")return 8000+az.indexOf(b);else return az.indexOf(a)*52+az.indexOf(b)}
            function su(a,b,c){var e=(a+'').substring(b,b+c);return e}
            function nn(n){return n<10?'00'+n:n<100?'0'+n:n}
            function mm(p){return(parseInt((p-1)/10)%10)+(((p-1)%10)*3)}
            """
    }
}
