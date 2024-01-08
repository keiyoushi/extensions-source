package eu.kanade.tachiyomi.multisrc.sinmh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 圣樱漫画CMS https://gitee.com/shenl/SinMH-2.0-Guide
 * ref: https://github.com/kanasimi/CeJS/tree/master/application/net/work_crawler/sites
 *      https://github.com/kanasimi/work_crawler/blob/master/document/README.cmn-Hant-TW.md
 */
abstract class SinMH(
    override val name: String,
    _baseUrl: String,
    override val lang: String = "zh",
) : ParsedHttpSource() {

    override val baseUrl = _baseUrl
    protected open val mobileUrl = _baseUrl.replaceFirst("www.", "m.")
    override val supportsLatest = true

    override val client = network.client.newBuilder().rateLimit(2).build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    protected open val nextPageSelector = "ul.pagination > li.next:not(.disabled)"
    protected open val comicItemSelector = "#contList > li, li.list-comic"
    protected open val comicItemTitleSelector = "p > a, h3 > a"
    protected open fun mangaFromElement(element: Element) = SManga.create().apply {
        val titleElement = element.selectFirst(comicItemTitleSelector)!!
        title = titleElement.text()
        setUrlWithoutDomain(titleElement.attr("href"))
        val image = element.selectFirst(Evaluator.Tag("img"))!!
        thumbnail_url = image.attr("src").ifEmpty { image.attr("data-src") }
    }

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/click/?page=$page", headers)
    override fun popularMangaNextPageSelector(): String? = nextPageSelector
    override fun popularMangaSelector() = comicItemSelector
    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        parseCategories(document)
        val mangas = document.select(popularMangaSelector()).map(::popularMangaFromElement)
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/list/update/?page=$page", headers)
    override fun latestUpdatesNextPageSelector(): String? = nextPageSelector
    override fun latestUpdatesSelector() = comicItemSelector
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        parseCategories(document)
        val mangas = document.select(latestUpdatesSelector()).map(::latestUpdatesFromElement)
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override fun searchMangaNextPageSelector(): String? = nextPageSelector
    override fun searchMangaSelector(): String = comicItemSelector
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isNotEmpty()) {
            GET("$baseUrl/search/?keywords=$query&page=$page", headers)
        } else {
            val categories = filters.filterIsInstance<UriPartFilter>().map { it.toUriPart() }
                .filter { it.isNotEmpty() }
            val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.toUriPart().orEmpty()
            val url = StringBuilder(baseUrl).append("/list/").apply {
                categories.joinTo(this, separator = "-", postfix = "-/")
            }.append(sort).append("?page=").append(page).toString()
            GET(url, headers)
        }

    // Details

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".book-title > h1")!!.text()
        val detailsList = document.selectFirst(Evaluator.Class("detail-list"))!!
        author = detailsList.select("strong:contains(作者) ~ *").text()
        description = document.selectFirst(Evaluator.Id("intro-all"))!!.text().trim()
            .removePrefix("漫画简介：").trim()
            .removePrefix("漫画简介：").trim() // some sources have double prefix
        genre = mangaDetailsParseDefaultGenre(document, detailsList)
        status = when (detailsList.selectFirst("strong:contains(状态) + *")!!.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.book-cover img")!!.attr("src")
    }

    protected open fun mangaDetailsParseDefaultGenre(document: Document, detailsList: Element): String {
        val category = detailsList.selectFirst("strong:contains(类型) + a")!!
        val breadcrumbs = document.selectFirst("div.breadcrumb-bar")!!.select("a[href^=/list/]")
        return buildString {
            append(category.text())
            breadcrumbs.map(Element::text).filter(String::isNotEmpty).joinTo(this, prefix = ", ")
        }
    }

    protected fun mangaDetailsParseDMZJStyle(document: Document, hasBreadcrumb: Boolean) = SManga.create().apply {
        val detailsDiv = document.selectFirst("div.comic_deCon")!!
        title = detailsDiv.selectFirst(Evaluator.Tag("h1"))!!.text()
        val details = detailsDiv.select("> ul > li")
        val linkSelector = Evaluator.Tag("a")
        author = details[0].selectFirst(linkSelector)!!.text()
        status = when (details[1].selectFirst(linkSelector)!!.text()) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = mutableListOf<Element>().apply {
            add(details[2].selectFirst(linkSelector)!!) // 类别
            addAll(details[3].select(linkSelector)) // 类型
            if (hasBreadcrumb) addAll(document.selectFirst("div.mianbao")!!.select("a[href^=/list/]"))
        }.mapTo(mutableSetOf()) { it.text() }.joinToString(", ")
        description = detailsDiv.selectFirst("> p.comic_deCon_d")!!.text()
        thumbnail_url = document.selectFirst("div.comic_i_img > img")!!.attr("src")
    }

    // Chapters

    override fun chapterListRequest(manga: SManga) = GET(mobileUrl + manga.url, headers)

    protected open val dateSelector = ".date"

    protected open fun List<SChapter>.sortedDescending() = this.asReversed()
    protected open fun Elements.sectionsDescending() = this.asReversed()

    override fun chapterListParse(response: Response): List<SChapter> {
        return chapterListParse(response, chapterListSelector(), dateSelector)
    }

    protected fun chapterListParse(response: Response, listSelector: String, dateSelector: String): List<SChapter> {
        val document = response.asJsoup()
        val sectionSelector = listSelector.substringBefore(' ')
        val itemSelector = listSelector.substringAfter(' ')
        val list = document.select(sectionSelector).sectionsDescending().flatMap { section ->
            section.select(itemSelector).map { chapterFromElement(it) }.sortedDescending()
        }
        if (list.isNotEmpty()) {
            val date = document.selectFirst(dateSelector)!!.textNodes().last().text()
            list[0].date_upload = DATE_FORMAT.parse(date)?.time ?: 0L
        }
        return list
    }

    /** 必须是 "section item" */
    override fun chapterListSelector() = ".chapter-body li > a"
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        runCatching { setUrlWithoutDomain(element.attr("href")) }.onFailure { url = "" }
        val children = element.children()
        name = if (children.isEmpty()) element.text() else children[0].text()
    }

    // Pages

    override fun pageListRequest(chapter: SChapter) = GET(mobileUrl + chapter.url, headers)

    protected open val imageHost: String by lazy {
        client.newCall(GET("$baseUrl/js/config.js", headers)).execute().let {
            Regex("""resHost:.+?"?domain"?:\["(.+?)"""").find(it.body.string())!!
                .groupValues[1].substringAfter(':').run { "https:$this" }
        }
    }

    // baseUrl/js/common.js/getChapterImage()
    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("body > script")!!.html().let(::ProgressiveParser)
        val images = script.substringBetween("chapterImages = ", ";")
        if (images.length <= 2) return emptyList() // [] or ""
        val path = script.substringBetween("chapterPath = \"", "\";")
        return images.let(::parsePageImages).mapIndexed { i, image ->
            val imageUrl = when {
                image.startsWith("https://") -> image
                image.startsWith("/") -> "$imageHost$image"
                else -> "$imageHost/$path$image"
            }
            Page(i, imageUrl = imageUrl)
        }
    }

    // default parsing of ["...","..."]
    protected open fun parsePageImages(chapterImages: String): List<String> =
        if (chapterImages.length > 4) {
            chapterImages.run { substring(2, length - 2) }.replace("""\/""", "/").split("\",\"")
        } else {
            emptyList() // []
        }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used.")

    protected class UriPartFilter(displayName: String, values: Array<String>, private val uriParts: Array<String>) :
        Filter.Select<String>(displayName, values) {
        fun toUriPart(): String = uriParts[state]
    }

    private class SortFilter : Filter.Select<String>("排序方式", sortNames) {
        fun toUriPart(): String = sortKeys[state]
    }

    protected class Category(private val name: String, private val values: Array<String>, private val uriParts: Array<String>) {
        fun toUriPartFilter() = UriPartFilter(name, values, uriParts)
    }

    protected var categories: List<Category> = emptyList()

    protected open fun parseCategories(document: Document) {
        if (categories.isNotEmpty()) return
        val labelSelector = Evaluator.Tag("label")
        val linkSelector = Evaluator.Tag("a")
        categories = document.selectFirst(Evaluator.Class("filter-nav"))!!.children().map { element ->
            val name = element.selectFirst(labelSelector)!!.text()
            val tags = element.select(linkSelector)
            val values = tags.map { it.text() }.toTypedArray()
            val uriParts = tags.map { it.attr("href").removePrefix("/list/").removeSuffix("/") }.toTypedArray()
            Category(name, values, uriParts)
        }
    }

    override fun getFilterList(): FilterList {
        val list: ArrayList<Filter<*>>
        if (categories.isNotEmpty()) {
            list = ArrayList(categories.size + 2)
            with(list) {
                add(Filter.Header("分类筛选（搜索文本时无效）"))
                categories.forEach { add(it.toUriPartFilter()) }
            }
        } else {
            list = ArrayList(3)
            with(list) {
                add(Filter.Header("点击“重置”即可刷新分类，如果失败，"))
                add(Filter.Header("请尝试重新从图源列表点击进入图源"))
            }
        }
        list.add(SortFilter())
        return FilterList(list)
    }

    companion object {
        private val DATE_FORMAT by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
        private val sortNames = arrayOf("按发布排序", "按发布排序(逆序)", "按更新排序", "按更新排序(逆序)", "按点击排序", "按点击排序(逆序)")
        private val sortKeys = arrayOf("post/", "-post/", "update/", "-update/", "click/", "-click/")
    }
}
