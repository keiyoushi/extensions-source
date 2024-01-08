package eu.kanade.tachiyomi.extension.zh.wuqimanga

import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator

// Memo: the old implementation had a string preference with key "IMAGE_SERVER"
// Updating the domain to www.wuqimh.com causes some requests to return 404
class WuqiManga : SinMH("57漫画", "http://www.wuqimh.net") {

    override val nextPageSelector = "span.pager > a:last-child" // in the last page it's a span
    override val comicItemSelector = "#contList > li, .book-result > li"
    override val comicItemTitleSelector = "p > a, dt > a"

    // 人气排序的漫画全是 404，所以就用默认的最新发布了
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/order-id-p-$page", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/list/order-addtime-p-$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val isSearch = query.isNotEmpty()
        val params = arrayListOf<String>()
        if (isSearch) params.add(query)
        filters.filterIsInstance<UriPartFilter>().mapTo(params) { it.toUriPart() }
        params.add("p-")
        val url = buildString(120) {
            append(baseUrl)
            append(if (isSearch) "/search/q_" else "/list/")
            params.joinTo(this, separator = "-", postfix = page.toString())
        }
        return GET(url, headers)
    }

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        val comment = document.selectFirst(".book-title > h2")!!.text()
        if (comment.isNotEmpty()) description = "$comment\n\n$description"
    }

    override fun mangaDetailsParseDefaultGenre(document: Document, detailsList: Element): String =
        document.selectFirst("div.crumb")!!.select("a[href^=/list/]")
            .map { it.text().removeSuffix("年").removeSuffix("漫画") }
            .filter { it.isNotEmpty() }.joinToString(", ")

    override fun chapterListSelector() = ".chapter-list li > a"
    override fun List<SChapter>.sortedDescending() = this
    override val dateSelector = ".cont-list dt:contains(更新于) + dd"

    override val imageHost: String by lazy {
        client.newCall(GET("$mobileUrl/templates_pc/default/scripts/configs.js", headers)).execute().let {
            Regex("""\['(.+?)']""").find(it.body.string())!!.groupValues[1].run { "http://$this" }
        }
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("body > script")!!.html()
        val unpacked = Unpacker.unpack(script, ":[", "]")
            .ifEmpty { return emptyList() }
            .replace("\\", "")
            .removeSurrounding("\"").split("\",\"")
        val list = unpacked.filterNot { it.endsWith("/ManHuaKu/222.jpg") }.map { image ->
            if (image.startsWith("http")) image else imageHost + image
        }
        if (list.isEmpty()) return emptyList()
        client.newCall(GET(list[0], headers)).execute().apply { close() }.also {
            if (!it.isSuccessful) throw Exception("该章节的图片加载出错：${it.code}")
        }
        return list.mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    override fun parseCategories(document: Document) {
        if (categories.isNotEmpty()) return
        val labelSelector = Evaluator.Tag("label")
        val linkSelector = Evaluator.Tag("a")
        val filterMap = LinkedHashMap<String, LinkedHashMap<String, String>>(8)
        document.select(Evaluator.Class("filter")).forEach { row ->
            val tags = row.select(linkSelector)
            if (tags.isEmpty()) return@forEach
            val name = row.selectFirst(labelSelector)!!.text().removeSuffix("：")
            if (!filterMap.containsKey(name)) {
                filterMap[name] = LinkedHashMap(tags.size * 2)
            }
            val tagMap = filterMap[name]!!
            for (tag in tags) {
                val tagName = tag.text()
                if (!tagMap.containsKey(tagName)) {
                    tagMap[tagName] = tag.attr("href").removePrefix("/list/").substringBeforeLast("-order-")
                }
            }
        }
        categories = filterMap.map {
            val tagMap = it.value
            Category(it.key, tagMap.keys.toTypedArray(), tagMap.values.toTypedArray())
        }
    }

    override fun getFilterList(): FilterList {
        val list: ArrayList<Filter<*>>
        if (categories.isNotEmpty()) {
            list = ArrayList(categories.size + 2)
            with(list) {
                add(Filter.Header("使用文本搜索时，只有地区、年份、字母选项有效"))
                categories.forEach { add(it.toUriPartFilter()) }
            }
        } else {
            list = ArrayList(4)
            with(list) {
                add(Filter.Header("点击“重置”即可刷新分类，如果失败，"))
                add(Filter.Header("请尝试重新从图源列表点击进入图源"))
                add(Filter.Header("使用文本搜索时，只有地区、年份、字母选项有效"))
            }
        }
        list.add(UriPartFilter("排序方式", sortNames, sortKeys))
        return FilterList(list)
    }

    private val sortNames = arrayOf("最新发布", "最近更新", "人气最旺", "评分最高")
    private val sortKeys = arrayOf("order-id", "order-addtime", "order-hits", "order-gold")
}
