package eu.kanade.tachiyomi.extension.all.cosplaytele

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class CosplayTele : ParsedHttpSource() {
    override val baseUrl = "https://cosplaytele.com"
    override val lang = "all"
    override val name = "CosplayTele"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.selectFirst("img")!!.attr("src")
        val linkEl = element.selectFirst("h5 a")!!
        manga.title = linkEl.text()
        manga.setUrlWithoutDomain(linkEl.attr("abs:href"))
        return manga
    }

    override fun latestUpdatesNextPageSelector() = ".next.page-number"
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/")

    override fun latestUpdatesSelector() = "div.box"

    // Popular
    override fun popularMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun popularMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    private val popularPageLimit = 20

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/wp-json/wordpress-popular-posts/v1/popular-posts?offset=${page * popularPageLimit}&limit=$popularPageLimit&range=last7days&embed=true&_embed=wp:featuredmedia&_fields=title,link,_embedded,_links.wp:featuredmedia")
    override fun popularMangaSelector(): String = ""

    override fun popularMangaParse(response: Response): MangasPage {
        val respObject = json.decodeFromString<JsonArray>(response.body.string())
        val mangas = respObject.map { item ->
            SManga.create().apply {
                title = item.jsonObject!!["title"]!!.jsonObject!!["rendered"]!!.jsonPrimitive.content
                thumbnail_url = item.jsonObject!!["_embedded"]!!.jsonObject!!["wp:featuredmedia"]!!.jsonArray[0]!!.jsonObject["source_url"]!!.jsonPrimitive.content
                setUrlWithoutDomain(item.jsonObject!!["link"]!!.jsonPrimitive.content)
            }
        }
        return MangasPage(mangas, mangas.size >= popularPageLimit)
    }

    // Search
    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val categoryFilter = filterList.findInstance<UriPartFilter>()
        return when {
            categoryFilter?.state != 0 -> GET(
                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegments(categoryFilter!!.toUriPart())
                    addPathSegment("page")
                    addPathSegment(page.toString())
                    if (query.isNotEmpty()) {
                        addQueryParameter("s", query)
                    }
                }.build(),
            )
            query.isNotEmpty() -> GET(
                "$baseUrl/page/$page/".toHttpUrl().newBuilder().apply {
                    addQueryParameter("s", query)
                }.build(),
            )
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".entry-title").text()
        manga.description = document.select(".entry-title").text()
        manga.genre = getTags(document).joinToString(", ")
        manga.status = SManga.COMPLETED
        return manga
    }

    private fun getTags(document: Element): List<String> {
        val pattern = """.*/(tag|category)/.*""".toRegex()
        return document.select("#main a").filter { a -> pattern.matches(a.attr("href")) }.map { a ->
            val link = a.attr("href").split(".com/")[1]
            val tag = a.text()
            if (tag.isNotEmpty()) {
                categories[tag] = link
            }
            tag
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("[rel=\"bookmark\"]").attr("href"))
        chapter.name = "Gallery"
        chapter.date_upload = getDate(element.select("time.updated").attr("datetime"))
        return chapter
    }

    override fun chapterListSelector() = "html"

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".gallery-item img").forEachIndexed { i, it ->
            val itUrl = it.attr("src")
            pages.add(Page(i, imageUrl = itUrl))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch { fetchFilters() }
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("NOTE: Only one filter will be applied!"),
            Filter.Separator(),
            UriPartFilter("Category", categories.entries.toTypedArray()),
        )

        if (filtersState == FilterState.Unfetched) {
            filters.add(1, Filter.Header("Use 'reset' to load all filters"))
        }
        return FilterList(filters)
    }

    open class UriPartFilter(
        displayName: String,
        private val valuePair: Array<MutableMap.MutableEntry<String, String>>,
    ) : Filter.Select<String>(displayName, valuePair.map { it.key }.toTypedArray()) {
        fun toUriPart() = valuePair[state].value
    }

    private var categories = mutableMapOf(
        Pair("All", ""),
        Pair("Cosplay Nude", "category/nude"),
        Pair("Cosplay Ero", "category/no-nude"),
        Pair("Cosplay", "category/cosplay"),
    )

    private var filtersState = FilterState.Unfetched
    private var filterAttempts = 0

    private enum class FilterState {
        Fetching, Fetched, Unfetched
    }

    private suspend fun fetchFilters() {
        if (filtersState == FilterState.Unfetched && filterAttempts < 3) {
            filtersState = FilterState.Fetching
            filterAttempts++

            try {
                client.newCall(GET("$baseUrl/explore-categories/", headers))
                    .await()
                    .asJsoup().let { document -> getTags(document) }
                filtersState = FilterState.Fetched
            } catch (e: Exception) {
                Log.e(name, e.stackTraceToString())
                filtersState = FilterState.Unfetched
            }
        }
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun getDate(str: String): Long {
        try {
            val format = str.split("T")[0]
            return DATE_FORMAT.parse(format)?.time ?: 0L
        } catch (e: ParseException) {
            return 0L
        }
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
    }
}
