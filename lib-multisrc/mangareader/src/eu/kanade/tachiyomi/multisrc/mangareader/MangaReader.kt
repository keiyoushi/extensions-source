package eu.kanade.tachiyomi.multisrc.mangareader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder

abstract class MangaReader(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json: Json by injectLazy()

    open fun addPage(page: Int, builder: HttpUrl.Builder) {
        builder.addQueryParameter("page", page.toString())
    }

    // ============================== Popular ===============================

    protected open val sortPopularValue = "most-viewed"

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(sortFilterName, sortFilterParam, sortFilterValues(), sortPopularValue)),
        )
    }

    final override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    protected open val sortLatestValue = "latest-updated"

    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(sortFilterName, sortFilterParam, sortFilterValues(), sortLatestValue)),
        )
    }

    final override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // =============================== Search ===============================

    protected open val searchPathSegment = "search"
    protected open val searchKeyword = "keyword"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment(searchPathSegment)
                addQueryParameter(searchKeyword, query)
            } else {
                addPathSegment("filter")
                val filterList = filters.ifEmpty { getFilterList() }
                filterList.filterIsInstance<UriFilter>().forEach {
                    it.addToUri(this)
                }
            }

            addPage(page, this)
        }.build()

        return GET(url, headers)
    }

    open fun searchMangaSelector(): String = ".manga_list-sbs .manga-poster"

    open fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        element.selectFirst("img")!!.let {
            title = it.attr("alt")
            thumbnail_url = it.imgAttr()
        }
    }

    open fun searchMangaNextPageSelector(): String = "ul.pagination > li.active + li"

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select(searchMangaSelector())
            .map(::searchMangaFromElement)

        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(entries, hasNextPage)
    }

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    private val authorText: String = when (lang) {
        "ja" -> "著者"
        else -> "Authors"
    }

    private val statusText: String = when (lang) {
        "ja" -> "地位"
        else -> "Status"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            document.selectFirst("#ani_detail")!!.run {
                title = selectFirst(".manga-name")!!.ownText()
                thumbnail_url = selectFirst("img")?.imgAttr()
                genre = select(".genres > a").joinToString { it.ownText() }

                description = buildString {
                    selectFirst(".description")?.ownText()?.let { append(it) }
                    append("\n\n")
                    selectFirst(".manga-name-or")?.ownText()?.let {
                        if (it.isNotEmpty() && it != title) {
                            append("Alternative Title: ")
                            append(it)
                        }
                    }
                }.trim()

                select(".anisc-info > .item").forEach { info ->
                    when (info.selectFirst(".item-head")?.ownText()) {
                        "$authorText:" -> info.parseAuthorsTo(this@apply)
                        "$statusText:" -> info.parseStatus(this@apply)
                    }
                }
            }
        }
    }

    private fun Element.parseAuthorsTo(manga: SManga): SManga {
        val authors = select("a")
        val text = authors.map { it.ownText().replace(",", "") }

        val count = authors.size
        when (count) {
            0 -> return manga
            1 -> {
                manga.author = text.first()
                return manga
            }
        }

        val authorList = ArrayList<String>(count)
        val artistList = ArrayList<String>(count)
        for ((index, author) in authors.withIndex()) {
            val textNode = author.nextSibling() as? TextNode
            val list = if (textNode?.wholeText?.contains("(Art)") == true) artistList else authorList
            list.add(text[index])
        }

        if (authorList.isNotEmpty()) manga.author = authorList.joinToString()
        if (artistList.isNotEmpty()) manga.artist = artistList.joinToString()
        return manga
    }

    private fun Element.parseStatus(manga: SManga): SManga {
        manga.status = this.selectFirst(".name")?.text().getStatus()
        return manga
    }

    open fun String?.getStatus(): Int = when (this?.lowercase()) {
        "ongoing", "publishing", "releasing" -> SManga.ONGOING
        "completed", "finished" -> SManga.COMPLETED
        "on-hold", "on_hiatus" -> SManga.ON_HIATUS
        "canceled", "discontinued" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url.substringBeforeLast('#')
    }

    open val chapterIdSelect = "en-chapters"

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(attr("href") + "#${element.attr("data-id")}")
            name = selectFirst(".name")?.text() ?: text()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#$chapterIdSelect > li.chapter-item").map(::chapterFromElement)
    }

    // =============================== Pages ================================

    open fun getChapterId(chapter: SChapter): String {
        val document = client.newCall(GET(baseUrl + chapter.url, headers)).execute().asJsoup()
        return document.selectFirst("div[data-reading-id]")
            ?.attr("data-reading-id")
            .orEmpty()
            .ifEmpty {
                throw Exception("Unable to retrieve chapter id")
            }
    }

    open fun getAjaxUrl(id: String): String {
        return "$baseUrl//ajax/image/list/$id?mode=vertical"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast('#').ifEmpty {
            getChapterId(chapter)
        }

        val ajaxHeaders = super.headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Referer", URLEncoder.encode(baseUrl + chapter.url.substringBeforeLast("#"), "utf-8"))
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        return GET(getAjaxUrl(chapterId), ajaxHeaders)
    }

    open fun pageListParseSelector(): String = ".container-reader-chapter > div > img"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.parseHtmlProperty()

        val pageList = document.select(pageListParseSelector()).mapIndexed { index, element ->
            val imgUrl = element.imgAttr().ifEmpty {
                element.selectFirst("img")!!.imgAttr()
            }

            Page(index, imageUrl = imgUrl)
        }

        return pageList
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================

    open fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        else -> attr("abs:src")
    }

    open fun Response.parseHtmlProperty(): Document {
        val html = json.parseToJsonElement(body.string()).jsonObject["html"]!!.jsonPrimitive.content
        return Jsoup.parseBodyFragment(html)
    }

    // =============================== Filters ==============================

    object Note : Filter.Header("NOTE: Ignored if using text search!")

    interface UriFilter {
        fun addToUri(builder: HttpUrl.Builder)
    }

    open class UriPartFilter(
        name: String,
        private val param: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        name,
        vals.map { it.first }.toTypedArray(),
        vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ),
        UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            builder.addQueryParameter(param, vals[state].second)
        }
    }

    open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

    open class UriMultiSelectFilter(
        name: String,
        private val param: String,
        private val vals: Array<Pair<String, String>>,
        private val join: String? = null,
    ) : Filter.Group<UriMultiSelectOption>(name, vals.map { UriMultiSelectOption(it.first, it.second) }), UriFilter {
        override fun addToUri(builder: HttpUrl.Builder) {
            val checked = state.filter { it.state }
            if (join == null) {
                checked.forEach {
                    builder.addQueryParameter(param, it.value)
                }
            } else {
                builder.addQueryParameter(param, checked.joinToString(join) { it.value })
            }
        }
    }

    open class SortFilter(
        title: String,
        param: String,
        values: Array<Pair<String, String>>,
        default: String? = null,
    ) : UriPartFilter(title, param, values, default)

    private val sortFilterName: String = when (lang) {
        "ja" -> "選別"
        else -> "Sort"
    }

    protected open val sortFilterParam: String = "sort"

    protected open fun sortFilterValues(): Array<Pair<String, String>> {
        return arrayOf(
            Pair("Default", "default"),
            Pair("Latest Updated", sortLatestValue),
            Pair("Score", "score"),
            Pair("Name A-Z", "name-az"),
            Pair("Release Date", "release-date"),
            Pair("Most Viewed", sortPopularValue),
        )
    }

    open fun getSortFilter() = SortFilter(sortFilterName, sortFilterParam, sortFilterValues())

    override fun getFilterList(): FilterList = FilterList(
        getSortFilter(),
    )
}
