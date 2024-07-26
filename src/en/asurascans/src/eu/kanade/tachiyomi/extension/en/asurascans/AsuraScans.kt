package eu.kanade.tachiyomi.extension.en.asurascans

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

class AsuraScans : ParsedHttpSource(), ConfigurableSource {

    override val name = "Asura Scans"

    override val baseUrl = "https://asuracomic.net"

    private val apiUrl = "https://gg.asuracomic.net/api"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMMM d yyyy", Locale.US)

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    init {
        // remove legacy preferences
        preferences.run {
            if (contains("pref_url_map")) {
                edit().remove("pref_url_map").apply()
            }
            if (contains("pref_base_url_host")) {
                edit().remove("pref_base_url_host").apply()
            }
            if (contains("pref_permanent_manga_url_2_en")) {
                edit().remove("pref_permanent_manga_url_2_en").apply()
            }
        }
    }

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1, 3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/series?genres=&status=-1&types=-1&order=rating&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/series?genres=&status=-1&types=-1&order=update&page=$page", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()

        url.addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("name", query)
        }

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter(Genre::state)
            .map(Genre::id)
            .joinToString(",")

        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: "-1"
        val types = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: "-1"
        val order = filters.firstInstanceOrNull<OrderFilter>()?.toUriPart() ?: "rating"

        url.addQueryParameter("genres", genres)
        url.addQueryParameter("status", status)
        url.addQueryParameter("types", types)
        url.addQueryParameter("order", order)

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = "div.grid > a[href]"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href").toPermSlugIfNeeded())
        title = element.selectFirst("div.block > span.block")!!.ownText()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun searchMangaNextPageSelector() = "div.flex > a.flex.bg-themecolor:contains(Next)"

    override fun getFilterList(): FilterList {
        fetchFilters()
        val filters = mutableListOf<Filter<*>>()
        if (filtersState == FiltersState.FETCHED) {
            filters += listOf(
                GenreFilter("Genres", getGenreFilters()),
                StatusFilter("Status", getStatusFilters()),
                TypeFilter("Types", getTypeFilters()),
            )
        } else {
            filters += Filter.Header("Press 'Reset' to attempt to fetch the filters")
        }

        filters += OrderFilter(
            "Order by",
            listOf(
                Pair("Rating", "rating"),
                Pair("Update", "update"),
                Pair("Latest", "latest"),
                Pair("Z-A", "desc"),
                Pair("A-Z", "asc"),
            ),
        )

        return FilterList(filters)
    }

    private fun getGenreFilters(): List<Genre> = genresList.map { Genre(it.first, it.second) }
    private fun getStatusFilters(): List<Pair<String, String>> = statusesList.map { it.first to it.second.toString() }
    private fun getTypeFilters(): List<Pair<String, String>> = typesList.map { it.first to it.second.toString() }

    private var genresList: List<Pair<String, Int>> = emptyList()
    private var statusesList: List<Pair<String, Int>> = emptyList()
    private var typesList: List<Pair<String, Int>> = emptyList()

    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val response = client.newCall(GET("$apiUrl/series/filters", headers)).execute()
                val filters = json.decodeFromString<FiltersDto>(response.body.string())

                genresList = filters.genres.filter { it.id > 0 }.map { it.name.trim() to it.id }
                statusesList = filters.statuses.map { it.name.trim() to it.id }
                typesList = filters.types.map { it.name.trim() to it.id }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!preferences.dynamicUrl()) return super.mangaDetailsRequest(manga)
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        val savedSlug = preferences.slugMap[slug] ?: "$slug-"
        return GET("$baseUrl/series/$savedSlug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (preferences.dynamicUrl()) {
            val url = response.request.url.toString()
            val newSlug = url.substringAfter("/series/").substringBefore("/")
            val absSlug = newSlug.substringBeforeLast("-")
            preferences.slugMap = preferences.slugMap.apply { put(absSlug, newSlug) }
        }
        return super.mangaDetailsParse(response)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("span.text-xl.font-bold")!!.ownText()
        thumbnail_url = document.selectFirst("img[alt=poster]")?.attr("abs:src")
        description = document.selectFirst("span.font-medium.text-sm")?.text()
        author = document.selectFirst("div.grid > div:has(h3:eq(0):containsOwn(Author)) > h3:eq(1)")?.ownText()
        artist = document.selectFirst("div.grid > div:has(h3:eq(0):containsOwn(Artist)) > h3:eq(1)")?.ownText()
        genre = document.select("div[class^=space] > div.flex > button.text-white").joinToString { it.ownText() }
        status = parseStatus(document.selectFirst("div.flex:has(h3:eq(0):containsOwn(Status)) > h3:eq(1)")?.ownText())
    }

    private fun parseStatus(status: String?) = when (status) {
        "Ongoing", "Season End" -> SManga.ONGOING
        "Hiatus" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (preferences.dynamicUrl()) {
            val url = response.request.url.toString()
            val newSlug = url.substringAfter("/series/").substringBefore("/")
            val absSlug = newSlug.substringBeforeLast("-")
            preferences.slugMap = preferences.slugMap.apply { put(absSlug, newSlug) }
        }
        return super.chapterListParse(response)
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListSelector() = "div.scrollbar-thumb-themecolor > a.block"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("abs:href").toPermSlugIfNeeded())
        name = element.selectFirst("h3:eq(0)")!!.text()
        date_upload = try {
            val text = element.selectFirst("h3:eq(1)")!!.ownText()
            val cleanText = text.replace(CLEAN_DATE_REGEX, "$1")
            dateFormat.parse(cleanText)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (!preferences.dynamicUrl()) return super.pageListRequest(chapter)
        val match = OLD_FORMAT_CHAPTER_REGEX.containsMatchIn(chapter.url)
        if (match) throw Exception("Please refresh the chapter list before reading.")
        val slug = chapter.url.substringAfter("/series/").substringBefore("/")
        val savedSlug = preferences.slugMap[slug] ?: "$slug-"
        return GET(baseUrl + chapter.url.replace(slug, savedSlug), headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div > img[alt=chapter]").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    private inline fun <reified R> List<*>.firstInstanceOrNull(): R? =
        filterIsInstance<R>().firstOrNull()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DYNAMIC_URL
            title = "Automatically update dynamic URLs"
            summary = "Automatically update random numbers in manga URLs.\nHelps mitigating HTTP 404 errors during update and \"in library\" marks when browsing.\nNote: This setting may require clearing database in advanced settings and migrating all manga to the same source."
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private var SharedPreferences.slugMap: MutableMap<String, String>
        get() {
            val jsonMap = getString(PREF_SLUG_MAP, "{}")!!
            return try {
                json.decodeFromString<Map<String, String>>(jsonMap).toMutableMap()
            } catch (_: Exception) {
                mutableMapOf()
            }
        }
        set(newSlugMap) {
            edit()
                .putString(PREF_SLUG_MAP, json.encodeToString(newSlugMap))
                .apply()
        }

    private fun SharedPreferences.dynamicUrl(): Boolean = getBoolean(PREF_DYNAMIC_URL, true)

    private fun String.toPermSlugIfNeeded(): String {
        if (!preferences.dynamicUrl()) return this
        val slug = this.substringAfter("/series/").substringBefore("/")
        val absSlug = slug.substringBeforeLast("-")
        preferences.slugMap = preferences.slugMap.apply { put(absSlug, slug) }
        return this.replace(slug, absSlug)
    }

    companion object {
        private val CLEAN_DATE_REGEX = """(\d+)(st|nd|rd|th)""".toRegex()
        private val OLD_FORMAT_MANGA_REGEX = """^/manga/(\d+-)?([^/]+)/?$""".toRegex()
        private val OLD_FORMAT_CHAPTER_REGEX = """^/(\d+-)?[^/]*-chapter-\d+(-\d+)*/?$""".toRegex()
        private const val PREF_SLUG_MAP = "pref_slug_map"
        private const val PREF_DYNAMIC_URL = "pref_dynamic_url"
    }
}
