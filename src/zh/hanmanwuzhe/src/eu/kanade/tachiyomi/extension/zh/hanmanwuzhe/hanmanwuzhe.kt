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
        val genres = filters.filterIsInstance<GenreListFilter>().first()
            .state.filter { it.state }.map { it.id }

        val url = if (query.isNotBlank() || genres.isEmpty()) {
            "$baseUrl/index.php/search"
                .toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .toString()
        } else {
            "$baseUrl/category/${genres.joinToString(",")}" +
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

    private class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Text search will be ignored if genre's picked, and you can only choose one genre"),
        GenreListFilter(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("3D漫画", "list/1"),
        Genre("韩漫", "list/2"),
        Genre("日漫", "list/3"),
        Genre("内地", "city/42"),
        Genre("港台", "city/43"),
        Genre("韩国", "city/44"),
        Genre("日本", "city/45"),
        Genre("热血", "tags/6"),
        Genre("冒险", "tags/7"),
        Genre("科幻", "tags/8"),
        Genre("霸总", "tags/9"),
        Genre("玄幻", "tags/10"),
        Genre("校园", "tags/11"),
        Genre("修真", "tags/12"),
        Genre("搞笑", "tags/13"),
        Genre("穿越", "tags/14"),
        Genre("后宫", "tags/15"),
        Genre("耽美", "tags/16"),
        Genre("恋爱", "tags/17"),
        Genre("悬疑", "tags/18"),
        Genre("恐怖", "tags/19"),
        Genre("战争", "tags/20"),
        Genre("动作", "tags/21"),
        Genre("同人", "tags/22"),
        Genre("竞技", "tags/23"),
        Genre("励志", "tags/24"),
        Genre("架空", "tags/25"),
        Genre("灵异", "tags/26"),
        Genre("百合", "tags/27"),
        Genre("古风", "tags/28"),
        Genre("生活", "tags/29"),
        Genre("真人", "tags/30"),
        Genre("都市", "tags/31"),
        Genre("3D漫画", "tags/48"),
        Genre("古装", "tags/49"),
        Genre("御姐", "tags/50"),
        Genre("肉欲", "tags/51"),
        Genre("扶他", "tags/52"),
        Genre("强奸", "tags/53"),
        Genre("洗脑", "tags/54"),
        Genre("调教", "tags/55"),
        Genre("幻想", "tags/56"),
        Genre("死体", "tags/57"),
        Genre("死奸", "tags/58"),
        Genre("淫荡", "tags/59"),
        Genre("寝取", "tags/60"),
        Genre("性奴", "tags/61"),
        Genre("人妻", "tags/62"),
        Genre("巨乳", "tags/63"),
        Genre("教师", "tags/64"),
        Genre("露出", "tags/65"),
        Genre("黑丝", "tags/66"),
        Genre("母子", "tags/67"),
        Genre("乱伦", "tags/68"),
        Genre("少女", "tags/69"),
        Genre("催眠", "tags/70"),
        Genre("皮衣", "tags/71"),
        Genre("剧情", "tags/72"),
        Genre("丝袜", "tags/73"),
        Genre("欧美", "tags/74"),
        Genre("凌辱", "tags/75"),
        Genre("足交", "tags/76"),
        Genre("奇幻", "tags/77"),
        Genre("跨种族", "tags/78"),
        Genre("卖肉", "tags/79"),
        Genre("姐妹", "tags/80"),
        Genre("公媳", "tags/81"),
        Genre("捆绑", "tags/82"),
        Genre("黑人", "tags/83"),
        Genre("日常", "tags/84"),
        Genre("迷奸", "tags/85"),
        Genre("合集", "tags/86"),
        Genre("短篇", "tags/87"),
        Genre("模特", "tags/88"),
        Genre("空姐", "tags/89"),
        Genre("美少女", "tags/90"),
        Genre("青年", "tags/91"),
        Genre("韩国漫画", "tags/92"),
        Genre("日本漫画", "tags/93"),
        Genre("真人漫画", "tags/94"),
        Genre("绿母", "tags/95"),
        Genre("美乳", "tags/96"),
        Genre("OL装", "tags/97"),
        Genre("绿帽", "tags/98"),
        Genre("出轨", "tags/99"),
        Genre("母女", "tags/100"),
        Genre("女同", "tags/101"),
        Genre("堕落", "tags/102"),
        Genre("猎奇", "tags/103"),
        Genre("旗袍装", "tags/104"),
        Genre("连载", "finish/1"),
        Genre("完结", "finish/2"),
    )

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    private fun Elements.imgAttr(): String = this.first()!!.imgAttr()
}
