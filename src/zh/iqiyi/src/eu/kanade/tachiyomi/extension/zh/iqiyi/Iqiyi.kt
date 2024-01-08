package eu.kanade.tachiyomi.extension.zh.iqiyi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class Iqiyi : ParsedHttpSource() {
    override val name: String = "爱奇艺叭嗒"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://www.iqiyi.com/manhua"
    private val json: Json by injectLazy()

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/全部_0_9_$page/", headers)
    override fun popularMangaNextPageSelector(): String = "div.mod-page > a.a1:contains(下一页)"
    override fun popularMangaSelector(): String = "ul.cartoon-hot-ul > li.cartoon-hot-list"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("a.cartoon-item-tit")!!.text()
        url = element.selectFirst("a.cartoon-item-tit")!!.attr("href").drop(7)
        thumbnail_url = element.selectFirst("img")!!.attr("src")
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/全部_0_4_$page/", headers)
    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search-keyword=${query}_$page", headers)
    }

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = "ul.stacksList > li.stacksBook"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3.stacksBook-tit > a")!!.text()
        url = element.selectFirst("h3.stacksBook-tit > a")!!.attr("href").drop(7)
        thumbnail_url = element.selectFirst("img")!!.attr("src")
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.detail-tit > h1")!!.text()
        thumbnail_url = document.selectFirst("div.detail-cover > img")!!.attr("src")
        author = document.selectFirst("p.author > span.author-name")!!.text()
        artist = author
        genre = document.select("div.detail-tit > a.detail-categ").eachText().joinToString(", ")
        description = document.selectFirst("p.detail-docu")!!.text()
        status = when (document.selectFirst("span.cata-info")!!.text()) {
            "连载中" -> SManga.ONGOING
            "完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("detail_").substringBefore(".html")
        return GET("$baseUrl/catalog/$id/", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return json.parseToJsonElement(response.body.string())
            .jsonObject["data"]!!.jsonObject["episodes"]!!.jsonArray.map {
            SChapter.create().apply {
                val comicId = it.jsonObject["comicId"]!!.jsonPrimitive.content
                val episodeId = it.jsonObject["episodeId"]!!.jsonPrimitive.content
                val episodeTitle = it.jsonObject["episodeTitle"]!!.jsonPrimitive.content
                val episodeOrder = it.jsonObject["episodeOrder"]!!.jsonPrimitive.int
                url = "/reader/${comicId}_$episodeId.html"
                name = "$episodeOrder $episodeTitle"
                date_upload = it.jsonObject["firstOnlineTime"]!!.jsonPrimitive.long
            }
        }.reversed()
    }

    override fun chapterListSelector(): String = throw Exception("Not Used")
    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not Used")

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        if (!document.select("div.main > p.pay-title").isEmpty()) {
            throw Exception("本章为付费章节")
        }
        return document.select("ul.main-container > li.main-item > img").mapIndexed { index, element ->
            if (element.hasAttr("data-original")) {
                Page(index, "", element.attr("data-original"))
            } else {
                Page(index, "", element.attr("src"))
            }
        }
    }

    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")
}
