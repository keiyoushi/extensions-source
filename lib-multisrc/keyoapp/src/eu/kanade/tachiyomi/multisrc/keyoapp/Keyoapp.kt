package eu.kanade.tachiyomi.multisrc.keyoapp

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
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class Keyoapp :
    HttpSource(),
    ConfigurableSource {

    protected val preferences: SharedPreferences by getPreferencesLazy()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    protected fun launchIO(block: suspend () -> Unit) = scope.launch { block() }

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("ar", "en", "fr"),
        classLoader = this::class.java.classLoader!!,
    )

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    open val popularMangaTitleSelector = listOf(
        "Popular",
        "Popularie",
        "Trending",
    )

    open fun popularMangaSelector(): String = selector(
        "div:contains(%s) + div .group.overflow-hidden.grid",
        popularMangaTitleSelector,
    )

    open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.getImageUrl("*[style*=background-image]")
        element.selectFirst("a[href]")!!.run {
            title = attr("title")
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    open fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector())
            .withoutNovels()
            .map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/", headers)

    open fun latestUpdatesSelector(): String = "div.grid > div.group"

    open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector())
            .withoutNovels()
            .map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            addPathSegment("")
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            filters.firstInstanceOrNull<TypeList>()?.addCheckedTo(this, "type")
            filters.firstInstanceOrNull<StatusList>()?.addCheckedTo(this, "status")
            filters.firstInstanceOrNull<GenreList>()?.addCheckedTo(this, "genre")
        }.build()

        return GET(url, headers)
    }

    open fun searchMangaSelector() = "#searched_series_page > button"

    open fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        val document = response.asJsoup()

        val query = response.request.url.queryParameter("q") ?: ""
        val genres = response.request.url.queryParameterValues("genre").filterNotNull()
        val types = response.request.url.queryParameterValues("type").filterNotNull()
        val statuses = response.request.url.queryParameterValues("status").filterNotNull()

        val mangaList = document.select(searchMangaSelector())
            .withoutNovels()
            .asSequence()
            .filter { it.attr("title").contains(query, true) }
            .filter { entry ->
                val entryGenres = runCatching {
                    entry.attr("tags").parseAs<List<String>>()
                    // Tags are frequently padded, e.g. `[" Fantasy", "Action "]`
                }.getOrDefault(emptyList()).map(String::trim)
                genres.all { genre -> entryGenres.any { it.equals(genre, true) } }
            }
            .filter { entry -> types.isEmpty() || types.any { it.equals(entry.attr("data-type"), true) } }
            .filter { entry -> statuses.isEmpty() || statuses.any { it.equals(entry.attr("data-status"), true) } }
            .map(::searchMangaFromElement)
            .toList()

        return MangasPage(mangaList, false)
    }

    // ============================== Filters ==============================

    /**
     * Automatically fetched genres from the source to be used in the filters.
     */
    protected var genresList: List<Genre> = emptyList()
        private set

    /**
     * Automatically fetched types from the source to be used in the filters.
     */
    protected var typesList: List<Type> = emptyList()
        private set

    /**
     * Automatically fetched statuses from the source to be used in the filters.
     */
    protected var statusesList: List<Status> = emptyList()
        private set

    /**
     * Inner variable to control the filter fetching failed state.
     */
    private var fetchFiltersFailed: Boolean = false

    /**
     * Whether filters have been successfully fetched at least once.
     */
    private var filtersFetched: Boolean = false

    /**
     * Inner variable to avoid overlapping filter fetches.
     */
    @Volatile
    private var fetchFiltersInProgress: Boolean = false

    /**
     * Inner variable to control how much tries the filters request was called.
     */
    private var fetchFiltersAttempts: Int = 0

    abstract class CheckBoxFilter(name: String, val id: String) : Filter.CheckBox(name)

    class Genre(name: String, id: String = name) : CheckBoxFilter(name, id)

    class Type(name: String, id: String = name) : CheckBoxFilter(name, id)

    class Status(name: String, id: String = name) : CheckBoxFilter(name, id)

    protected class GenreList(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)

    protected class TypeList(title: String, types: List<Type>) : Filter.Group<Type>(title, types)

    protected class StatusList(title: String, statuses: List<Status>) : Filter.Group<Status>(title, statuses)

    /**
     * Single-choice dropdown, for search endpoints that honour only one value per
     * query parameter and so cannot be offered as a checkbox group.
     */
    protected class SelectFilter(name: String, val param: String, private val options: List<CheckBoxFilter>) : Filter.Select<String>(name, (listOf("All") + options.map { it.name }).toTypedArray()) {
        val selectedId: String? get() = options.getOrNull(state - 1)?.id
    }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        return filterListOrHeader(
            buildList {
                if (typesList.isNotEmpty()) add(TypeList("Type", typesList))
                if (statusesList.isNotEmpty()) add(StatusList("Status", statusesList))
                if (genresList.isNotEmpty()) add(GenreList("Genres", genresList))
            },
        )
    }

    /**
     * Filter list for sources that filter server-side and honour only one value per
     * parameter, instead of returning every entry for client-side narrowing.
     */
    protected fun singleSelectFilterList(): FilterList {
        launchIO { fetchGenres() }

        return filterListOrHeader(
            buildList {
                if (typesList.isNotEmpty()) add(SelectFilter("Type", "type", typesList))
                if (statusesList.isNotEmpty()) add(SelectFilter("Status", "status", statusesList))
                if (genresList.isNotEmpty()) add(SelectFilter("Genres", "genre", genresList))
            },
        )
    }

    private fun filterListOrHeader(filters: List<Filter<*>>): FilterList = if (filters.isNotEmpty()) {
        FilterList(filters)
    } else {
        FilterList(
            Filter.Header("Press 'Reset' to attempt to show the filters"),
        )
    }

    /**
     * Fetch the filter options from the source to be used in the filters.
     */
    protected open fun fetchGenres() {
        if (fetchFiltersAttempts >= 3 || fetchFiltersInProgress || filtersFetched) {
            return
        }

        fetchFiltersInProgress = true
        fetchFiltersAttempts++

        try {
            client.newCall(genresRequest()).execute().use { response ->
                val document = response.asJsoup()
                genresList = parseGenres(document)
                typesList = parseTypes(document)
                statusesList = parseStatuses(document)
                fetchFiltersFailed = genresList.isEmpty()
                filtersFetched = genresList.isNotEmpty()
            }
        } catch (_: Exception) {
            fetchFiltersFailed = true
        }

        fetchFiltersInProgress = false
    }

    protected open fun genresRequest(): Request = GET("$baseUrl/series/", headers)

    /**
     * Get the genres from the search page document.
     *
     * @param document The search page document
     */
    protected open fun parseGenres(document: Document): List<Genre> {
        val genres = document.parseDropdown("genre").map { (id, name) -> Genre(name, id) }

        if (genres.isNotEmpty()) {
            return genres
        }

        return document.select("button[menu-logged][name=genre] [menu-options] [menu-option]")
            .map { option ->
                Genre(option.selectFirst("span")?.text().orEmpty(), option.attr("value"))
            }
    }

    /**
     * Get the types from the search page document.
     *
     * @param document The search page document
     */
    protected open fun parseTypes(document: Document): List<Type> = document.parseDropdown("type")
        .map { (id, name) -> Type(name, id) }
        .filterNot { excludeNovels && it.id.equals("novel", true) }

    /**
     * Get the statuses from the search page document.
     *
     * @param document The search page document
     */
    protected open fun parseStatuses(document: Document): List<Status> = document.parseDropdown("status")
        .map { (id, name) -> Status(name, id) }

    /**
     * Whether novel entries should be excluded from source listings.
     */
    protected open val excludeNovels = true

    /**
     * Returns whether a listing element represents a novel.
     *
     * Override this when a site uses different novel markers.
     */
    protected open fun isNovel(element: Element): Boolean {
        if (element.attr("data-type").equals("novel", true)) {
            return true
        }

        if (element.select("span").any { it.ownText().equals("novel", true) }) {
            return true
        }

        val title = element.attr("title")
            .ifEmpty { element.selectFirst("[title]")?.attr("title").orEmpty() }

        return NOVEL_TITLE_REGEX.containsMatchIn(title)
    }

    protected fun Iterable<Element>.withoutNovels(): List<Element> = if (excludeNovels) filterNot(::isNovel) else toList()

    /**
     * The filter options are only available as the payload of an inline
     * `initializeDropdownMenu({ ... })` call, not as regular markup.
     *
     * @return the `value` to `displayName` pairs of the requested dropdown
     */
    private fun Document.parseDropdown(type: String): List<Pair<String, String>> {
        val regex = dropdownItemsRegex(type)
        val items = select("script")
            .firstNotNullOfOrNull { regex.find(it.data()) }
            ?.groupValues?.get(1)
            ?: return emptyList()

        return DROPDOWN_ITEM_REGEX.findAll(items)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
    }

    // ============================== Details ==============================
    protected open val descriptionSelector: String = "#expand_content p"
    protected open val altNameSelector: String = "div.font-medium:containsOwn(Alternative titles) ~ div span"
    protected open val altNamePrefix: String = "${intl["alt_names_heading"]} "
    protected open val statusSelector: String = "div:has(span:containsOwn(Status)) ~ div"
    protected open val authorSelector: String = "div:has(span:containsOwn(Author)) ~ div"
    protected open val artistSelector: String = "div:has(span:containsOwn(Artist)) ~ div"
    protected open val genreSelector: String = "div.grid:has(>h1) > div > a:not([title='Status'])"

    protected open val typeSelector: String = "div:has(span:containsOwn(Type)) ~ div"
    protected open val dateSelector: String = ".text-xs"

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.grid > h1")!!.text()
        thumbnail_url = document.getImageUrl("div[class*=photoURL]")
        status = document.selectFirst(statusSelector).parseStatus()
        author = document.selectFirst(authorSelector)?.text()
        artist = document.selectFirst(artistSelector)?.text()
        genre = buildList {
            document.selectFirst(typeSelector)?.text()?.replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase(Locale.ENGLISH)
                } else {
                    it.toString()
                }
            }?.let(::add)
            document.select(genreSelector).forEach { add(it.text()) }
        }.joinToString()

        val synopsis = document.selectFirst(descriptionSelector)?.text().orEmpty()
        val altNames = document.select(altNameSelector)
            .map { it.text() }
            .filter { it.isNotEmpty() && it != "No alternative titles." }
        description = buildString {
            append(synopsis)
            if (altNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(altNamePrefix.trim())
                append("\n")
                altNames.joinTo(this, "\n") { "- $it" }
            }
        }.takeIf { it.isNotEmpty() }
    }

    protected fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "dropped" -> SManga.CANCELLED
        "paused" -> SManga.ON_HIATUS
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ==============================
    protected open val paidChapterSelector: String = "img[alt~=Coin]"

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(chapterListSelector()).map { chapterFromElement(it) }

    open fun chapterListSelector(): String {
        if (!preferences.showPaidChapters) {
            return "#chapters > a:not(:has(.text-sm span:matches(Upcoming))):not(:has($paidChapterSelector))"
        }
        return "#chapters > a:not(:has(.text-sm span:matches(Upcoming)))"
    }

    open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[href]")!!.attr("abs:href"))
        name = element.selectFirst(".text-sm")!!.text()
        element.selectFirst(dateSelector)?.run {
            date_upload = text().trim().parseDate()
        }
        if (element.select(paidChapterSelector).isNotEmpty()) {
            name = "🔒 $name"
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    open fun pageListParse(document: Document): List<Page> {
        val cdnUrl = getCdnUrl(document)
        document.select("#pages > img")
            .map { it.attr("uid") }
            .filter { it.isNotEmpty() }
            .also { if (it.isNotEmpty() && cdnUrl == null) throw Exception(intl["chapter_page_url_not_found"]) }
            .mapIndexed { index, img ->
                Page(index, url = document.location(), imageUrl = "$cdnUrl/$img")
            }
            .takeIf { it.isNotEmpty() }
            ?.also { return it }

        // Fallback, old method
        return document.select("#pages > img")
            .map { it.imgAttr() }
            .filter { it.contains(oldImgCdnRegex) }
            .mapIndexed { index, img ->
                Page(index, url = document.location(), imageUrl = img)
            }
    }

    protected open fun getCdnUrl(document: Document): String? = document.select("script")
        .firstOrNull { CDN_HOST_REGEX.containsMatchIn(it.html()) }
        ?.let {
            val cdnHost = CDN_HOST_REGEX.find(it.html())
                ?.groups?.get(1)?.value
                ?.replace(CDN_CLEAN_REGEX, "")
            "https://$cdnHost/uploads"
        }

    private val oldImgCdnRegex = Regex("""^(https?:)?//cdn\d*\.keyoapp\.com""")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities =============================

    // From mangathemesia
    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    protected open fun Element.getImageUrl(selector: String): String? = this.selectFirst(selector)?.let { element ->
        IMG_REGEX.find(element.attr("style"))?.groups?.get(1)?.value
            ?.toHttpUrlOrNull()?.let {
                it.newBuilder()
                    .setQueryParameter("w", "480") // Keyoapp returns the dynamic size of the thumbnail to any size
                    .build()
                    .toString()
            }
    }

    private fun String.parseDate(): Long = if (this.contains("ago")) {
        this.parseRelativeDate()
    } else {
        dateFormat.tryParse(this)
    }

    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val relativeDate = this.split(" ").firstOrNull()
            ?.replace("one", "1")
            ?.replace("a", "1")
            ?.toIntOrNull()
            ?: return 0L

        when {
            "second" in this -> now.add(Calendar.SECOND, -relativeDate)

            // parse: 30 seconds ago
            "minute" in this -> now.add(Calendar.MINUTE, -relativeDate)

            // parses: "42 minutes ago"
            "hour" in this -> now.add(Calendar.HOUR, -relativeDate)

            // parses: "1 hour ago" and "2 hours ago"
            "day" in this -> now.add(Calendar.DAY_OF_YEAR, -relativeDate)

            // parses: "2 days ago"
            "week" in this -> now.add(Calendar.WEEK_OF_YEAR, -relativeDate)

            // parses: "2 weeks ago"
            "month" in this -> now.add(Calendar.MONTH, -relativeDate)

            // parses: "2 months ago"
            "year" in this -> now.add(Calendar.YEAR, -relativeDate) // parse: "2 years ago"
        }
        return now.timeInMillis
    }

    private fun selector(selector: String, contains: List<String>): String = contains.joinToString { selector.replace("%s", it) }

    protected fun <T : CheckBoxFilter> Filter.Group<T>.addCheckedTo(builder: HttpUrl.Builder, name: String) {
        state.filter { it.state }.forEach { builder.addQueryParameter(name, it.id) }
    }

    protected fun HttpUrl.Builder.addSelectedTo(filters: FilterList) {
        filters.filterIsInstance<SelectFilter>().forEach { filter ->
            filter.selectedId?.let { addQueryParameter(filter.param, it) }
        }
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
        private val NOVEL_TITLE_REGEX = """(?i)(?:\[\s*novel\s*]|\(\s*novel\s*\))""".toRegex()
        private fun dropdownItemsRegex(type: String) = """type:\s*"$type"\s*,.*?items:\s*\[(.*?)]\s*\}\)""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val DROPDOWN_ITEM_REGEX = """value:\s*"([^"]+)"\s*,\s*displayName:\s*"([^"]+)"""".toRegex()
        val CDN_HOST_REGEX = """realUrl\s*=\s*`[^`]+//([^/]+)""".toRegex()
        val CDN_CLEAN_REGEX = """\$\{[^}]*\}""".toRegex()
        val IMG_REGEX = """url\(['"]?([^(['")])]+)""".toRegex()
    }
}
