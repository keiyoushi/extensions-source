package eu.kanade.tachiyomi.multisrc.mangareader

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * @param sortPopularValue Query parameter value for sorting by popular
 * @param sortLatestValue Query parameter value for sorting by latest
 * @param searchPathSegment Path segment used when searching with a query.
 * @param searchKeyword Query parameter used when searching
 * @param searchMangaSelector Selector for each manga entry
 * @param searchMangaNextPageSelector Selector fo whether a next page exists
 * @param containsVolumes Whether the site supports volumes
 * @param chapterType Value the source uses to denote chapters
 * @param volumeType Value the source uses to denote volumes
 * @param pageListParseSelector Selector for each page
 * @param sortFilterName Name used for the sort filter
 * @param sortFilterParam Query parameter for the sort value
 * @param sortFilterValues Values for each sort
 */
abstract class MangaReader
@Suppress("UNUSED")
constructor(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,

    vararg useNamedArgumentsBelow: Forbidden,

    private val sortPopularValue: String = "most-viewed",
    private val sortLatestValue: String = "latest-updated",
    private val searchPathSegment: String = "search",
    protected val searchKeyword: String = "keyword",
    private val searchMangaSelector: String = ".manga_list-sbs .manga-poster",
    private val searchMangaNextPageSelector: String = "ul.pagination > li.active + li",
    private val containsVolumes: Boolean = false,
    protected val chapterType: String = "chapter",
    protected val volumeType: String = "volume",
    private val pageListParseSelector: String = ".container-reader-chapter > div > img",
    protected val sortFilterName: String = when (lang) {
        "ja" -> "選別"
        else -> "Sort"
    },
    protected val sortFilterParam: String = "sort",
    protected val sortFilterValues: Array<Pair<String, String>> = arrayOf(
        Pair("Most Viewed", sortPopularValue),
        Pair("Latest Updated", sortLatestValue),
    ),
) : HttpSource(), ConfigurableSource {

    val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val supportsLatest = true

    open fun addPage(page: Int, builder: HttpUrl.Builder) {
        builder.addQueryParameter("page", page.toString())
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(sortFilterName, sortFilterParam, sortFilterValues, sortPopularValue)),
        )
    }

    final override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(sortFilterName, sortFilterParam, sortFilterValues, sortLatestValue)),
        )
    }

    final override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // =============================== Search ===============================

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

    open fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        element.selectFirst("img")!!.let {
            title = it.attr("alt")
            thumbnail_url = it.imgAttr()
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var entries = document.select(searchMangaSelector).map(::searchMangaFromElement)
        if (containsVolumes && preferences.getBoolean(SHOW_VOLUME_PREF, false)) {
            entries = entries.flatMapTo(ArrayList(entries.size * 2)) { manga ->
                val volume = SManga.create().apply {
                    url = manga.url + VOLUME_URL_SUFFIX
                    title = VOLUME_TITLE_PREFIX + manga.title
                    thumbnail_url = manga.thumbnail_url
                }
                listOf(manga, volume)
            }
        }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector) != null
        return MangasPage(entries, hasNextPage)
    }

    // =========================== Manga Details ============================

    final override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.removeSuffix(VOLUME_URL_SUFFIX)

    final override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = mangaDetailsParse(document)
        if (response.request.url.fragment == VOLUME_URL_FRAGMENT) {
            manga.title = VOLUME_TITLE_PREFIX + manga.title
        }
        return manga
    }

    private val authorText: String = when (lang) {
        "ja" -> "著者"
        else -> "Authors"
    }

    private val statusText: String = when (lang) {
        "ja" -> "地位"
        else -> "Status"
    }

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
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
        manga.status = this.selectFirst(".name")?.ownText().getStatus()
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

    /**
     * Method to create request for either chapters or volumes
     */
    abstract fun chapterListRequest(mangaUrl: String, type: String): Request

    /**
     * Method to parse elements depending on type
     */
    abstract fun parseChapterElements(response: Response, isVolume: Boolean): List<Element>

    open fun updateChapterList(manga: SManga, chapters: List<SChapter>) = Unit

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val path = manga.url
        val isVolume = path.endsWith(VOLUME_URL_SUFFIX)
        val type = if (isVolume) volumeType else chapterType

        val request = if (containsVolumes) {
            chapterListRequest(path.removeSuffix(VOLUME_URL_SUFFIX), type)
        } else {
            chapterListRequest(manga)
        }

        val response = client.newCall(request).execute()
        val chapters = chapterListParse(response, isVolume)

        chapters.also {
            if (!isVolume && it.isNotEmpty()) updateChapterList(manga, it)
        }
    }

    private fun chapterListParse(response: Response, isVolume: Boolean): List<SChapter> {
        return if (containsVolumes) {
            parseChapterElements(response, isVolume).map {
                chapterFromElement(it, isVolume)
            }
        } else {
            chapterListParse(response)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector).map(::chapterFromElement)
    }

    private val chapterListSelector = "#ja-chaps > li.chapter-item, #chapters-list > li"

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(attr("href"))
            name = selectFirst(".name")?.text() ?: text()
        }
    }

    abstract fun chapterFromElement(element: Element, isVolume: Boolean): SChapter

    final override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBeforeLast('#')

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.parseHtmlProperty()

        val pageList = document.select(pageListParseSelector).mapIndexed { index, element ->
            val imgUrl = element.imgAttr().ifEmpty {
                element.selectFirst("img")!!.imgAttr()
            }

            Page(index, imageUrl = imgUrl)
        }

        return pageList
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (containsVolumes) {
            SwitchPreferenceCompat(screen.context).apply {
                key = SHOW_VOLUME_PREF
                title = "Show volume entries in search result"
                setDefaultValue(false)
            }.let(screen::addPreference)
        }
    }

    companion object {
        private const val SHOW_VOLUME_PREF = "show_volume"

        private const val VOLUME_URL_FRAGMENT = "vol"
        private const val VOLUME_URL_SUFFIX = "#$VOLUME_URL_FRAGMENT"
        private const val VOLUME_TITLE_PREFIX = "[VOL] "
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

    open fun getSortFilter() = SortFilter(sortFilterName, sortFilterParam, sortFilterValues)

    override fun getFilterList(): FilterList = FilterList(
        getSortFilter(),
    )

    // ============================= Utilities ==============================

    open fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    open fun Response.parseHtmlProperty(): Document {
        val html = Json.parseToJsonElement(body.string()).jsonObject["html"]!!.jsonPrimitive.content
        return Jsoup.parseBodyFragment(html)
    }
}
