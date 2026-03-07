package eu.kanade.tachiyomi.extension.zh.mh1234

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
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

class MH1234 : ParsedHttpSource() {

    override val baseUrl = "https://m.wmh1234.com"
    override val lang = "zh"
    override val name = "漫画1234"
    override val supportsLatest = true

    // Popular Page

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("order")
            addPathSegment("hits")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = ".comic-card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.comic-card__link")!!.let {
            this.setUrlWithoutDomain(it.absUrl("href"))
            title = it.selectFirst(".comic-card__title")!!.text()
            thumbnail_url = it.selectFirst("img.comic-card__image")?.attr("data-src")
        }
    }

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // Latest Page

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("order")
            addPathSegment("addtime")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // Search Page

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addPathSegment(query)
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        GET(url, headers)
    } else {
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selected?.value ?: "0"
        val status = filters.filterIsInstance<StatusFilter>().firstOrNull()?.selected?.value ?: "0"
        val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.selected?.value ?: "id"

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("tags")
            addPathSegment(genre)
            addPathSegment("finish")
            addPathSegment(status)
            addPathSegment("order")
            addPathSegment(sort)
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = ".pagination-wrapper a:contains(下一页), .pagination-wrapper a:contains(>)"

    // Manga Detail Page

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val meta = document.select(".comic-hero__meta .meta-item")
        author = meta.getOrNull(0)?.text()
        genre = meta.getOrNull(1)?.text()
        status = when (document.selectFirst(".stat-item:contains(状态) .stat-value")?.text()) {
            "连载" -> SManga.ONGOING
            "完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = document.selectFirst("#comicDesc")?.text()?.removePrefix("介绍:")?.trim()
    }

    // Manga Detail Page / Chapters Page (Separate)

    // override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url)

    override fun chapterListSelector() = ".chapter-list a.chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        this.setUrlWithoutDomain(element.absUrl("href"))
        name = element.selectFirst(".chapter-title")!!.text()
    }

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()

    // Manga View Page

    override fun pageListParse(document: Document): List<Page> = document.select("img.reader-image").mapIndexed { i, img ->
        Page(i, "", img.attr("data-src"))
    }

    // Image

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    private class GenreFilter :
        SelectFilter(
            "题材",
            arrayOf(
                Pair("全部", "0"),
                Pair("神魔", "149"),
                Pair("丧尸", "148"),
                Pair("逗比", "147"),
                Pair("血腥", "146"),
                Pair("重口味", "145"),
                Pair("其它", "144"),
                Pair("游戏", "143"),
                Pair("震撼", "142"),
                Pair("乡村", "141"),
                Pair("商战", "140"),
                Pair("科技", "139"),
                Pair("未来", "138"),
                Pair("权谋", "137"),
                Pair("宫廷", "136"),
                Pair("僵尸", "135"),
                Pair("末世", "134"),
                Pair("机甲", "133"),
                Pair("体育", "132"),
                Pair("豪门", "131"),
                Pair("感动", "130"),
                Pair("纠结", "129"),
                Pair("娱乐圈", "128"),
                Pair("烧脑", "127"),
                Pair("逆袭", "126"),
                Pair("段子", "125"),
                Pair("少年热血", "48"),
                Pair("武侠格斗", "49"),
                Pair("科幻魔幻", "50"),
                Pair("竞技体育", "51"),
                Pair("爆笑喜剧", "52"),
                Pair("侦探推理", "53"),
                Pair("恐怖灵异", "54"),
                Pair("耽美人生", "55"),
                Pair("少女爱情", "56"),
                Pair("恋爱生活", "57"),
                Pair("生活漫画", "58"),
                Pair("战争漫画", "59"),
                Pair("故事漫画", "60"),
                Pair("其他漫画", "61"),
                Pair("快看漫画", "62"),
                Pair("韩国漫画", "63"),
                Pair("爱情", "64"),
                Pair("唯美", "65"),
                Pair("武侠", "66"),
                Pair("治愈", "67"),
                Pair("虐心", "68"),
                Pair("魔幻", "69"),
                Pair("欢乐向", "70"),
                Pair("节操", "71"),
                Pair("历史", "72"),
                Pair("职场", "73"),
                Pair("神鬼", "74"),
                Pair("明星", "75"),
                Pair("西方魔幻", "76"),
                Pair("纯爱", "77"),
                Pair("音乐舞蹈", "78"),
                Pair("轻小说", "79"),
                Pair("侦探", "80"),
                Pair("伪娘", "81"),
                Pair("仙侠", "82"),
                Pair("四格", "83"),
                Pair("剧情", "84"),
                Pair("萌系", "85"),
                Pair("东方", "86"),
                Pair("性转换", "87"),
                Pair("宅系", "88"),
                Pair("美食", "89"),
                Pair("脑洞", "90"),
                Pair("惊险", "91"),
                Pair("爆笑", "92"),
                Pair("格斗", "93"),
                Pair("魔法", "94"),
                Pair("奇幻", "95"),
                Pair("其他", "96"),
                Pair("搞笑喜剧", "97"),
                Pair("青春", "98"),
                Pair("浪漫", "99"),
                Pair("爽流", "100"),
                Pair("神话", "101"),
                Pair("轻松", "102"),
                Pair("日常", "103"),
                Pair("家庭", "104"),
                Pair("婚姻", "105"),
                Pair("战斗", "106"),
                Pair("异能", "107"),
                Pair("内涵", "108"),
                Pair("惊奇", "109"),
                Pair("正剧", "110"),
                Pair("推理", "111"),
                Pair("宠物", "112"),
                Pair("温馨", "113"),
                Pair("异世界", "114"),
                Pair("颜艺", "115"),
                Pair("惊悚", "116"),
                Pair("舰娘", "117"),
                Pair("机战", "118"),
                Pair("彩虹", "119"),
                Pair("同人漫画", "120"),
                Pair("复仇", "122"),
            ),
        )

    private class StatusFilter :
        SelectFilter(
            "状态",
            arrayOf(
                Pair("全部", "0"),
                Pair("连载", "1"),
                Pair("完结", "2"),
            ),
        )

    private class SortFilter :
        SelectFilter(
            "排序",
            arrayOf(
                Pair("最新", "id"),
                Pair("热门", "hits"),
                Pair("更新", "addtime"),
            ),
        )

    private abstract class SelectFilter(name: String, val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
        val selected get() = options[state]
    }
}
