package eu.kanade.tachiyomi.multisrc.sinmh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
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
) : HttpSource() {

    override val baseUrl = _baseUrl
    protected open val mobileUrl = _baseUrl.replaceFirst("www.", "m.")
    override val supportsLatest = true

    override val client = network.client.newBuilder().rateLimit(2).build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", System.getProperty("http.agent")!!)
        .add("Referer", "$baseUrl/")

    protected open val nextPageSelector = "ul.pagination > li.next:not(.disabled)"
    protected open val comicItemSelector = "#contList > li, li.list-comic"
    protected open val comicItemTitleSelector = "p > a, h3 > a"
    protected open fun mangaFromElement(element: Element) = SManga.create().apply {
        val titleElement = element.selectFirst(comicItemTitleSelector)!!
        title = titleElement.text()
        setUrlWithoutDomain(titleElement.absUrl("href"))
        val image = element.selectFirst("img")
        thumbnail_url = image?.absUrl("src")?.ifEmpty { image.absUrl("data-src") }
    }

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/list/click/?page=$page", headers)
    protected open fun popularMangaNextPageSelector(): String? = nextPageSelector
    protected open fun popularMangaSelector() = comicItemSelector
    protected open fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        parseCategories(document)
        val mangas = document.select(popularMangaSelector()).map(::popularMangaFromElement)
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/list/update/?page=$page", headers)
    protected open fun latestUpdatesNextPageSelector(): String? = nextPageSelector
    protected open fun latestUpdatesSelector() = comicItemSelector
    protected open fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        parseCategories(document)
        val mangas = document.select(latestUpdatesSelector()).map(::latestUpdatesFromElement)
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    protected open fun searchMangaNextPageSelector(): String? = nextPageSelector
    protected open fun searchMangaSelector(): String = comicItemSelector
    protected open fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = if (query.isNotEmpty()) {
        GET("$baseUrl/search/?keywords=$query&page=$page", headers)
    } else {
        val categories = filters.filterIsInstance<UriPartFilter>().map { it.toUriPart() }
            .filter { it.isNotEmpty() }
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart().orEmpty()
        val url = buildString {
            append(baseUrl).append("/list/")
            categories.joinTo(this, separator = "-", postfix = "-/")
            append(sort).append("?page=").append(page)
        }
        GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        parseCategories(document)
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        val hasNextPage = searchMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details

    override fun getMangaUrl(manga: SManga) = mobileUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    protected open fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".book-title > h1")?.text().orEmpty()
        val detailsList = document.selectFirst(".detail-list")
        if (detailsList != null) {
            author = detailsList.select("strong:contains(作者) ~ *").text()
            genre = mangaDetailsParseDefaultGenre(document, detailsList)
            status = when (detailsList.selectFirst("strong:contains(状态) + *")?.text()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        description = document.selectFirst("#intro-all")?.text()
            ?.removePrefix("漫画简介：")?.trimStart()
            ?.removePrefix("漫画简介：")?.trimStart().orEmpty() // some sources have double prefix
        thumbnail_url = document.selectFirst("div.book-cover img")?.absUrl("src")
    }

    protected open fun mangaDetailsParseDefaultGenre(document: Document, detailsList: Element): String {
        val category = detailsList.selectFirst("strong:contains(类型) + a")
        val breadcrumbs = document.selectFirst("div.breadcrumb-bar")?.select("a[href^=/list/]")
        return buildString {
            category?.text()?.let { append(it) }
            breadcrumbs?.map(Element::text)?.filter(String::isNotEmpty)?.joinTo(this, prefix = ", ")
        }
    }

    protected fun mangaDetailsParseDMZJStyle(document: Document, hasBreadcrumb: Boolean) = SManga.create().apply {
        val detailsDiv = document.selectFirst("div.comic_deCon") ?: return@apply
        title = detailsDiv.selectFirst("h1")?.text().orEmpty()
        val details = detailsDiv.select("> ul > li")
        val linkSelector = "a"

        if (details.isNotEmpty()) {
            author = details[0].text().removePrefix("作者：").trimStart()
        }
        if (details.size > 1) {
            status = when (details[1].selectFirst(linkSelector)?.text()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        genre = buildList {
            if (details.size > 2) details[2].selectFirst(linkSelector)?.let { add(it) } // 类别
            if (details.size > 3) addAll(details[3].select(linkSelector)) // 类型
            if (hasBreadcrumb) {
                document.selectFirst("div.mianbao")?.select("a[href^=/list/]")?.let { addAll(it) }
            }
        }.mapTo(mutableSetOf()) { it.text() }.joinToString()

        description = detailsDiv.selectFirst("> p.comic_deCon_d")?.text().orEmpty()
        thumbnail_url = document.selectFirst("div.comic_i_img > img")?.absUrl("src")
    }

    // Chapters

    override fun chapterListRequest(manga: SManga) = GET(mobileUrl + manga.url, headers)

    protected open val dateSelector = ".date"

    protected open fun List<SChapter>.sortedDescending() = this.asReversed()
    protected open fun Elements.sectionsDescending() = this.asReversed()

    override fun chapterListParse(response: Response): List<SChapter> = chapterListParse(response, chapterListSelector(), dateSelector)

    protected fun chapterListParse(response: Response, listSelector: String, dateSelector: String): List<SChapter> {
        val document = response.asJsoup()
        val sectionSelector = listSelector.substringBefore(' ')
        val itemSelector = listSelector.substringAfter(' ')
        val list = document.select(sectionSelector).sectionsDescending().flatMap { section ->
            section.select(itemSelector).map { chapterFromElement(it) }.sortedDescending()
        }
        if (list.isNotEmpty()) {
            val date = document.selectFirst(dateSelector)?.textNodes()?.lastOrNull()?.text()
            list[0].date_upload = DATE_FORMAT.tryParse(date)
        }
        return list
    }

    /** 必须是 "section item" */
    protected open fun chapterListSelector() = ".chapter-body li > a"

    protected open fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val children = element.children()
        name = if (children.isEmpty()) element.text() else children[0].text()
    }

    // Pages

    override fun getChapterUrl(chapter: SChapter) = mobileUrl + chapter.url

    override fun pageListRequest(chapter: SChapter) = GET(mobileUrl + chapter.url, headers)

    protected open val imageHost: String by lazy {
        client.newCall(GET("$baseUrl/js/config.js", headers)).execute().use { response ->
            Regex("""resHost:.+?"?domain"?:\["(.+?)"""").find(response.body.string())?.groupValues?.get(1).orEmpty()
        }
    }

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    // baseUrl/js/common.js/getChapterImage()
    protected open fun pageListParse(document: Document): List<Page> {
        val scriptText = document.selectFirst("body > script")?.html() ?: return emptyList()
        val script = ProgressiveParser(scriptText)
        val images = script.substringBetween("chapterImages = ", ";")
        if (images.length <= 2) return emptyList() // [] or ""
        val path = script.substringBetween("chapterPath = \"", "\";")
        return parsePageImages(images).mapIndexed { i, image ->
            val imageUrl = when {
                image.startsWith("https://") -> image
                image.startsWith("/") -> "$imageHost$image"
                else -> "$imageHost/$path$image"
            }
            Page(i, imageUrl = imageUrl)
        }
    }

    // default parsing of ["...","..."]
    protected open fun parsePageImages(chapterImages: String): List<String> = if (chapterImages.length > 4) {
        chapterImages.run { substring(2, length - 2) }.replace("""\/""", "/").split("\",\"")
    } else {
        emptyList() // []
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    protected var categories: List<Category> = emptyList()

    protected open fun parseCategories(document: Document) {
        if (categories.isNotEmpty()) return
        val labelSelector = "label"
        val linkSelector = "a"
        categories = document.selectFirst(".filter-nav")?.children()?.mapNotNull { element ->
            val name = element.selectFirst(labelSelector)?.text() ?: return@mapNotNull null
            val tags = element.select(linkSelector)
            val values = tags.map { it.text() }.toTypedArray()
            val uriParts = tags.map { it.attr("href").removePrefix("/list/").removeSuffix("/") }.toTypedArray()
            Category(name, values, uriParts)
        } ?: emptyList()
    }

    override fun getFilterList(): FilterList {
        val list = ArrayList<Filter<*>>()
        if (categories.isNotEmpty()) {
            list.ensureCapacity(categories.size + 2)
            list.add(Filter.Header("分类筛选（搜索文本时无效）"))
            categories.forEach { list.add(it.toUriPartFilter()) }
        } else {
            list.ensureCapacity(3)
            list.add(Filter.Header("点击“重置”即可刷新分类，如果失败，"))
            list.add(Filter.Header("请尝试重新从图源列表点击进入图源"))
        }
        list.add(SortFilter())
        return FilterList(list)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
