package eu.kanade.tachiyomi.extension.zh.hanmanwuzhe

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

class hanmanwuzhe : ParsedHttpSource() {
    override val name = "韩漫无遮挡"
    override val baseUrl = "https://www.hanmanwuzhe.com"
    override val lang = "zh"
    override val supportsLatest = true
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    override val client = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl + "/custom/hot"
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl + "/custom/update"

        return GET(url, headers)
    }

    override fun popularMangaSelector() = "ul.u_list > li"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = "ul.u_list > li, ul.catagory-list > li"

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = null
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = "a:contains(下一页)"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("div.neirong > a.name, a.img").attr("href"))
            title = element.select("div.neirong > a.name, a.txt").text()
            thumbnail_url = element.select("div.pic > a > img, a.img > img").imgAttr()
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
//        val genres = filters.filterIsInstance<GenreListFilter>().first()
//            .state.filter { it.state }.map { it.id }

        val url = if (query.isNotBlank()) {
            "$baseUrl/index.php/search"
                .toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .toString()
        } else {
            var tagUrl = ""
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        tagUrl = arrayOf("list/1", "list/2", "list/3", "city/42", "city/43", "city/44", "city/45", "tags/6", "tags/7", "tags/8", "tags/9", "tags/10", "tags/11", "tags/12", "tags/13", "tags/14", "tags/15", "tags/16", "tags/17", "tags/18", "tags/19", "tags/20", "tags/21", "tags/22", "tags/23", "tags/24", "tags/25", "tags/26", "tags/27", "tags/28", "tags/29", "tags/30", "tags/31", "tags/48", "tags/49", "tags/50", "tags/51", "tags/52", "tags/53", "tags/54", "tags/55", "tags/56", "tags/57", "tags/58", "tags/59", "tags/60", "tags/61", "tags/62", "tags/63", "tags/64", "tags/65", "tags/66", "tags/67", "tags/68", "tags/69", "tags/70", "tags/71", "tags/72", "tags/73", "tags/74", "tags/75", "tags/76", "tags/77", "tags/78", "tags/79", "tags/80", "tags/81", "tags/82", "tags/83", "tags/84", "tags/85", "tags/86", "tags/87", "tags/88", "tags/89", "tags/90", "tags/91", "tags/92", "tags/93", "tags/94", "tags/95", "tags/96", "tags/97", "tags/98", "tags/99", "tags/100", "tags/101", "tags/102", "tags/103", "tags/104", "finish/1", "finish/2")[filter.state]
                    }
                    else -> {}
                }
            }

            "$baseUrl/category/$tagUrl" +
                if (page > 1) {
                    "/page/$page/"
                } else {
                    ""
                }
        }
        return GET(url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1 > div.title").text()
            author = document.select("div.info > p.tage:contains(作者：)").text().substringAfter("作者：")
            genre = document.select("p.tage:contains(类型：) > a[href*=/category/tags/]").eachText().joinToString()
            thumbnail_url = document.select("div.info > div.img > img").imgAttr()
            description = document.select("div.text").text()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun chapterListSelector() = "div.listbox > ul.list li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.select("a").attr("href")
        name = element.select("a").first()!!.ownText()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        println(document.select(chapterListSelector()))
        return document.select(chapterListSelector()).map { chapterFromElement(it) }.reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.chapterbox li.pic img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private class GenreFilter() : Filter.Select<String>(
        "Genre",
        arrayOf(
            "3D漫画",
            "韩漫",
            "日漫",
            "内地",
            "港台",
            "韩国",
            "日本",
            "热血",
            "冒险",
            "科幻",
            "霸总",
            "玄幻",
            "校园",
            "修真",
            "搞笑",
            "穿越",
            "后宫",
            "耽美",
            "恋爱",
            "悬疑",
            "恐怖",
            "战争",
            "动作",
            "同人",
            "竞技",
            "励志",
            "架空",
            "灵异",
            "百合",
            "古风",
            "生活",
            "真人",
            "都市",
            "3D漫画",
            "古装",
            "御姐",
            "肉欲",
            "扶他",
            "强奸",
            "洗脑",
            "调教",
            "幻想",
            "死体",
            "死奸",
            "淫荡",
            "寝取",
            "性奴",
            "人妻",
            "巨乳",
            "教师",
            "露出",
            "黑丝",
            "母子",
            "乱伦",
            "少女",
            "催眠",
            "皮衣",
            "剧情",
            "丝袜",
            "欧美",
            "凌辱",
            "足交",
            "奇幻",
            "跨种族",
            "卖肉",
            "姐妹",
            "公媳",
            "捆绑",
            "黑人",
            "日常",
            "迷奸",
            "合集",
            "短篇",
            "模特",
            "空姐",
            "美少女",
            "青年",
            "韩国漫画",
            "日本漫画",
            "真人漫画",
            "绿母",
            "美乳",
            "OL装",
            "绿帽",
            "出轨",
            "母女",
            "女同",
            "堕落",
            "猎奇",
            "旗袍装",
            "连载",
            "完结",
        ),
        0,
    )

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Text search will be ignored if genre's picked"),
        GenreFilter(),
    )

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    private fun Elements.imgAttr(): String = this.first()!!.imgAttr()
}
