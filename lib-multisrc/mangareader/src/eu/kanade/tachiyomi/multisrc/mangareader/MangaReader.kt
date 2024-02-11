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
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
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

abstract class MangaReader(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val sortPopularValue: String = "most-viewed",
    private val sortLatestValue: String = "latest-updated",
) : ParsedHttpSource(), ConfigurableSource {

    val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val supportsLatest = true

    /**
     * Enable if site supports volumes. Must set chapterType and volumeType to a value
     * if enabled.
     */
    protected open val containsVolumes = false

    open val chapterType: String? = null

    open val volumeType: String? = null

    /**
     * When null, a page path segment will be added. Otherwise, it will use
     * the `pageQueryParameter` as a query parameter with page as the value.
     */
    protected open val pageQueryParameter: String? = null

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(sortFilterName, sortFilterParam, sortFilterValues, sortPopularValue)),
        )
    }

    override fun popularMangaSelector() =
        throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element) =
        throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String =
        throw UnsupportedOperationException()

    final override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(sortFilterName, sortFilterParam, sortFilterValues, sortLatestValue)),
        )
    }

    override fun latestUpdatesSelector() =
        throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String =
        throw UnsupportedOperationException()

    final override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // =============================== Search ===============================

    /**
     * Path segment used when searching with a query.
     */
    open val searchPathSegment = "search"

    /**
     * Query parameter used when searching
     */
    open val searchKeyword = "keyword"

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

            if (pageQueryParameter != null) {
                addQueryParameter(pageQueryParameter!!, page.toString())
            } else {
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".manga_list-sbs .manga-poster"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        element.selectFirst("img")!!.let {
            title = it.attr("alt")
            thumbnail_url = it.imgAttr()
        }
    }

    override fun searchMangaNextPageSelector() = "ul.pagination > li.active + li"

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var entries = document.select(searchMangaSelector()).map(::searchMangaFromElement)
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
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
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

    protected open val authorText: String = when (lang) {
        "ja" -> "著者"
        else -> "Authors"
    }

    protected open val statusText: String = when (lang) {
        "ja" -> "地位"
        else -> "Status"
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
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
        "ongoing" -> SManga.ONGOING
        "publishing" -> SManga.ONGOING
        "releasing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "finished" -> SManga.COMPLETED
        "on-hold" -> SManga.ON_HIATUS
        "on_hiatus" -> SManga.ON_HIATUS
        "discontinued" -> SManga.CANCELLED
        "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    /**
     * Method to create request for either chapters or volumes
     */
    open fun chapterListRequest(mangaUrl: String, type: String): Request =
        throw NotImplementedError()

    /**
     * Method to parse elements depending on type
     */
    open fun parseChapterElements(response: Response, isVolume: Boolean): List<Element> =
        throw NotImplementedError()

    open fun updateChapterList(manga: SManga, chapters: List<SChapter>) = Unit

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val path = manga.url
        val isVolume = path.endsWith(VOLUME_URL_SUFFIX)
        val type = if (isVolume) volumeType else chapterType

        val request = if (containsVolumes) {
            chapterListRequest(path.removeSuffix(VOLUME_URL_SUFFIX), type!!)
        } else {
            chapterListRequest(manga)
        }

        val response = client.newCall(request).execute()
        val elements = if (containsVolumes) {
            parseChapterElements(response, isVolume)
        } else {
            val document = response.asJsoup()
            document.select(chapterListSelector())
        }

        val chapters = if (containsVolumes) {
            elements.map { chapterFromElement(it, isVolume) }
        } else {
            elements.map(::chapterFromElement)
        }

        chapters.also {
            if (!isVolume && it.isNotEmpty()) updateChapterList(manga, it)
        }
    }

    override fun chapterListSelector() = "#ja-chaps > li.chapter-item, #chapters-list > li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")!!.run {
            setUrlWithoutDomain(attr("href"))
            name = selectFirst(".name")?.text() ?: text()
        }
    }

    open fun chapterFromElement(element: Element, isVolume: Boolean): SChapter =
        throw NotImplementedError()

    final override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBeforeLast('#')

    // =============================== Pages ================================

    open val pageListParseSelector = ".container-reader-chapter > div > img"

    open val pageListImgSelector = "img"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.parseHtmlProperty()

        val pageList = document.select(pageListParseSelector).mapIndexed { index, element ->
            val imgUrl = element.imgAttr().ifEmpty {
                element.selectFirst(pageListImgSelector)!!.imgAttr()
            }

            Page(index, imageUrl = imgUrl)
        }

        return pageList
    }

    override fun pageListParse(document: Document): List<Page> =
        throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) =
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

    open val sortFilterName: String = when (lang) {
        "ja" -> "選別"
        else -> "Sort"
    }

    open val sortFilterParam = "sort"

    open val sortFilterValues = arrayOf(
        Pair("Most Viewed", sortPopularValue),
        Pair("Latest Updated", sortLatestValue),
    )

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
