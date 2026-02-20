package eu.kanade.tachiyomi.multisrc.gigaviewer

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// GigaViewer Sources: https://hatena.co.jp/solutions/gigaviewer
abstract class GigaViewer(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource(),
    ConfigurableSource {
    protected open val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
    protected open val dayTimeZone = TimeZone.getTimeZone("Asia/Tokyo")!!
    protected open val preferences: SharedPreferences by getPreferencesLazy()
    protected open val dayOfWeek: String by lazy {
        Calendar.getInstance(dayTimeZone)
            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US)!!
            .lowercase(Locale.US)
    }

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor {
            // Search returns 404 when no results are found.
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 404 && request.url.pathSegments.contains(searchPathSegment)) {
                response.close()
                return@addInterceptor response.newBuilder()
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map(::popularMangaFromElement)
        val hasNextPage = popularMangaNextPageSelector?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    protected open val popularMangaSelector: String = "ul.series-list li a"
    protected open val popularMangaNextPageSelector: String? = null

    protected open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2.series-list-title")!!.text()
        thumbnail_url = element.selectFirst("div.series-list-thumb img")?.absUrl("data-src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector).map(::latestUpdatesFromElement)
        val hasNextPage = latestUpdatesNextPageSelector?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    protected open val latestUpdatesSelector: String = "h2.series-list-date-week.$dayOfWeek + ul.series-list li a"
    protected open val latestUpdatesNextPageSelector: String? = null

    protected open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/$searchPathSegment".toHttpUrl().newBuilder().apply {
                addQueryParameter("q", query)
                if (page > 1) {
                    addQueryParameter("page", page.toString())
                }
            }.build()
            return GET(url, headers)
        }

        val path = filters.firstInstance<CollectionFilter>().selected.path
        val url = "$baseUrl/series".toHttpUrl().newBuilder().apply {
            if (path.isNotBlank()) {
                addPathSegments(path)
            }
        }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains(searchPathSegment)) {
            val document = response.asJsoup()
            val mangas = document.select(searchMangaSelector).map(::searchMangaFromElement)
            val hasNextPage = searchMangaNextPageSelector?.let { document.selectFirst(it) != null } ?: false
            return MangasPage(mangas, hasNextPage)
        }
        return popularMangaParse(response)
    }

    protected open val searchMangaSelector = "ul.search-series-list li, ul.series-list li"
    protected open val searchPathSegment = "search"
    protected open val searchMangaNextPageSelector: String? = null

    protected open fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.title-box p.series-title")!!.text()
        thumbnail_url = element.selectFirst("div.thmb-container a img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("div.thmb-container a")!!.absUrl("href"))
    }

    // Details
    protected open val mangaDetailsInfoSelector: String = "section.series-information div.series-header"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val infoElement = document.selectFirst(mangaDetailsInfoSelector)!!
            title = infoElement.selectFirst("h1.series-header-title")!!.text()
            author = infoElement.selectFirst("h2.series-header-author")?.text()
            description = infoElement.selectFirst("p.series-header-description")?.text()
            thumbnail_url = infoElement.selectFirst("div.series-header-image-wrapper img")?.absUrl("data-src")
        }
    }

    // Chapters
    protected open fun paginatedChaptersRequest(referer: String, aggregateId: String, offset: Int, type: String = "episode"): Response {
        val newHeaders = super.headersBuilder()
            .set("Referer", referer)
            .build()

        val apiUrl = "$baseUrl/api/viewer/pagination_readable_products".toHttpUrl().newBuilder()
            .addQueryParameter("type", type)
            .addQueryParameter("aggregate_id", aggregateId)
            .addQueryParameter("sort_order", "desc")
            .addQueryParameter("offset", offset.toString())
            .build()

        val request = GET(apiUrl, newHeaders)
        val response = client.newCall(request).execute()
        return response
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val referer = response.request.url.toString()
        val aggregateId = document.selectFirst("script.js-valve")?.attr("data-giga_series")
            ?: document.selectFirst(".js-readable-products-pagination")!!.attr("data-aggregate-id")
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val hideUnavailable = preferences.getBoolean(HIDE_UNAVAILABLE_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()

        fun fetchChapters(type: String) {
            var offset = 0
            val isVolume = type == "volume"

            // repeat until the offset is too large to return any chapters, resulting in an empty list
            while (true) {
                // make request
                val result = paginatedChaptersRequest(referer, aggregateId, offset, type)
                val resultData = result.parseAs<List<GigaViewerPaginationReadableProduct>>()

                if (resultData.isEmpty()) break

                resultData.asSequence().filter {
                    when (it.status?.label) {
                        "unpublished" -> !hideUnavailable
                        "is_rentable", "is_purchasable", "is_rentable_and_subscribable" -> !hideLocked
                        else -> true
                    }
                }.map {
                    it.toSChapter(dateFormat, isVolume)
                }.toCollection(chapters)

                // increase offset
                offset += resultData.size
            }
        }

        // Fetch both types
        fetchChapters("episode")
        fetchChapters("volume")

        return chapters
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val episode = document.selectFirst("script#episode-json")!!.attr("data-value")
        val results = episode.parseAs<GigaViewerEpisodeDto>()
        val page = results.readableProduct.pageStructure
        if (page == null || page.pages.isEmpty()) {
            throw Exception("This chapter is either unavailable or must be purchased.")
        }

        val isScrambled = page.choJuGiga == "baku"

        return page.pages
            .filter { it.type == "main" && !it.src.isNullOrBlank() }
            .mapIndexed { i, page ->
                val imageUrl = page.src!!.toHttpUrl().newBuilder().apply {
                    if (isScrambled) {
                        fragment("scramble")
                    }
                }.build().toString()
                Page(i, document.location(), imageUrl)
            }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = super.headersBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList {
        val collections = getCollections()
        return if (collections.isNotEmpty()) {
            FilterList(CollectionFilter(collections))
        } else {
            FilterList()
        }
    }

    protected open class Collection(val name: String, val path: String) {
        override fun toString(): String = name
    }

    protected open class CollectionFilter(val collections: List<Collection>) : Filter.Select<Collection>("コレクション", collections.toTypedArray()) {
        open val selected: Collection
            get() = collections[state]
    }

    protected open fun getCollections(): List<Collection> = emptyList()

    // Preferences
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Paid Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_UNAVAILABLE_PREF_KEY
            title = "Hide Unavailable Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val HIDE_UNAVAILABLE_PREF_KEY = "hide_unavailable"
    }
}
