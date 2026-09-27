package eu.kanade.tachiyomi.multisrc.sinmh

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * 圣樱漫画CMS https://gitee.com/shenl/SinMH-2.0-Guide
 * ref: https://github.com/kanasimi/CeJS/tree/master/application/net/work_crawler/sites
 *      https://github.com/kanasimi/work_crawler/blob/master/document/README.cmn-Hant-TW.md
 */
abstract class SinMH : KeiSource() {

    protected open val mobileUrl: String
        get() = baseUrl.replaceFirst("www.", "m.")

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override fun Headers.Builder.configureHeaders() = set("User-Agent", System.getProperty("http.agent")!!)
        .removeAll("Origin")

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

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/list/click/?page=$page").asJsoup()
        val mangas = document.select(popularMangaSelector()).map(::popularMangaFromElement)
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaNextPageSelector(): String? = nextPageSelector
    protected open fun popularMangaSelector() = comicItemSelector
    protected open fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/list/update/?page=$page").asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map(::latestUpdatesFromElement)
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun latestUpdatesNextPageSelector(): String? = nextPageSelector
    protected open fun latestUpdatesSelector() = comicItemSelector
    protected open fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    // Search

    protected open fun searchMangaNextPageSelector(): String? = nextPageSelector
    protected open fun searchMangaSelector(): String = comicItemSelector
    protected open fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("search/")
                .addQueryParameter("keywords", query)
                .addQueryParameter("page", page.toString())
                .build()
                .toString()
        } else {
            val categories = filters.filterIsInstance<UriPartFilter>().map { it.toUriPart() }
                .filter { it.isNotEmpty() }
            val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart().orEmpty()
            buildString {
                append(baseUrl).append("/list/")
                categories.joinTo(this, separator = "-", postfix = "-/")
                append(sort).append("?page=").append(page)
            }
        }
        val document = client.get(url).asJsoup()
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        val hasNextPage = searchMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details

    override fun getMangaUrl(manga: SManga) = mobileUrl + manga.url

    // details and chapters come from the same mobile page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(mobileUrl + manga.url).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    protected open fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("#comicName")!!.text()
        val items = document.select(".Introduct_Sub .txtItme")
        author = items.firstOrNull { it.selectFirst(".icon01") != null }?.text()
        val links = items.filter { it.selectFirst(".icon02") != null }.flatMap { it.select("a") }.map { it.text().removePrefix("#") }
        status = when {
            "连载中" in links -> SManga.ONGOING
            "已完结" in links -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = links.filter { it != "连载中" && it != "已完结" }.distinct().joinToString()
        description = (document.selectFirst("#full-des") ?: document.selectFirst("#simple-des"))?.text()?.removePrefix("介绍:")
        thumbnail_url = document.selectFirst("#Cover img")?.absUrl("src")
    }

    // Chapters

    protected open val dateSelector = ".date"

    protected open fun List<SChapter>.sortedDescending() = this.asReversed()
    protected open fun Elements.sectionsDescending() = this.asReversed()

    protected open fun chapterListParse(document: Document): List<SChapter> = chapterListParse(document, chapterListSelector(), dateSelector)

    protected fun chapterListParse(document: Document, listSelector: String, dateSelector: String): List<SChapter> {
        val sectionSelector = listSelector.substringBefore(' ')
        val itemSelector = listSelector.substringAfter(' ')
        val list = document.select(sectionSelector).sectionsDescending().flatMap { section ->
            section.select(itemSelector).map { chapterFromElement(it) }.sortedDescending()
        }
        if (list.isNotEmpty()) {
            val date = document.selectFirst(dateSelector)?.textNodes()?.lastOrNull()?.text()
            list[0].date_upload = DATE_FORMAT.tryParseDateTime(date?.trim(), ZoneId.of("Asia/Shanghai"))
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

    protected open fun pageListUrl(chapter: SChapter) = mobileUrl + chapter.url

    private var imageHost: String? = null

    private suspend fun getImageHost(): String = imageHost ?: client.get("$baseUrl/js/config.js").body.string().let { script ->
        Regex("""resHost:.+?"?domain"?:\["(.+?)"""").find(script)?.groupValues?.get(1).orEmpty()
    }.also { imageHost = it }

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(pageListUrl(chapter)).asJsoup(), getImageHost())

    // baseUrl/js/common.js/getChapterImage()
    protected open fun pageListParse(document: Document, imageHost: String): List<Page> {
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

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = parseCategories(client.get("$baseUrl/list/").asJsoup()).toJsonElement()

    protected open fun parseCategories(document: Document): List<Category> {
        val labelSelector = "label"
        val linkSelector = "a"
        return document.selectFirst(".filter-nav")?.children()?.mapNotNull { element ->
            val name = element.selectFirst(labelSelector)?.text() ?: return@mapNotNull null
            val tags = element.select(linkSelector)
            val values = tags.map { it.text() }.toTypedArray()
            val uriParts = tags.map { it.attr("href").removePrefix("/list/").removeSuffix("/") }.toTypedArray()
            Category(name, values, uriParts)
        } ?: emptyList()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<Category>>().orEmpty()
        val list = ArrayList<Filter<*>>(categories.size + 2)
        if (categories.isNotEmpty()) {
            list.add(Filter.Header("分类筛选（搜索文本时无效）"))
            categories.forEach { list.add(it.toUriPartFilter()) }
        }
        list.add(SortFilter())
        return FilterList(list)
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd[ HH:mm[:ss]]")
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.ROOT)
    }
}
