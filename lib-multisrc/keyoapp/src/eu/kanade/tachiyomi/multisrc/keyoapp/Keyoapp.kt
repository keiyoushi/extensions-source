package eu.kanade.tachiyomi.multisrc.keyoapp

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

abstract class Keyoapp :
    KeiSource(),
    ConfigurableSource {

    protected val preferences = getPreferences()

    open val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("ar", "en", "fr"),
        classLoader = this::class.java.classLoader!!,
    )

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int) = popularMangaParse(client.get(baseUrl).asJsoup())

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

    open fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select(popularMangaSelector()).filter { !it.isNovel() }.map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int) = latestUpdatesParse(client.get("$baseUrl/latest/").asJsoup())

    open fun latestUpdatesSelector(): String = "div.grid > div.group"

    open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector(): String? = null

    open fun latestUpdatesParse(document: Document): MangasPage {
        val mangas = document.select(latestUpdatesSelector()).filter { !it.isNovel() }.map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.getOrNull(1) ?: return null
        return mangaDetailsParse(client.get(url).asJsoup())
    }

    open fun searchUrlBuilder(query: String, page: Int) = "$baseUrl/series".toHttpUrl().newBuilder().apply {
        if (query.isNotBlank()) addQueryParameter("q", query)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchUrlBuilder(query, page).apply {
            filters.filterIsInstance<MultiSelectFilter>().forEach {
                it.addToUri(this)
            }
        }.build()

        return parseSearchManga(client.get(url))
    }

    open fun searchMangaSelector() = "#searched_series_page > button"

    open fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    open fun searchMangaNextPageSelector(): String? = null

    open fun Element.isNovel() = select("""[data-type="novel" i], span:matchesOwn((?i)^novel$)""").isNotEmpty()

    open fun Element.matchesQuery(query: String) = attr("title").contains(query, ignoreCase = true)

    open fun Element.matchesGenres(genres: List<String>): Boolean {
        val tagsAttr = attr("tags").replace("___", "'")
        val entryGenres = runCatching { tagsAttr.parseAs<List<String>>() }.getOrDefault(emptyList())
        return genres.all { genre -> entryGenres.any { it.equals(genre, ignoreCase = true) } }
    }

    open fun Element.matchesTypes(types: List<String>) = types.any { it.equals(attr("data-type"), ignoreCase = true) }

    open fun Element.matchesStatuses(statuses: List<String>) = statuses.any { it.equals(attr("data-status"), ignoreCase = true) }

    open fun parseSearchManga(response: Response): MangasPage {
        val url = response.request.url

        val document = response.asJsoup()
        val mangaList = document.select(searchMangaSelector())
            .filter { entry ->
                !entry.isNovel() &&
                    url.queryParameter("q")?.let { entry.matchesQuery(it) } ?: true &&
                    url.listParams("genre")?.let { entry.matchesGenres(it) } ?: true &&
                    url.listParams("type")?.let { entry.matchesTypes(it) } ?: true &&
                    url.listParams("status")?.let { entry.matchesStatuses(it) } ?: true
            }
            .map(::searchMangaFromElement)

        return MangasPage(mangaList, false)
    }

    // ========================= Details + Chapters ========================

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(doc), chapterListParse(doc))
    }

    protected open val descriptionSelector: String = "#expand_content p"
    protected open val altNameSelector: String = "div.font-medium:containsOwn(Alternative titles) ~ div span"
    protected open val altNamePrefix: String = "${intl["alt_names_heading"]} "
    protected open val statusSelector: String = "div:has(span:containsOwn(Status)) ~ div"
    protected open val authorSelector: String = "div:has(span:containsOwn(Author)) ~ div"
    protected open val artistSelector: String = "div:has(span:containsOwn(Artist)) ~ div"
    protected open val genreSelector: String = "div:has(>h1) a[href*='genre=']"

    protected open val typeSelector: String = "div:has(span:containsOwn(Type)) ~ div"
    protected open val dateSelector: String = ".text-xs"

    open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        title = document.selectFirst("div.grid > h1")!!.text()
        thumbnail_url = document.getImageUrl("div[class*=photoURL], div[style*=photoURL]")
        status = document.selectFirst(statusSelector).parseStatus()
        author = document.selectFirst(authorSelector)?.text()
        artist = document.selectFirst(artistSelector)?.text()
        genre = buildList {
            document.selectFirst(typeSelector)?.text()?.replaceFirstChar {
                it.titlecase(Locale.ENGLISH)
            }?.let(::add)
            document.select(genreSelector).forEach { add(it.text().trim(',', ' ')) }
        }.filter(String::isNotBlank).joinToString()

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

    protected open val paidChapterSelector: String = "img[alt~=Coin]"

    open fun chapterListSelector(): String {
        if (!showPaidChapters) {
            return "#chapters > :is(a, div):not(:has(.text-sm span:matches(Upcoming))):not(:has($paidChapterSelector))"
        }
        return "#chapters > :is(a, div):not(:has(.text-sm span:matches(Upcoming)))"
    }

    open fun chapterListParse(document: Document): List<SChapter> = document.select(chapterListSelector()).map { chapterFromElement(it) }

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

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(getChapterUrl(chapter)).asJsoup())

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

    // ============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = parseGenres(requestGeneres().asJsoup()).toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Map<String, String>>()?.toSortedMap().orEmpty()

        return FilterList(
            listOfNotNull(
                genres.takeIf { it.isNotEmpty() }?.let { GenreFilter(it) },
                getTypeList().takeIf { it.isNotEmpty() }?.let { TypeFilter(it) },
                getStatusList().takeIf { it.isNotEmpty() }?.let { StatusFilter(it) },
            ),
        )
    }

    open suspend fun requestGeneres() = client.get("$baseUrl/series/")

    protected open fun parseGenres(document: Document): Map<String, String> {
        val script = document
            .select("script:containsData(initializeDropdownMenu)")
            .firstOrNull() ?.data()
            ?: return emptyMap()

        val items = script
            .substringAfter("""initializeDropdownMenu({""")
            .substringAfter("""type: "genre",""")
            .substringAfter("items:")
            .substringBefore("]")
            .trim() + "]"

        return QuickJs.create().use {
            it.evaluate("JSON.stringify(Object.fromEntries($items.map(i => [i.displayName, i.value])))")
        }.toString().parseAs<Map<String, String>>()
    }

    open fun getTypeList() = listOf(
        "Manhwa",
        "Manhua",
        "Manga",
        "Mangatoon",
        "Comic",
    ).associateWith { it.lowercase() }

    open fun getStatusList() = listOf(
        "Ongoing",
        "Completed",
        "Dropped",
        "Hiatus",
    ).associateWith { it.lowercase() }

    // ============================= Utilities =============================

    protected fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "dropped" -> SManga.CANCELLED
        "paused" -> SManga.ON_HIATUS
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

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

    open fun String.parseDate(): Long = if (this.contains("ago")) {
        this.parseRelativeDate()
    } else {
        dateFormat.tryParseDate(this)
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

    private fun HttpUrl.listParams(name: String) = queryParameterValues(name).filterNotNull().takeIf { it.isNotEmpty() }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = intl["pref_show_paid_chapter_title"]
            summaryOn = intl["pref_show_paid_chapter_summary_on"]
            summaryOff = intl["pref_show_paid_chapter_summary_off"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    protected val showPaidChapters
        get() = preferences.getBoolean(SHOW_PAID_CHAPTERS_PREF, false)

    companion object {
        private const val SHOW_PAID_CHAPTERS_PREF = "pref_show_paid_chap"
        val CDN_HOST_REGEX = """realUrl\s*=\s*`[^`]+//([^/]+)""".toRegex()
        val CDN_CLEAN_REGEX = """\$\{[^}]*\}""".toRegex()
        val IMG_REGEX = """url\(['"]?([^(['")])]+)""".toRegex()
    }
}
