package eu.kanade.tachiyomi.multisrc.mangareader

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URLEncoder

@Serializable
class AjaxResponse(
    val html: String,
)

abstract class MangaReader : KeiSource() {

    open fun addPage(page: Int, builder: HttpUrl.Builder) {
        builder.addQueryParameter("page", page.toString())
    }

    // ============================== Popular ===============================

    protected open val sortPopularValue = "most-viewed"

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(SortFilter(sortFilterName, sortFilterParam, sortFilterValues(), sortPopularValue)),
    )

    // =============================== Latest ===============================

    protected open val sortLatestValue = "latest-updated"

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(SortFilter(sortFilterName, sortFilterParam, sortFilterValues(), sortLatestValue)),
    )

    // =============================== Search ===============================

    protected open val searchPathSegment = "search"
    protected open val searchKeyword = "keyword"

    open fun searchMangaUrl(page: Int, query: String, filters: FilterList): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        if (query.isNotBlank()) {
            if (searchPathSegment.isNotEmpty()) {
                addPathSegment(searchPathSegment)
            }
            addQueryParameter(searchKeyword, query)
        } else {
            addPathSegment("filter")
            addPathSegment("")
            filters.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
        }

        addPage(page, this)
    }.build()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.get(searchMangaUrl(page, query, filters))
        return searchMangaParse(response)
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

    open fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select(searchMangaSelector())
            .map(::searchMangaFromElement)

        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(entries, hasNextPage)
    }

    // =========================== Manga Details & Chapters ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val manga = SManga.create().apply { this.url = url.encodedPath }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga.takeIf { it.title.isNotEmpty() }
    }

    private val authorText: String = when (lang) {
        "ja" -> "著者"
        else -> "Authors"
    }

    private val statusText: String = when (lang) {
        "ja" -> "地位"
        else -> "Status"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        parseMangaDetails(document, manga)
        return SMangaUpdate(manga, parseChapterList(document))
    }

    open fun parseMangaDetails(document: Document, manga: SManga) {
        document.selectFirst("#ani_detail")?.run {
            selectFirst(".manga-name")?.let { manga.title = it.ownText() }
            manga.thumbnail_url = selectFirst("img")?.imgAttr()
            manga.genre = select(".genres > a").joinToString { it.ownText() }

            manga.description = buildString {
                selectFirst(".description")?.ownText()?.let { append(it) }
                append("\n\n")
                selectFirst(".manga-name-or")?.ownText()?.let {
                    if (it.isNotEmpty() && it != manga.title) {
                        append("Alternative Title: ")
                        append(it)
                    }
                }
            }.trim()

            select(".anisc-info > .item").forEach { info ->
                when (info.selectFirst(".item-head")?.ownText()) {
                    "$authorText:" -> info.parseAuthorsTo(manga)
                    "$statusText:" -> info.parseStatus(manga)
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

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBeforeLast('#')

    open val chapterIdSelect = "en-chapters"

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(attr("href") + "#${element.attr("data-id")}")
            name = selectFirst(".name")?.text() ?: text()
        }
    }

    open fun parseChapterList(document: Document): List<SChapter> = document.select("#$chapterIdSelect > li.chapter-item").map(::chapterFromElement)

    // =============================== Pages ================================

    open suspend fun getChapterId(chapter: SChapter): String {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.selectFirst("div[data-reading-id]")
            ?.attr("data-reading-id")
            .orEmpty()
            .ifEmpty {
                throw Exception("Unable to retrieve chapter id")
            }
    }

    open fun getAjaxUrl(id: String): String = "$baseUrl/ajax/image/list/$id?mode=vertical"

    open fun pageListParseSelector(): String = ".container-reader-chapter > div > img"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast('#').ifEmpty {
            getChapterId(chapter)
        }

        val ajaxHeaders = headersBuilder()
            .add("Accept", "application/json, text/javascript, */*; q=0.01")
            .set("Referer", URLEncoder.encode(baseUrl + chapter.url.substringBeforeLast("#"), "utf-8"))
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val response = client.get(getAjaxUrl(chapterId), ajaxHeaders)
        val document = response.parseHtmlProperty()

        return document.select(pageListParseSelector()).mapIndexed { index, element ->
            val imgUrl = element.imgAttr().ifEmpty {
                element.selectFirst("img")!!.imgAttr()
            }

            Page(index, imageUrl = imgUrl)
        }
    }

    override fun imageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(
            headers.newBuilder()
                .removeAll("Origin")
                .set("Referer", page.imageUrl!!)
                .build(),
        )
        .build()

    // ============================= Utilities ==============================

    open fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-url") -> attr("abs:data-url")
        else -> attr("abs:src")
    }.trim()

    open fun Response.parseHtmlProperty(): Document {
        val html = parseAs<AjaxResponse>().html
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
    ) : Filter.Group<UriMultiSelectOption>(name, vals.map { UriMultiSelectOption(it.first, it.second) }),
        UriFilter {
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

    protected open fun sortFilterValues(): Array<Pair<String, String>> = arrayOf(
        Pair("Default", "default"),
        Pair("Latest Updated", sortLatestValue),
        Pair("Score", "score"),
        Pair("Name A-Z", "name-az"),
        Pair("Release Date", "release-date"),
        Pair("Most Viewed", sortPopularValue),
    )

    open fun getSortFilter() = SortFilter(sortFilterName, sortFilterParam, sortFilterValues())

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        getSortFilter(),
    )
}
