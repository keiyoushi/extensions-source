package eu.kanade.tachiyomi.multisrc.keyoapp

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.i18n.Intl
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class Keyoapp(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
) : ParsedHttpSource(), ConfigurableSource {

    protected val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("ar", "en", "fr"),
        classLoader = this::class.java.classLoader!!,
    )

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.flex-col div.grid > div.group.border"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.getImageUrl("*[style*=background-image]")
        element.selectFirst("a[href]")!!.run {
            title = attr("title")
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        return super.popularMangaParse(response)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/", headers)

    override fun latestUpdatesSelector(): String = "div.grid > div.group"

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        return super.latestUpdatesParse(response)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            addPathSegment("")
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            filters.firstOrNull { it is GenreList }?.also {
                val filter = it as GenreList
                filter.state
                    .filter { it.state }
                    .forEach { genre ->
                        addQueryParameter("genre", genre.id)
                    }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "#searched_series_page > button"

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        val document = response.asJsoup()

        val query = response.request.url.queryParameter("q") ?: ""
        val genres = response.request.url.queryParameterValues("genre")

        val mangaList = document.select(searchMangaSelector())
            .toTypedArray()
            .filter { it.attr("title").contains(query, true) }
            .filter { entry ->
                val entryGenres = json.decodeFromString<List<String>>(entry.attr("tags"))
                genres.all { genre -> entryGenres.any { it.equals(genre, true) } }
            }
            .map(::searchMangaFromElement)

        return MangasPage(mangaList, false)
    }

    // Filters

    /**
     * Automatically fetched genres from the source to be used in the filters.
     */
    private var genresList: List<Genre> = emptyList()

    /**
     * Inner variable to control the genre fetching failed state.
     */
    private var fetchGenresFailed: Boolean = false

    /**
     * Inner variable to control how much tries the genres request was called.
     */
    private var fetchGenresAttempts: Int = 0

    class Genre(name: String, val id: String = name) : Filter.CheckBox(name)

    protected class GenreList(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)

    override fun getFilterList(): FilterList {
        return if (genresList.isNotEmpty()) {
            FilterList(
                GenreList("Genres", genresList),
            )
        } else {
            FilterList(
                Filter.Header("Press 'Reset' to attempt to show the genres"),
            )
        }
    }

    /**
     * Fetch the genres from the source to be used in the filters.
     */
    protected open fun fetchGenres() {
        if (fetchGenresAttempts <= 3 && (genresList.isEmpty() || fetchGenresFailed)) {
            val genres = runCatching {
                client.newCall(genresRequest()).execute()
                    .use { parseGenres(it.asJsoup()) }
            }

            fetchGenresFailed = genres.isFailure
            genresList = genres.getOrNull().orEmpty()
            fetchGenresAttempts++
        }
    }

    private fun genresRequest(): Request = GET("$baseUrl/series/", headers)

    /**
     * Get the genres from the search page document.
     *
     * @param document The search page document
     */
    protected open fun parseGenres(document: Document): List<Genre> {
        return document.select("#series_tags_page > button")
            .map { btn ->
                Genre(btn.text(), btn.attr("tag"))
            }
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.grid > h1")!!.text()
        thumbnail_url = document.getImageUrl("div[class*=photoURL]")
        description = document.selectFirst("div.grid > div.overflow-hidden > p")?.text()
        status = document.selectFirst("div[alt=Status]").parseStatus()
        author = document.selectFirst("div[alt=Author]")?.text()
        artist = document.selectFirst("div[alt=Artist]")?.text()
        genre = buildList {
            document.selectFirst("div[alt='Series Type']")?.text()?.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(
                        Locale.getDefault(),
                    )
                } else {
                    it.toString()
                }
            }.let(::add)
            document.select("div.grid:has(>h1) > div > a").forEach { add(it.text()) }
        }.joinToString()
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "dropped" -> SManga.CANCELLED
        "paused" -> SManga.ON_HIATUS
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapter list

    override fun chapterListSelector(): String {
        if (!preferences.showPaidChapters) {
            return "#chapters > a:not(:has(.text-sm span:matches(Upcoming))):not(:has(img[src*=Coin.svg]))"
        }
        return "#chapters > a:not(:has(.text-sm span:matches(Upcoming)))"
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[href]")!!.attr("href"))
        name = element.selectFirst(".text-sm")!!.text()
        element.selectFirst(".text-xs")?.run {
            date_upload = text().trim().parseDate()
        }
        if (element.select("img[src*=Coin.svg]").isNotEmpty()) {
            name = "🔒 $name"
        }
    }

    // Image list

    override fun pageListParse(document: Document): List<Page> {
        val cdnUrl = getCdnUrl(document)
        document.select("#pages > img")
            .map { it.attr("uid") }
            .filter { it.isNotEmpty() }
            .also { cdnUrl ?: throw Exception(intl["chapter_page_url_not_found"]) }
            .mapIndexed { index, img ->
                Page(index, document.location(), "$cdnUrl/$img")
            }
            .takeIf { it.isNotEmpty() }
            ?.also { return it }

        // Fallback, old method
        return document.select("#pages > img")
            .map { it.imgAttr() }
            .filter { it.contains(oldImgCdnRegex) }
            .mapIndexed { index, img ->
                Page(index, document.location(), img)
            }
    }

    protected open fun getCdnUrl(document: Document): String? {
        return document.select("script")
            .firstOrNull { CDN_HOST_REGEX.containsMatchIn(it.html()) }
            ?.let {
                val cdnHost = CDN_HOST_REGEX.find(it.html())
                    ?.groups?.get("host")?.value
                    ?.replace(CDN_CLEAN_REGEX, "")
                "https://$cdnHost/uploads"
            }
    }

    private val oldImgCdnRegex = Regex("""^(https?:)?//cdn\d*\.keyoapp\.com""")

    override fun imageUrlParse(document: Document) = ""

    // Utilities

    // From mangathemesia
    private fun Element.imgAttr(): String {
        val url = when {
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("data-src") -> attr("abs:data-src")
            else -> attr("abs:src")
        }
        return url
    }

    protected open fun Element.getImageUrl(selector: String): String? {
        return this.selectFirst(selector)?.let { element ->
            IMG_REGEX.find(element.attr("style"))?.groups?.get("url")?.value
        }
    }

    private fun String.parseDate(): Long {
        return if (this.contains("ago")) {
            this.parseRelativeDate()
        } else {
            try {
                dateFormat.parse(this)!!.time
            } catch (_: ParseException) {
                0L
            }
        }
    }

    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val relativeDate = this.split(" ").firstOrNull()
            ?.replace("one", "1")
            ?.replace("a", "1")
            ?.toIntOrNull()
            ?: return 0L

        when {
            "second" in this -> now.add(Calendar.SECOND, -relativeDate) // parse: 30 seconds ago
            "minute" in this -> now.add(Calendar.MINUTE, -relativeDate) // parses: "42 minutes ago"
            "hour" in this -> now.add(Calendar.HOUR, -relativeDate) // parses: "1 hour ago" and "2 hours ago"
            "day" in this -> now.add(Calendar.DAY_OF_YEAR, -relativeDate) // parses: "2 days ago"
            "week" in this -> now.add(Calendar.WEEK_OF_YEAR, -relativeDate) // parses: "2 weeks ago"
            "month" in this -> now.add(Calendar.MONTH, -relativeDate) // parses: "2 months ago"
            "year" in this -> now.add(Calendar.YEAR, -relativeDate) // parse: "2 years ago"
        }
        return now.timeInMillis
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = intl["pref_show_paid_chapter_title"]
            summaryOn = intl["pref_show_paid_chapter_summary_on"]
            summaryOff = intl["pref_show_paid_chapter_summary_off"]
            setDefaultValue(SHOW_PAID_CHAPTERS_DEFAULT)
        }.also(screen::addPreference)
    }

    protected val SharedPreferences.showPaidChapters: Boolean
        get() = getBoolean(SHOW_PAID_CHAPTERS_PREF, SHOW_PAID_CHAPTERS_DEFAULT)

    companion object {
        private const val SHOW_PAID_CHAPTERS_PREF = "pref_show_paid_chap"
        private const val SHOW_PAID_CHAPTERS_DEFAULT = false
        val CDN_HOST_REGEX = """realUrl\s*=\s*`[^`]+//(?<host>[^/]+)""".toRegex()
        val CDN_CLEAN_REGEX = """\$\{[^}]*\}""".toRegex()
        val IMG_REGEX = """url\(['"]?(?<url>[^(['"\)])]+)""".toRegex()
    }
}
