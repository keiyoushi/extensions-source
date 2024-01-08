package eu.kanade.tachiyomi.extension.en.tapastic

import android.app.Application
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Tapastic : ConfigurableSource, ParsedHttpSource() {

    // Originally Tapastic
    override val id = 3825434541981130345

    override val name = "Tapas"

    override val lang = "en"

    override val baseUrl = "https://tapas.io"

    override val supportsLatest = true

    private val webViewCookieManager: CookieManager by lazy { CookieManager.getInstance() }

    override val client: OkHttpClient = super.client.newBuilder()
        .cookieJar(
            // Syncs okhttp with webview cookies, allowing logged-in users do logged-in stuff
            object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    for (cookie in cookies) {
                        webViewCookieManager.setCookie(url.toString(), cookie.toString())
                    }
                }
                override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
                    val cookiesString = webViewCookieManager.getCookie(url.toString())

                    if (cookiesString != null && cookiesString.isNotEmpty()) {
                        val cookieHeaders = cookiesString.split("; ").toList()
                        val cookies = mutableListOf<Cookie>()
                        for (header in cookieHeaders) {
                            cookies.add(Cookie.parse(url, header)!!)
                        }
                        // Adds age verification cookies to access mature comics
                        return cookies.apply {
                            add(
                                Cookie.Builder()
                                    .domain("tapas.io")
                                    .path("/")
                                    .name("birthDate")
                                    .value("1994-01-01")
                                    .build(),
                            )
                            add(
                                Cookie.Builder()
                                    .domain("tapas.io")
                                    .path("/")
                                    .name("adjustedBirthDate")
                                    .value("1994-01-01")
                                    .build(),
                            )
                        }
                    } else {
                        return mutableListOf()
                    }
                }
            },
        )
        .addInterceptor(TextInterceptor())
        .build()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "https://m.tapas.io")
        .set("User-Agent", USER_AGENT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val chapterVisibilityPref = SwitchPreferenceCompat(screen.context).apply {
            key = CHAPTER_VIS_PREF_KEY
            title = "Show paywalled chapters"
            summary = "Tapas requires login/payment for some chapters. Enable to always show paywalled chapters."
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(CHAPTER_VIS_PREF_KEY, checkValue).commit()
            }
        }
        screen.addPreference(chapterVisibilityPref)

        val lockPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCK_PREF_KEY
            title = "Show lock icon"
            summary = "Enable to continue showing \uD83D\uDD12 for locked chapters after login."
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(SHOW_LOCK_PREF_KEY, checkValue).commit()
            }
        }
        screen.addPreference(lockPref)

        val authorsNotesPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_AUTHORS_NOTES_KEY
            title = "Show author's notes"
            summary = "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(SHOW_AUTHORS_NOTES_KEY, checkValue).commit()
            }
        }
        screen.addPreference(authorsNotesPref)
    }

    private fun showLockedChapterPref() = preferences.getBoolean(CHAPTER_VIS_PREF_KEY, false)
    private fun showLockPref() = preferences.getBoolean(SHOW_LOCK_PREF_KEY, false)
    private fun showAuthorsNotesPref() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)

    // Popular

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/comics?b=POPULAR&g=0&f=NONE&pageNumber=$page&pageSize=20&", headers)

    override fun popularMangaNextPageSelector() = "div[data-has-next=true]"
    override fun popularMangaSelector() = "li.js-list-item"
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        url = element.select(".item__thumb a").attr("href")
        title = toTitle(element.select(".item__thumb img").attr("alt"))
        thumbnail_url = element.select(".item__thumb img").attr("src")
    }

    private class Genre {
        companion object {
            val genreArray = arrayOf(
                "Any",
                "Action",
                "BL",
                "Comedy",
                "Drama",
                "Fantasy",
                "GL",
                "Gaming",
                "Horror",
                "LGBTQ+",
                "Mystery",
                "Romance",
                "Science fiction",
                "Slice of life",
            )
        }
    }

    private fun toTitle(str: String): String {
        for (genre in Genre.genreArray) {
            val extraTitle = ("Tapas " + genre.plus(" "))
            if (str.contains(extraTitle, ignoreCase = true)) {
                return str.replace(extraTitle, "")
            }
        }
        return str
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/comics?b=FRESH&g=0&f=NONE&pageNumber=$page&pageSize=20&", headers)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val url: HttpUrl.Builder
        // If there is any search text, use text search, ignoring filters
        if (query.isNotBlank()) {
            url = "$baseUrl/search".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("t", "COMICS")
        } else {
            // Checking mature filter
            val matureFilter = filterList.find { it is MatureFilter } as MatureFilter
            if (matureFilter.state) {
                url = "$baseUrl/mature".toHttpUrlOrNull()!!.newBuilder()
                // Append only mature uri filters
                filterList.forEach {
                    if (it is UriFilter && it.isMature) {
                        it.addToUri(url)
                    }
                }
            } else {
                url = "$baseUrl/comics".toHttpUrlOrNull()!!.newBuilder()
                // Append only non-mature uri filters
                filterList.forEach {
                    if (it is UriFilter && !it.isMature) {
                        it.addToUri(url)
                    }
                }
            }
        }
        // Append sort if category = ALL
        if (url.toString().contains("b=ALL")) {
            val sortFilter = filterList.find { it is SortFilter } as SortFilter
            sortFilter.addToUri(url)
        }
        // Append page number
        url.addQueryParameter("pageNumber", page.toString())
        return GET(url.toString(), headers)
    }

    override fun searchMangaNextPageSelector() =
        "${popularMangaNextPageSelector()}, a.paging__button--next"

    override fun searchMangaSelector() = "${popularMangaSelector()}, .search-item-wrap"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.select(".item__thumb a, .title-section .title a").attr("href")
        title = toTitle(element.select(".item__thumb img").firstOrNull()?.attr("alt") ?: element.select(".title-section .title a").text())
        thumbnail_url = element.select(".item__thumb img, .thumb-wrap img").attr("src")
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + "${manga.url}/info", headers)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        genre = document.select("div.info-detail__row a.genre-btn").joinToString { it.text() }
        title = document.select("div.title-wrapper a.title").text()
        thumbnail_url = document.select("div.thumb-wrapper img").attr("abs:src")
        author = document.select("ul.creator-section a.name").joinToString { it.text() }
        artist = author
        status = document.select("div.schedule span.schedule-label").text().toStatus()
        val announcementName: String? = document.select("div.series-announcement div.announcement__text p").text()

        if (announcementName!!.contains("Hiatus")) {
            status = SManga.ON_HIATUS
            description = document.select("div.row-body span.description__body").text()
        } else {
            val announcementText: String? = document.select("div.announcement__body p.js-announcement-text").text()
            description = if (announcementName.isNullOrEmpty() || announcementText.isNullOrEmpty()) {
                document.select("div.row-body span.description__body").text()
            } else {
                announcementName.plus("\n") + announcementText.plus("\n\n") + document.select("div.row-body span.description__body").text()
            }
        }
    }

    private fun String.toStatus() = when {
        this.contains("Updates", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    /**
     * Checklist: Paginated chapter lists, locked chapters, future chapters, early-access chapters (app only?), chapter order
     */

    private val json: Json by injectLazy()

    private fun Element.isLockedChapter(): Boolean {
        return this.hasClass("js-have-to-sign") || (showLockPref() && this.hasClass("js-locked"))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.select("div.info-body__bottom a").attr("data-id")
        val chapters = mutableListOf<SChapter>()

        // recursively build the chapter list
        fun parseChapters(page: Int) {
            val url = "$baseUrl/series/$mangaId/episodes?page=$page&sort=NEWEST&init_load=0&large=true&last_access=0&"
            val jsonResponse = client.newCall(GET(url, headers)).execute()
            val json = json.parseToJsonElement(jsonResponse.body.string()).jsonObject["data"]!!.jsonObject

            Jsoup.parse(json["body"]!!.jsonPrimitive.content).select(chapterListSelector())
                .let { list ->
                    // show/don't show locked chapters based on user's preferences
                    if (showLockedChapterPref()) list else list.filterNot { it.isLockedChapter() }
                }
                .map { chapters.add(chapterFromElement(it)) }

            val hasNextPage = json["pagination"]!!.jsonObject["has_next"]!!.jsonPrimitive.boolean
            val nextPage = json["pagination"]!!.jsonObject["page"]!!.jsonPrimitive.int
            if (hasNextPage) parseChapters(nextPage)
        }

        parseChapters(1)
        return chapters
    }

    override fun chapterListSelector() = "li a:not(.js-early-access):not(.js-coming-soon)"

    private val datePattern = Regex("""\w\w\w \d\d, \d\d\d\d""")

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val episode = element.select("p.scene").text()
        val chName = element.select("span.title__body").text()
        name = (if (element.isLockedChapter()) "\uD83D\uDD12 " else "") + "$episode | $chName"
        setUrlWithoutDomain(element.attr("href"))
        date_upload = datePattern.find(element.select("p.additional").text())?.value.toDate()
    }

    private fun String?.toDate(): Long {
        this ?: return 0L
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(this)?.time ?: 0L
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        var pages = document.select("img.content__img").mapIndexed { i, img ->
            Page(i, "", img.let { if (it.hasAttr("data-src")) it.attr("abs:data-src") else it.attr("abs:src") })
        }

        if (showAuthorsNotesPref()) {
            val episodeStory = document.select("p.js-episode-story").html()

            if (episodeStory.isNotEmpty()) {
                val creator = document.select("a.name.js-fb-tracking")[0].text()

                pages = pages + Page(
                    pages.size,
                    "",
                    TextInterceptorHelper.createUrl(creator, episodeStory),
                )
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("This method should not be called!")

    // Filters

    override fun getFilterList() = FilterList(
        // Tapastic does not support genre filtering and text search at the same time
        Filter.Header("NOTE: All filters ignored if using text search!"),
        Filter.Separator(),
        Filter.Header("Sort: Only applied when category is All"),
        SortFilter(),
        Filter.Separator(),
        CategoryFilter(),
        GenreFilter(),
        StatusFilter(),
        Filter.Separator(),
        Filter.Header("Mature filters"),
        MatureFilter("Show Mature Results Only"),
        MatureCategoryFilter(),
        MatureGenreFilter(),
    )

    private class CategoryFilter : UriSelectFilter(
        "Category",
        false,
        "b",
        arrayOf(
            Pair("ALL", "All"),
            Pair("POPULAR", "Popular"),
            Pair("TRENDING", "Trending"),
            Pair("FRESH", "Fresh"),
            Pair("BINGE", "Binge"),
            Pair("ORIGINAL", "Tapas Originals"),
        ),
        defaultValue = 1,
    )

    private class GenreFilter : UriSelectFilter(
        "Genre",
        false,
        "g",
        arrayOf(
            Pair("0", "Any"),
            Pair("7", "Action"),
            Pair("22", "Boys Love"),
            Pair("2", "Comedy"),
            Pair("8", "Drama"),
            Pair("3", "Fantasy"),
            Pair("24", "Girls Love"),
            Pair("9", "Gaming"),
            Pair("6", "Horror"),
            Pair("25", "LGBTQ+"),
            Pair("10", "Mystery"),
            Pair("5", "Romance"),
            Pair("4", "Science Fiction"),
            Pair("1", "Slice of Life"),
        ),
    )

    private class StatusFilter : UriSelectFilter(
        "Status",
        false,
        "f",
        arrayOf(
            Pair("NONE", "All"),
            Pair("F2R", "Free to read"),
            Pair("PRM", "Premium"),
        ),
    )

    private class MatureFilter(name: String) : Filter.CheckBox(name)

    private class MatureCategoryFilter : UriSelectFilter(
        "Category",
        true,
        "b",
        arrayOf(
            Pair("ALL", "All"),
            Pair("POPULAR", "Popular"),
            Pair("FRESH", "Fresh"),
        ),
        defaultValue = 1,
    )

    private class MatureGenreFilter : UriSelectFilter(
        "Genre",
        true,
        "g",
        arrayOf(
            Pair("0", "Any"),
            Pair("5", "Romance"),
            Pair("8", "Drama"),
            Pair("22", "Boys Love"),
            Pair("24", "Girls Love"),
            Pair("2", "Comedy"),
            Pair("6", "Horror"),
        ),
    )

    private class SortFilter(
        name: String = "Sort by",
        var vals: Array<Pair<String, String>> = arrayOf(
            Pair("DATE", "Date"),
            Pair("LIKE", "Likes"),
            Pair("SUBSCRIBE", "Subscribers"),
        ),
        defaultValue: Int = 0,
    ) : Filter.Select<String>(name, vals.map { it.second }.toTypedArray(), defaultValue) {
        fun addToUri(uri: HttpUrl.Builder) {
            uri.addQueryParameter("s", vals[state].first)
        }
    }

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    // vals: <name, display>
    private open class UriSelectFilter(
        displayName: String,
        override val isMature: Boolean,
        val uriParam: String,
        val vals: Array<Pair<String, String>>,
        val firstIsUnspecified: Boolean = false,
        defaultValue: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue),
        UriFilter {
        override fun addToUri(uri: HttpUrl.Builder) {
            if (state != 0 || !firstIsUnspecified) {
                uri.addQueryParameter(uriParam, vals[state].first)
            }
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        val isMature: Boolean
        fun addToUri(uri: HttpUrl.Builder)
    }

    companion object {
        private const val CHAPTER_VIS_PREF_KEY = "lockedChapterVisibility"
        private const val SHOW_LOCK_PREF_KEY = "showChapterLock"
        private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:105.0) Gecko/20100101 Firefox/105.0"
    }
}
