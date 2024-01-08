package eu.kanade.tachiyomi.multisrc.mangathemesia

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// Formerly WPMangaStream & WPMangaReader -> MangaThemesia
abstract class MangaThemesia(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    val mangaUrlDirectory: String = "/manga",
    val dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
) : ParsedHttpSource(), ConfigurableSource {

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    protected open val json: Json by injectLazy()

    override val supportsLatest = true

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .setRandomUserAgent(
                preferences.getPrefUAType(),
                preferences.getPrefCustomUA(),
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    open val projectPageString = "/project"

    // Popular (Search with popular order and nothing else)
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList(OrderByFilter("popular")))
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest (Search with update order and nothing else)
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(OrderByFilter("update")))
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX).not()) return super.fetchSearchManga(page, query, filters)

        val mangaPath = try {
            mangaPathFromUrl(query.substringAfter(URL_SEARCH_PREFIX))
                ?: return Observable.just(MangasPage(emptyList(), false))
        } catch (e: Exception) {
            return Observable.error(e)
        }

        return fetchMangaDetails(
            SManga.create()
                .apply { this.url = "$mangaUrlDirectory/$mangaPath/" },
        )
            .map {
                // Isn't set in returned manga
                it.url = "$mangaUrlDirectory/$mangaPath/"
                MangasPage(listOf(it), false)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaUrlDirectory.substring(1))
            .addQueryParameter("title", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    url.addQueryParameter("status", filter.selectedValue())
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.selectedValue())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.selectedValue())
                }
                is GenreListFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach {
                            val value = if (it.state == Filter.TriState.STATE_EXCLUDE) "-${it.value}" else it.value
                            url.addQueryParameter("genre[]", value)
                        }
                }
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.selectedValue() == "project-filter-on") {
                        url.setPathSegment(0, projectPageString.substring(1))
                    }
                }
                else -> { /* Do Nothing */ }
            }
        }
        url.addPathSegment("")
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (genrelist == null) {
            genrelist = parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        }

        return super.searchMangaParse(response)
    }

    override fun searchMangaSelector() = ".utao .uta .imgu, .listupd .bs .bsx, .listo .bs .bsx"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").imgAttr()
        title = element.select("a").attr("title")
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    override fun searchMangaNextPageSelector() = "div.pagination .next, div.hpage .r"

    // Manga details
    open val seriesDetailsSelector = "div.bigcontent, div.animefull, div.main-info, div.postbody"
    open val seriesTitleSelector = "h1.entry-title"
    open val seriesArtistSelector = ".infotable tr:contains(artist) td:last-child, .tsinfo .imptdt:contains(artist) i, .fmed b:contains(artist)+span, span:contains(artist)"
    open val seriesAuthorSelector = ".infotable tr:contains(author) td:last-child, .tsinfo .imptdt:contains(author) i, .fmed b:contains(author)+span, span:contains(author)"
    open val seriesDescriptionSelector = ".desc, .entry-content[itemprop=description]"
    open val seriesAltNameSelector = ".alternative, .wd-full:contains(alt) span, .alter, .seriestualt"
    open val seriesGenreSelector = "div.gnr a, .mgen a, .seriestugenre a, span:contains(genre)"
    open val seriesTypeSelector = ".infotable tr:contains(type) td:last-child, .tsinfo .imptdt:contains(type) i, .tsinfo .imptdt:contains(type) a, .fmed b:contains(type)+span, span:contains(type) a, a[href*=type\\=]"
    open val seriesStatusSelector = ".infotable tr:contains(status) td:last-child, .tsinfo .imptdt:contains(status) i, .fmed b:contains(status)+span span:contains(status)"
    open val seriesThumbnailSelector = ".infomanga > div[itemprop=image] img, .thumb img"

    open val altNamePrefix = "Alternative Name: "

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst(seriesDetailsSelector)?.let { seriesDetails ->
            title = seriesDetails.selectFirst(seriesTitleSelector)?.text().orEmpty()
            artist = seriesDetails.selectFirst(seriesArtistSelector)?.ownText().removeEmptyPlaceholder()
            author = seriesDetails.selectFirst(seriesAuthorSelector)?.ownText().removeEmptyPlaceholder()
            description = seriesDetails.select(seriesDescriptionSelector).joinToString("\n") { it.text() }.trim()
            // Add alternative name to manga description
            val altName = seriesDetails.selectFirst(seriesAltNameSelector)?.ownText().takeIf { it.isNullOrBlank().not() }
            altName?.let {
                description = "$description\n\n$altNamePrefix$altName".trim()
            }
            val genres = seriesDetails.select(seriesGenreSelector).map { it.text() }.toMutableList()
            // Add series type (manga/manhwa/manhua/other) to genre
            seriesDetails.selectFirst(seriesTypeSelector)?.ownText().takeIf { it.isNullOrBlank().not() }?.let { genres.add(it) }
            genre = genres.map { genre ->
                genre.lowercase(Locale.forLanguageTag(lang)).replaceFirstChar { char ->
                    if (char.isLowerCase()) {
                        char.titlecase(Locale.forLanguageTag(lang))
                    } else {
                        char.toString()
                    }
                }
            }
                .joinToString { it.trim() }

            status = seriesDetails.selectFirst(seriesStatusSelector)?.text().parseStatus()
            thumbnail_url = seriesDetails.select(seriesThumbnailSelector).imgAttr()
        }
    }

    protected fun String?.removeEmptyPlaceholder(): String? {
        return if (this.isNullOrBlank() || this == "-" || this == "N/A") null else this
    }

    open fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        this.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        listOf("dropped", "cancelled").any { this.contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // Chapter list
    override fun chapterListSelector() = "div.bxcl li, div.cl li, #chapterlist li, ul li:has(div.chbox):has(div.eph-num)"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }

        // Add timestamp to latest chapter, taken from "Updated On".
        // So source which not provide chapter timestamp will have at least one
        if (chapters.isNotEmpty() && chapters.first().date_upload == 0L) {
            val date = document
                .select(".listinfo time[itemprop=dateModified], .fmed:contains(update) time, span:contains(update) time")
                .attr("datetime")
            if (date.isNotEmpty()) chapters.first().date_upload = parseUpdatedOnDate(date)
        }

        countViews(document)

        return chapters
    }

    private fun parseUpdatedOnDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(date)?.time ?: 0L
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".lch a, .chapternum").text().ifBlank { urlElements.first()!!.text() }
        date_upload = element.selectFirst(".chapterdate")?.text().parseChapterDate()
    }

    protected open fun String?.parseChapterDate(): Long {
        if (this == null) return 0
        return try {
            dateFormat.parse(this)?.time ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // Pages
    open val pageSelector = "div#readerarea img"

    override fun pageListParse(document: Document): List<Page> {
        val chapterUrl = document.location()
        val htmlPages = document.select(pageSelector)
            .filterNot { it.imgAttr().isEmpty() }
            .mapIndexed { i, img -> Page(i, chapterUrl, img.imgAttr()) }

        countViews(document)

        // Some sites also loads pages via javascript
        if (htmlPages.isNotEmpty()) { return htmlPages }

        val docString = document.toString()
        val imageListJson = JSON_IMAGE_LIST_REGEX.find(docString)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        val scriptPages = imageList.mapIndexed { i, jsonEl ->
            Page(i, chapterUrl, jsonEl.jsonPrimitive.content)
        }

        return scriptPages
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    /**
     * Set it to false if you want to disable the extension reporting the view count
     * back to the source website through admin-ajax.php.
     */
    protected open val sendViewCount: Boolean = true

    protected open fun countViewsRequest(document: Document): Request? {
        val wpMangaData = document.select("script:containsData(dynamic_view_ajax)").firstOrNull()
            ?.data() ?: return null

        val postId = CHAPTER_PAGE_ID_REGEX.find(wpMangaData)?.groupValues?.get(1)
            ?: MANGA_PAGE_ID_REGEX.find(wpMangaData)?.groupValues?.get(1)
            ?: return null

        val formBody = FormBody.Builder()
            .add("action", "dynamic_view_ajax")
            .add("post_id", postId)
            .build()

        val newHeaders = headersBuilder()
            .set("Content-Length", formBody.contentLength().toString())
            .set("Content-Type", formBody.contentType().toString())
            .set("Referer", document.location())
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", newHeaders, formBody)
    }

    /**
     * Send the view count request to the sites endpoint.
     *
     * @param document The response document with the wp-manga data
     */
    protected open fun countViews(document: Document) {
        if (!sendViewCount) {
            return
        }

        val request = countViewsRequest(document) ?: return
        runCatching { client.newCall(request).execute().close() }
    }

    // Filters
    protected class AuthorFilter : Filter.Text("Author")

    protected class YearFilter : Filter.Text("Year")

    open class SelectFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
        vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ) {
        fun selectedValue() = vals[state].second
    }

    protected class StatusFilter : SelectFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Dropped", "dropped"),
        ),
    )

    protected class TypeFilter : SelectFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Comic", "Comic"),
        ),
    )

    protected class OrderByFilter(defaultOrder: String? = null) : SelectFilter(
        "Sort By",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
        ),
        defaultOrder,
    )

    protected class ProjectFilter : SelectFilter(
        "Filter Project",
        arrayOf(
            Pair("Show all manga", ""),
            Pair("Show only project manga", "project-filter-on"),
        ),
    )

    protected class Genre(
        name: String,
        val value: String,
        state: Int = STATE_IGNORE,
    ) : Filter.TriState(name, state)

    protected class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    private var genrelist: List<Genre>? = null
    protected open fun getGenreList(): List<Genre> {
        // Filters are fetched immediately once an extension loads
        // We're only able to get filters after a loading the manga directory,
        // and resetting the filters is the only thing that seems to reinflate the view
        return genrelist ?: listOf(Genre("Press reset to attempt to fetch genres", ""))
    }

    open val hasProjectPage = false

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            AuthorFilter(),
            YearFilter(),
            StatusFilter(),
            TypeFilter(),
            OrderByFilter(),
            Filter.Header("Genre exclusion is not available for all sources"),
            GenreListFilter(getGenreList()),
        )
        if (hasProjectPage) {
            filters.addAll(
                mutableListOf<Filter<*>>(
                    Filter.Separator(),
                    Filter.Header("NOTE: Can't be used with other filter!"),
                    Filter.Header("$name Project List page"),
                    ProjectFilter(),
                ),
            )
        }
        return FilterList(filters)
    }

    // Helpers
    /**
     * Given some string which represents an http urlString, returns path for a manga
     * which can be used to fetch its details at "$baseUrl$mangaUrlDirectory/$mangaPath"
     *
     * @param urlString: String
     *
     * @returns Path of a manga, or null if none could be found
     */
    protected open fun mangaPathFromUrl(urlString: String): String? {
        val baseMangaUrl = "$baseUrl$mangaUrlDirectory".toHttpUrl()
        val url = urlString.toHttpUrlOrNull() ?: return null

        val isMangaUrl = (baseMangaUrl.host == url.host && pathLengthIs(url, 2) && url.pathSegments[0] == baseMangaUrl.pathSegments[0])
        if (isMangaUrl) return url.pathSegments[1]

        val potentiallyChapterUrl = pathLengthIs(url, 1)
        if (potentiallyChapterUrl) {
            val response = client.newCall(GET(urlString, headers)).execute()
            if (response.isSuccessful.not()) {
                response.close()
                throw IllegalStateException("HTTP error ${response.code}")
            } else if (response.isSuccessful) {
                val links = response.asJsoup().select("a[itemprop=item]")
                //  near the top of page: home > manga > current chapter
                if (links.size == 3) {
                    val newUrl = links[1].attr("href").toHttpUrlOrNull() ?: return null
                    val isNewMangaUrl = (baseMangaUrl.host == newUrl.host && pathLengthIs(newUrl, 2) && newUrl.pathSegments[0] == baseMangaUrl.pathSegments[0])
                    if (isNewMangaUrl) return newUrl.pathSegments[1]
                }
            }
        }

        return null
    }

    private fun pathLengthIs(url: HttpUrl, n: Int, strict: Boolean = false): Boolean {
        return url.pathSegments.size == n && url.pathSegments[n - 1].isNotEmpty() ||
            (!strict && url.pathSegments.size == n + 1 && url.pathSegments[n].isEmpty())
    }

    private fun parseGenres(document: Document): List<Genre>? {
        return document.selectFirst("ul.genrez")?.select("li")?.map { li ->
            Genre(
                li.selectFirst("label")!!.text(),
                li.selectFirst("input[type=checkbox]")!!.attr("value"),
            )
        }
    }

    protected open fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    protected open fun Elements.imgAttr(): String = this.first()!!.imgAttr()

    // Unused
    override fun popularMangaSelector(): String = throw UnsupportedOperationException("Not used")
    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")
    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    companion object {
        const val URL_SEARCH_PREFIX = "url:"

        // More info: https://issuetracker.google.com/issues/36970498
        @Suppress("RegExpRedundantEscape")
        private val MANGA_PAGE_ID_REGEX = "post_id\\s*:\\s*(\\d+)\\}".toRegex()
        private val CHAPTER_PAGE_ID_REGEX = "chapter_id\\s*=\\s*(\\d+);".toRegex()

        val JSON_IMAGE_LIST_REGEX = "\"images\"\\s*:\\s*(\\[.*?])".toRegex()
    }
}
