package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class XAsiatAlbums : ParsedHttpSource() {
    override val baseUrl = "https://www.xasiat.com/albums"
    override val lang = "all"
    override val name = "XAsiat Albums"
    override val supportsLatest = true

    private val mainUrl = "https://www.xasiat.com"

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Latest
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesRequest(page: Int) = searchQuery("albums/", "list_albums_common_albums_list", page, mapOf(Pair("sort_by", "post_date")))
    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun mangaDetailsRequest(manga: SManga) = GET("${mainUrl}${manga.url}", headers)

    // Popular
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.attr("title")
        thumbnail_url = element.select(".thumb").attr("data-original")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override fun popularMangaNextPageSelector(): String = ".load-more"

    override fun popularMangaRequest(page: Int) = searchQuery("albums/", "list_albums_common_albums_list", page, mapOf(Pair("sort_by", "album_viewed_week")))

    private fun searchQuery(path: String, blockId: String, page: Int, others: Map<String, String>): Request {
        return GET(
            mainUrl.toHttpUrl().newBuilder().apply {
                addPathSegments(path)
                addQueryParameter("mode", "async")
                addQueryParameter("function", "get_block")
                addQueryParameter("block_id", blockId)
                addQueryParameter("from", page.toString())
                others.forEach { addQueryParameter(it.key, it.value) }
                addQueryParameter("_", System.currentTimeMillis().toString())
            }.build(),
            headers,
        )
    }
    override fun popularMangaSelector(): String = ".list-albums a"

    // Search
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val categoryFilter = filterList.findInstance<UriPartFilter>()
        return when {
            query.isNotEmpty() -> searchQuery("search/search/", "list_albums_albums_list_search_result", page, mutableMapOf(Pair("q", query), Pair("from_albums", page.toString())))
            categoryFilter?.state != 0 -> searchQuery(categoryFilter!!.toUriPart(), "list_albums_common_albums_list", page, mutableMapOf(Pair("q", query)))
            else -> latestUpdatesRequest(page)
        }
    }
    override fun searchMangaSelector() = popularMangaSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select(".entry-title").text()
            description = document.select("meta[og:description]").attr("og:description")
            genre = getTags(document).joinToString(", ")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    private fun getTags(document: Element): List<String> {
        return document.select(".info-content a").map { a ->
            val link = a.attr("href").split(".com/")[1]
            val tag = a.text()
            if (tag.isNotEmpty()) {
                categories[tag] = link
            }
            tag
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(
            Request.Builder().apply {
                url(manga.thumbnail_url.toString())
                method("HEAD", null)
            }.build(),
        ).asObservableSuccess().map { response ->
            val lastModified = response.headers["last-modified"]
            listOf(
                SChapter.create().apply {
                    url = "${mainUrl}${manga.url}"
                    name = "Photobook"
                    date_upload = getDate(lastModified.toString())
                },
            )
        }
    }

    override fun chapterListSelector() = ""
    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.url)

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("a.item").mapIndexed { i, it ->
            Page(i, imageUrl = it.attr("href"))
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("NOTE: Only one filter will be applied!"),
            Filter.Separator(),
            UriPartFilter("Category", categories.entries.toTypedArray()),
        )
        return FilterList(filters)
    }

    open class UriPartFilter(
        displayName: String,
        private val valuePair: Array<MutableMap.MutableEntry<String, String>>,
    ) : Filter.Select<String>(displayName, valuePair.map { it.key }.toTypedArray()) {
        fun toUriPart() = valuePair[state].value
    }

    private var categories = mutableMapOf(
        Pair("All", "albums"),
        Pair("Gravure Idols", "albums/categories/gravure-idols"),
        Pair("JAV & AV Models", "albums/categories/jav"),
        Pair("South Korea", "albums/categories/korea"),
        Pair("China & Taiwan", "albums/categories/china-taiwan"),
        Pair("Amateur", "albums/categories/amateur3"),
        Pair("Western Girls", "albums/categories/western-girls"),
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun getDate(str: String): Long {
        return try {
            DATE_FORMAT.parse(str)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        }
    }
}
