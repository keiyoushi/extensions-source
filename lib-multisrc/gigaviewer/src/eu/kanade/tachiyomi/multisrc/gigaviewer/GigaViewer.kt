package eu.kanade.tachiyomi.multisrc.gigaviewer

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// GigaViewer Sources: https://hatena.co.jp/solutions/gigaviewer
abstract class GigaViewer :
    KeiSource(),
    ConfigurableSource {
    protected open val dayTimeZone = TimeZone.getTimeZone("Asia/Tokyo")!!
    protected open val preferences: SharedPreferences by getPreferencesLazy()
    protected open val dayOfWeek: String by lazy {
        Calendar.getInstance(dayTimeZone)
            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US)!!
            .lowercase(Locale.US)
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addGigaViewerInterceptors()

    protected fun OkHttpClient.Builder.addGigaViewerInterceptors(): OkHttpClient.Builder = addInterceptor(ImageInterceptor())
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

    // Popular
    protected open fun popularMangaUrl(page: Int) = "$baseUrl/series"

    override suspend fun getPopularManga(page: Int) = popularMangaParse(client.get(popularMangaUrl(page)).asJsoup())

    protected open fun popularMangaParse(document: Document): MangasPage {
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
    protected open fun latestUpdatesUrl(page: Int) = popularMangaUrl(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(latestUpdatesUrl(page)).asJsoup()
        val mangas = document.select(latestUpdatesSelector).map(::latestUpdatesFromElement)
        val hasNextPage = latestUpdatesNextPageSelector?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    protected open val latestUpdatesSelector: String = "h2.series-list-date-week.$dayOfWeek + ul.series-list li a"
    protected open val latestUpdatesNextPageSelector: String? = null

    protected open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search
    protected open fun searchMangaUrl(page: Int, query: String, filters: FilterList): HttpUrl {
        if (query.isNotEmpty()) {
            return "$baseUrl/$searchPathSegment".toHttpUrl().newBuilder().apply {
                addQueryParameter("q", query)
                if (page > 1) {
                    addQueryParameter("page", page.toString())
                }
            }.build()
        }

        val path = filters.firstInstance<CollectionFilter>().selected.path
        return "$baseUrl/series".toHttpUrl().newBuilder().apply {
            if (path.isNotBlank()) {
                addPathSegments(path)
            }
        }
            .build()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchMangaUrl(page, query, filters)
        val document = client.get(url).asJsoup()
        if (url.pathSegments.contains(searchPathSegment)) {
            val mangas = document.select(searchMangaSelector).map(::searchMangaFromElement)
            val hasNextPage = searchMangaNextPageSelector?.let { document.selectFirst(it) != null } ?: false
            return MangasPage(mangas, hasNextPage)
        }
        return popularMangaParse(document)
    }

    protected open val searchMangaSelector = "ul.search-series-list li, ul.series-list li"
    protected open val searchPathSegment = "search"
    protected open val searchMangaNextPageSelector: String? = null

    protected open fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.title-box p.series-title")!!.text()
        thumbnail_url = element.selectFirst("div.thmb-container a img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("div.thmb-container a")!!.absUrl("href"))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        return mangaDetailsParse(client.get(url).asJsoup()).apply { setUrlWithoutDomain(url.toString()) }
    }

    // Details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), fetchChapterList(document))
    }

    // Details
    protected open val mangaDetailsInfoSelector: String = "section.series-information div.series-header"

    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.selectFirst(mangaDetailsInfoSelector)!!
        title = infoElement.selectFirst("h1.series-header-title")!!.text()
        author = infoElement.selectFirst("h2.series-header-author")?.text()
        description = infoElement.selectFirst("p.series-header-description")?.text()
        thumbnail_url = infoElement.selectFirst("div.series-header-image-wrapper img")?.absUrl("data-src")
    }

    // Chapters
    protected open suspend fun paginatedChapters(referer: String, aggregateId: String, offset: Int, type: String = "episode"): List<GigaViewerPaginationReadableProduct> {
        val newHeaders = headersBuilder()
            .set("Referer", referer)
            .build()

        val apiUrl = "$baseUrl/api/viewer/pagination_readable_products".toHttpUrl().newBuilder()
            .addQueryParameter("type", type)
            .addQueryParameter("aggregate_id", aggregateId)
            .addQueryParameter("sort_order", "desc")
            .addQueryParameter("offset", offset.toString())
            .build()

        return client.get(apiUrl, newHeaders).parseAs()
    }

    private suspend fun fetchChapterList(document: Document): List<SChapter> {
        val referer = document.location()
        val aggregateId = document.selectFirst("script.js-valve")?.attr("data-giga_series")
            ?: document.selectFirst(".js-readable-products-pagination")!!.attr("data-aggregate-id")
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val hideUnavailable = preferences.getBoolean(HIDE_UNAVAILABLE_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()

        suspend fun fetchChapters(type: String) {
            var offset = 0
            val isVolume = type == "volume"

            // repeat until the offset is too large to return any chapters, resulting in an empty list
            while (true) {
                val resultData = paginatedChapters(referer, aggregateId, offset, type)

                if (resultData.isEmpty()) break

                resultData.asSequence().filter {
                    when (it.status?.label) {
                        "unpublished" -> !hideUnavailable
                        "is_rentable", "is_purchasable", "is_rentable_and_subscribable" -> !hideLocked
                        else -> true
                    }
                }.map {
                    it.toSChapter(isVolume)
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
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Referer", page.url)
        .build()

    // Filters
    override fun getFilterList(data: JsonElement?): FilterList {
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
