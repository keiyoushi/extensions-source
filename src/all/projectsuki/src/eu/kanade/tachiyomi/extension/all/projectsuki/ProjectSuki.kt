package eu.kanade.tachiyomi.extension.all.projectsuki

import android.os.Build
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.all.projectsuki.activities.INTENT_BOOK_QUERY_PREFIX
import eu.kanade.tachiyomi.extension.all.projectsuki.activities.INTENT_READ_QUERY_PREFIX
import eu.kanade.tachiyomi.extension.all.projectsuki.activities.INTENT_SEARCH_QUERY_PREFIX
import eu.kanade.tachiyomi.extension.all.projectsuki.activities.ProjectSukiSearchUrlActivity
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * [Project Suki](https://projectsuki.com)
 * [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi)
 * [extension](https://github.com/tachiyomiorg/tachiyomi-extensions)
 *
 * Most of the code should be documented, `@author` KDoc tags are mostly to know
 * who to bother *when necessary*.
 * If you contributed to this extension, be sure to add yourself in an `@author` tag!
 *
 * If you want to understand how this extension works,
 * I recommend first looking at [ProjectSuki], then [DataExtractor],
 * then the rest of the project.
 */
internal inline val EXTENSION_INFO: Nothing get() = error("EXTENSION_INFO")

internal const val SHORT_FORM_ID: String = """ps"""

internal val homepageUrl: HttpUrl = "https://projectsuki.com".toHttpUrl()
internal val homepageUri: URI = homepageUrl.toUri()

/** PATTERN: `https://projectsuki.com/book/<bookid>`  */
internal val bookUrlPattern = PathPattern(
    """book""".toRegex(RegexOption.IGNORE_CASE),
    """(?<bookid>.+)""".toRegex(RegexOption.IGNORE_CASE),
)

/** PATTERN: `https://projectsuki.com/browse/<pagenum>` */
@Suppress("unused")
internal val browsePattern = PathPattern(
    """browse""".toRegex(RegexOption.IGNORE_CASE),
    """(?<pagenum>\d+)""".toRegex(RegexOption.IGNORE_CASE),
)

/**
 * PATTERN: `https://projectsuki.com/read/<bookid>/<chapterid>/<startpage>`
 *
 * `<startpage>` is actually a filter of sorts that will remove pages &lt; `<startpage>`'s value.
 */
internal val chapterUrlPattern = PathPattern(
    """read""".toRegex(RegexOption.IGNORE_CASE),
    """(?<bookid>.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(?<chapterid>.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(?<startpage>.+)""".toRegex(RegexOption.IGNORE_CASE),
)

/**
 * PATTERNS:
 *  - `https://projectsuki.com/images/gallery/<bookid>/thumb`
 *  - `https://projectsuki.com/images/gallery/<bookid>/thumb.<thumbextension>`
 *  - `https://projectsuki.com/images/gallery/<bookid>/<thumbwidth>-thumb`
 *  - `https://projectsuki.com/images/gallery/<bookid>/<thumbwidth>-thumb.<thumbextension>`
 */
internal val thumbnailUrlPattern = PathPattern(
    """images""".toRegex(RegexOption.IGNORE_CASE),
    """gallery""".toRegex(RegexOption.IGNORE_CASE),
    """(?<bookid>.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(?<thumbwidth>\d+-)?thumb(?:\.(?<thumbextension>.+))?""".toRegex(RegexOption.IGNORE_CASE),
)

/** PATTERN: `https://projectsuki.com/images/gallery/<bookid>/<uuid>/<pagenum>` */
internal val pageUrlPattern = PathPattern(
    """images""".toRegex(RegexOption.IGNORE_CASE),
    """gallery""".toRegex(RegexOption.IGNORE_CASE),
    """(?<bookid>.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(?<uuid>.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(?<pagenum>.+)""".toRegex(RegexOption.IGNORE_CASE),
)

/** PATTERN: `https://projectsuki.com/genre/<genre>` */
internal val genreSearchUrlPattern = PathPattern(
    """genre""".toRegex(RegexOption.IGNORE_CASE),
    """(?<genre>.+)""".toRegex(RegexOption.IGNORE_CASE),
)

/** PATTERN: `https://projectsuki.com/group/<groupid>` */
@Suppress("unused")
internal val groupUrlPattern = PathPattern(
    """group""".toRegex(RegexOption.IGNORE_CASE),
    """(?<groupid>.+)""".toRegex(RegexOption.IGNORE_CASE),
)

/**
 * Used on the website when there's an image loading error, could be used in extension.
 */
@Suppress("unused")
internal val emptyImageUrl: HttpUrl = homepageUrl.newBuilder()
    .addPathSegment("images")
    .addPathSegment("gallery")
    .addPathSegment("empty.jpg")
    .build()

/**
 * Removes the [URL's](https://en.wikipedia.org/wiki/URL) host and scheme/protocol,
 * leaving only the path, query and fragment, *without leading `/`*
 *
 * @see URI.relativize
 */
internal val HttpUrl.rawRelative: String?
    get() {
        val uri = toUri()
        val relative = homepageUri.relativize(uri)
        return when {
            uri === relative -> null
            else -> relative.toASCIIString()
        }
    }

internal val reportPrefix: String
    get() = """Error! Report on GitHub (tachiyomiorg/tachiyomi-extensions)"""

/**
 * Simple named exception to differentiate it with all other "unexpected" exceptions.
 * @see unexpectedErrorCatchingLazy
 */
internal class ProjectSukiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Throws a [ProjectSukiException], which will get caught by Tachiyomi: the message will be exposed as a [toast][android.widget.Toast]. */
internal inline fun reportErrorToUser(locationHint: String? = null, message: () -> String): Nothing {
    throw ProjectSukiException(
        buildString {
            append("[")
            append(reportPrefix)
            append("""]: """)
            append(message())
            if (!locationHint.isNullOrBlank()) {
                append(" @$locationHint")
            }
        },
    )
}

/** Used when chapters don't have a [Language][DataExtractor.ChaptersTableColumnDataType.Language] column (if that ever happens). */
internal const val UNKNOWN_LANGUAGE: String = "unknown"

/**
 * Actual Tachiyomi extension, ties everything together.
 *
 * Most of the work happens in [DataExtractor], [ProjectSukiAPI], [ProjectSukiFilters] and [ProjectSukiPreferences].
 *
 * @author Federico d'Alonzo &lt;me@npgx.dev&gt;
 */
@Suppress("unused")
class ProjectSuki : HttpSource(), ConfigurableSource {

    override val name: String = "Project Suki"
    override val baseUrl: String = homepageUri.toASCIIString()
    override val lang: String = "all"
    override val id: Long = 8965918600406781666L

    /** Handles extension preferences found in Extensions &gt; Project Suki &gt; Gear icon */
    private val preferences = ProjectSukiPreferences(id)

    /** See [Kotlinx-Serialization](https://github.com/Kotlin/kotlinx.serialization). */
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = true
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        with(preferences) { screen.configure() }
    }

    /**
     * [OkHttp's](https://square.github.io/okhttp/) [OkHttpClient] that handles network requests and responses.
     *
     * Thanks to Tachiyomi's [NetworkHelper](https://github.com/tachiyomiorg/tachiyomi/blob/58daedc89ee18d04e7af5bab12629680dba4096c/core/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt#L21C12-L21C12)
     * (this is a permalink, check for updated version),
     * most client options are already set as they should be, including the [Cache][okhttp3.Cache].
     */
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .setRandomUserAgent(
            userAgentType = preferences.shared.getPrefUAType(),
            customUA = preferences.shared.getPrefCustomUA(),
        )
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    /**
     * Specify what request will be sent to the server.
     *
     * This specific method returns a [GET](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods)
     * request to be sent to [https://projectsuki.com/browse](https://projectsuki.com/browse).
     *
     * Using the default [HttpSource]'s [Headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers).
     */
    override fun popularMangaRequest(page: Int) = GET(
        homepageUrl.newBuilder()
            .addPathSegment("browse")
            .addPathSegment((page - 1).toString()) // starts at 0
            .build(),
        headers,
    )

    /** Whether or not this extension supports the "Latest" tab. */
    override val supportsLatest: Boolean get() = true

    /** Same concept as [popularMangaRequest], but is sent to [https://projectsuki.com/](https://projectsuki.com/). */
    override fun latestUpdatesRequest(page: Int) = GET(homepageUrl, headers)

    /**
     * Utility to find and apply a filter specified by [T],
     * see [reified](https://kotlinlang.org/docs/inline-functions.html#reified-type-parameters)
     * if you're not familiar with the concept.
     */
    private inline fun <reified T> HttpUrl.Builder.applyPSFilter(
        from: FilterList,
    ): HttpUrl.Builder where T : Filter<*>, T : ProjectSukiFilters.ProjectSukiFilter = apply {
        from.firstNotNullOfOrNull { it as? T }?.run { applyFilter() }
    }

    /**
     * Same concept as [popularMangaRequest], but is sent to [https://projectsuki.com/search](https://projectsuki.com/search).
     * This is the [Full-Site][ProjectSukiFilters.SearchMode.FULL_SITE] variant of search, it *will* return results that have no chapters.
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET(
            homepageUrl.newBuilder()
                .addPathSegment("search")
                .addQueryParameter("page", (page - 1).toString())
                .addQueryParameter("q", query)
                .applyPSFilter<ProjectSukiFilters.Origin>(from = filters)
                .applyPSFilter<ProjectSukiFilters.Status>(from = filters)
                .applyPSFilter<ProjectSukiFilters.Author>(from = filters)
                .applyPSFilter<ProjectSukiFilters.Artist>(from = filters)
                .build(),
            headers,
        )
    }

    /**
     * Handles the server's [Response] that was returned from [popularMangaRequest]'s [Request].
     *
     * Because we asked the server for a webpage, it will return, in the [Request's body][okhttp3.RequestBody],
     * the [html](https://developer.mozilla.org/en-US/docs/Web/HTML) that makes up that page,
     * including any [css](https://developer.mozilla.org/en-US/docs/Web/CSS) and
     * [JavaScript](https://developer.mozilla.org/en-US/docs/Web/JavaScript) in `<script>` tags.
     *
     * NOTE: [Jsoup](https://jsoup.org/) is not a browser, but an HTML parser and manipulator,
     * as such no JavaScript will actually run.
     * The html that you can see from a browser's [dev-tools](https://github.com/firefox-devtools)
     * could be very different from the initial state of the web-page's HTML,
     * especially for pages that use [hydration-heavy](https://en.wikipedia.org/wiki/Hydration_(web_development))
     * [JavaScript frameworks](https://developer.mozilla.org/en-US/docs/Learn/Tools_and_testing/Client-side_JavaScript_frameworks).
     *
     * To see the initial contents of a response, you can use an API tool like [REQBIN](https://reqbin.com/).
     *
     * [SManga]'s url should be in relative form, see [this SO answer](https://stackoverflow.com/a/21828923)
     * for a comprehensive difference between relative and absolute URLs.
     *
     * [SManga]'s thumbnail_url should instead be in absolute form. If possible [it should be set](https://github.com/tachiyomiorg/tachiyomi-extensions/blob/master/CONTRIBUTING.md#popular-manga)
     * at this point to avoid additional server requests. But if that is not possible, [fetchMangaDetails] will be called to fill in the details.
     */
    override fun popularMangaParse(response: Response): MangasPage {
        val document: Document = response.asJsoup()

        val extractor = DataExtractor(document)
        val books: Set<DataExtractor.PSBook> = extractor.books

        val mangas: List<SManga> = books.map { book ->
            SManga.create().apply {
                this.url = book.bookUrl.rawRelative ?: reportErrorToUser { "Could not relativize ${book.bookUrl}" }
                this.title = book.rawTitle
                this.thumbnail_url = book.thumbnail.toUri().toASCIIString()
            }
        }

        return MangasPage(
            mangas = mangas,
            hasNextPage = mangas.size >= 30, // observed max number of results in search,
        )
    }

    /**
     * Very similar to [popularMangaParse].
     *
     * Due to Project Suki's [home page](https://projectsuki.com) design,
     * differentiating between actually-latest chapters, "Trending" and "New additions",
     * is theoretically possible, but would be fragile and possibly error-prone.
     *
     * So we just grab everything in the homepage.
     * [DataExtractor.books] automatically de-duplicates based on [BookID].
     */
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document: Document = response.asJsoup()

        val extractor = DataExtractor(document)
        val books: Set<DataExtractor.PSBook> = extractor.books

        return MangasPage(
            mangas = books.map { book ->
                SManga.create().apply {
                    this.url = book.bookUrl.rawRelative ?: reportErrorToUser { "Could not relativize ${book.bookUrl}" }
                    this.title = book.rawTitle
                    this.thumbnail_url = book.thumbnail.toUri().toASCIIString()
                }
            },
            hasNextPage = false,
        )
    }

    /**
     * Function that is responsible for providing Tachiyomi with an [Observable](https://reactivex.io/documentation/observable.html)
     * that will return a single [MangasPage] (Tachiyomi uses [Observable.single] behind the scenes).
     *
     * Note that you shouldn't use [Observable.never] to convey "there are no mangas",
     * but [Observable.just(MangasPage(emptyList(), false))][Observable.just].
     * Otherwise Tachiyomi will just wait for the Observable forever (or until a timeout).
     *
     * Most of the times you won't need to override this function: [searchMangaRequest] and [searchMangaParse] will suffice.
     * But if you need to replace the default search behaviour (e.g. because of an [Url Activity][ProjectSukiSearchUrlActivity]),
     * you might need to override this function.
     */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val searchMode: ProjectSukiFilters.SearchMode = filters.filterIsInstance<ProjectSukiFilters.SearchModeFilter>()
            .singleOrNull()
            ?.state
            ?.let { ProjectSukiFilters.SearchMode[it] } ?: ProjectSukiFilters.SearchMode.SMART

        fun BookID.toMangasPageObservable(): Observable<MangasPage> {
            val rawSManga = SManga.create().apply {
                url = bookIDToURL().rawRelative ?: reportErrorToUser { "Could not create relative url for bookID: $this" }
            }

            return client.newCall(mangaDetailsRequest(rawSManga))
                .asObservableSuccess()
                .map { response -> mangaDetailsParse(response) }
                .map { manga -> MangasPage(listOf(manga), hasNextPage = false) }
        }

        val queryAsURL: HttpUrl? by unexpectedErrorCatchingLazy { query.toHttpUrlOrNull() ?: """${homepageUri}$query""".toHttpUrlOrNull() }
        val bookUrlMatch by unexpectedErrorCatchingLazy { queryAsURL?.matchAgainst(bookUrlPattern) }
        val readUrlMatch by unexpectedErrorCatchingLazy { queryAsURL?.matchAgainst(chapterUrlPattern) }

        return when {
            // sent by the url activity, might also be because the user entered a query via $ps-search:
            // but that won't really happen unless the user wants to do that
            query.startsWith(INTENT_SEARCH_QUERY_PREFIX) -> {
                val urlQuery = query.removePrefix(INTENT_SEARCH_QUERY_PREFIX)
                if (urlQuery.isBlank()) error("Empty search query!")

                val rawUrl = """${homepageUri.toASCIIString()}/search?$urlQuery"""
                val url = rawUrl.toHttpUrlOrNull() ?: reportErrorToUser { "Invalid search url: $rawUrl" }

                client.newCall(GET(url, headers))
                    .asObservableSuccess()
                    .map { response -> searchMangaParse(response, overrideHasNextPage = false) }
            }

            // sent by the book activity
            query.startsWith(INTENT_BOOK_QUERY_PREFIX) -> {
                val bookid = query.removePrefix(INTENT_BOOK_QUERY_PREFIX)
                if (bookid.isBlank()) error("Empty bookid!")

                bookid.toMangasPageObservable()
            }

            // sent by the read activity
            query.startsWith(INTENT_READ_QUERY_PREFIX) -> {
                val bookid = query.removePrefix(INTENT_READ_QUERY_PREFIX)
                if (bookid.isBlank()) error("Empty bookid!")

                bookid.toMangasPageObservable()
            }

            bookUrlMatch?.doesMatch == true -> {
                val bookid = bookUrlMatch!!["bookid"]!!.value
                if (bookid.isBlank()) error("Empty bookid!")

                bookid.toMangasPageObservable()
            }

            readUrlMatch?.doesMatch == true -> {
                val bookid = readUrlMatch!!["bookid"]!!.value
                if (bookid.isBlank()) error("Empty bookid!")

                bookid.toMangasPageObservable()
            }

            // use result from https://projectsuki.com/api/book/search
            searchMode == ProjectSukiFilters.SearchMode.SMART -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    error(
                        buildString {
                            append("Please enable ")
                            append(ProjectSukiFilters.SearchMode.SIMPLE)
                            append(" Search Mode: ")
                            append(ProjectSukiFilters.SearchMode.SMART)
                            append(" mode requires Android API version >= 24, but ")
                            append(Build.VERSION.SDK_INT)
                            append(" was found!")
                        },
                    )
                }

                client.newCall(ProjectSukiAPI.bookSearchRequest(json, headers))
                    .asObservableSuccess()
                    .map { response -> ProjectSukiAPI.parseBookSearchResponse(json, response) }
                    .map { data -> SmartBookSearchHandler(query, data).mangasPage }
            }

            // use result from https://projectsuki.com/api/book/search
            searchMode == ProjectSukiFilters.SearchMode.SIMPLE -> {
                client.newCall(ProjectSukiAPI.bookSearchRequest(json, headers))
                    .asObservableSuccess()
                    .map { response -> ProjectSukiAPI.parseBookSearchResponse(json, response) }
                    .map { data -> data.simpleSearchMangasPage(query) }
            }

            // use https://projectsuki.com/search
            else -> client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response -> searchMangaParse(response) }
        }
    }

    private fun filterList(vararg sequences: Sequence<Filter<*>>): FilterList {
        return FilterList(sequences.asSequence().flatten().toList())
    }

    /**
     * Should return a fresh [FilterList] containing fresh (new) instances
     * of all the filters you want to be available.
     * Otherwise things like the reset button won't work.
     */
    override fun getFilterList(): FilterList = filterList(
        ProjectSukiFilters.headersSequence(preferences),
        ProjectSukiFilters.filtersSequence(preferences),
        ProjectSukiFilters.footersSequence(preferences),
    )

    /**
     * Very similar to [popularMangaParse].
     *
     * Unfortunately, because projectsuki has a "Next" button even when the next page is empty,
     * it's useless to depend on that, so we just use a simple heuristic to determine
     * if the page contained in the [Response] is the last one.
     *
     * The heuristic *will* fail if the last page has 30 or more entries.
     */
    override fun searchMangaParse(response: Response): MangasPage = searchMangaParse(response, null)

    /** [searchMangaParse] extended with [overrideHasNextPage]. */
    private fun searchMangaParse(response: Response, overrideHasNextPage: Boolean? = null): MangasPage {
        val document = response.asJsoup()

        val extractor = DataExtractor(document)
        val books: Set<DataExtractor.PSBook> = extractor.books

        val mangas = books.map { book ->
            SManga.create().apply {
                this.url = book.bookUrl.rawRelative ?: reportErrorToUser { "Could not relativize ${book.bookUrl}" }
                this.title = book.rawTitle
                this.thumbnail_url = book.thumbnail.toUri().toASCIIString()
            }
        }

        return MangasPage(
            mangas = mangas,
            hasNextPage = overrideHasNextPage ?: (mangas.size >= 30),
        )
    }

    /**
     * Handles the [Response] given by [mangaDetailsRequest]'s [Request].
     * [HttpSource]'s inheritors will have a default [mangaDetailsRequest] that asks for:
     * ```
     * GET(baseUrl + manga.url, headers)
     * ```
     * You can override [mangaDetailsRequest] if this is not the case for you.
     *
     * Fills out all [SManga]'s fields:
     *  - url (relative)
     *  - title
     *  - artist
     *  - author
     *  - description
     *  - genre (comma-separated list of genres)
     *  - status (one of the constants in [SManga.Companion])
     *  - thumbnail_url (absolute)
     *  - update_strategy (enum [UpdateStrategy])
     *
     * Note that you should use [SManga.create] instead of implementing your own version of [SManga].
     */
    override fun mangaDetailsParse(response: Response): SManga {
        val document: Document = response.asJsoup()
        val extractor = DataExtractor(document)

        val data: DataExtractor.PSBookDetails = extractor.bookDetails

        return SManga.create().apply {
            url = data.book.bookUrl.rawRelative ?: reportErrorToUser { "Could not relativize ${data.book.bookUrl}" }
            title = data.book.rawTitle
            thumbnail_url = data.book.thumbnail.toUri().toASCIIString()

            author = data.details[DataExtractor.BookDetail.Author]?.detailData
            artist = data.details[DataExtractor.BookDetail.Artist]?.detailData
            status = when (data.details[DataExtractor.BookDetail.Status]?.detailData?.trim()?.lowercase(Locale.US)) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }

            description = buildString {
                if (data.alertData.isNotEmpty()) {
                    appendLine("Alerts have been found, refreshing the book/manga later might help in removing them.")
                    appendLine()

                    data.alertData.forEach {
                        appendLine(it)
                        appendLine()
                    }

                    appendLine(DESCRIPTION_DIVIDER)
                    appendLine()

                    appendLine()
                }

                appendLine(data.description)
                appendLine()

                appendLine(DESCRIPTION_DIVIDER)
                appendLine()

                data.details.values.forEach { (label, value) ->
                    append(label)
                    append("  ")
                    append(value.trim())

                    appendLine()
                }
            }

            update_strategy = when (status) {
                SManga.CANCELLED, SManga.COMPLETED, SManga.PUBLISHING_FINISHED -> UpdateStrategy.ONLY_FETCH_ONCE
                else -> UpdateStrategy.ALWAYS_UPDATE
            }

            genre = data.details[DataExtractor.BookDetail.Genre]!!.detailData
        }
    }

    /**
     * Handles the [Response] given by [chapterListRequest]'s [Request].
     * [HttpSource]'s inheritors will have a default [chapterListRequest] that asks for:
     * ```
     * GET(baseUrl + manga.url, headers)
     * ```
     * You can override [chapterListRequest] if this is not the case for you.
     *
     * Note that you should use [SChapter.create] instead of implementing your own version of [SChapter].
     *
     * The chapters list appears in the app from top to bottom (with the default source sort),
     * be careful of the direction in which you sort it!
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document: Document = response.asJsoup()
        val extractor = DataExtractor(document)
        val bookChapters: Map<ScanGroup, List<DataExtractor.BookChapter>> = extractor.bookChapters

        val blLangs: Set<String> = preferences.blacklistedLanguages()
        val wlLangs: Set<String> = preferences.whitelistedLanguages()

        return bookChapters.asSequence()
            .flatMap { (_, chapters) -> chapters }
            .filter { it.chapterLanguage !in blLangs }
            .filter { wlLangs.isEmpty() || it.chapterLanguage == UNKNOWN_LANGUAGE || it.chapterLanguage in wlLangs }
            .toList()
            .sortedWith(
                compareByDescending<DataExtractor.BookChapter> { chapter -> chapter.chapterNumber }
                    .thenBy { chapter -> chapter.chapterGroup }
                    .thenBy { chapter -> chapter.chapterLanguage },
            )
            .map { bookChapter ->
                SChapter.create().apply {
                    url = bookChapter.chapterUrl.rawRelative ?: reportErrorToUser { "Could not relativize ${bookChapter.chapterUrl}" }
                    name = bookChapter.chapterTitle
                    date_upload = bookChapter.chapterDateAdded?.time ?: 0L
                    scanlator = """${bookChapter.chapterGroup} | ${bookChapter.chapterLanguage.replaceFirstChar(Char::uppercaseChar)}"""
                    chapter_number = bookChapter.chapterNumber!!.let { (main, sub) ->
                        // no fractional part, log(0) is -Inf (technically undefined)
                        if (sub == 0u) return@let main.toFloat()

                        val subD = sub.toDouble()
                        // 1 + floor(log10(subD)) finds the number of digits (in base 10) "subD" has
                        // see https://www.wolframalpha.com/input?i=LogLinearPlot+y%3D1+%2B+floor%28log10%28x%29%29%2C+where+x%3D1+to+10%5E9
                        val digits: Double = 1.0 + floor(log10(subD))
                        val fractional: Double = subD / 10.0.pow(digits)
                        // this basically creates a float that has "main" as integral part and "sub" as fractional part
                        // see https://www.wolframalpha.com/input?i=LogLinearPlot+y%3Dx+%2F+10%5E%281+%2B+floor%28log10%28x%29%29%29%2C+where+x%3D0+to+10%5E9
                        // the lines look curved because it's a logarithmic plot in the x-axis, but they're straight in a linear plot
                        (main.toDouble() + fractional).toFloat()
                    }
                }
            }
    }

    /**
     * Usually using [pageListRequest] and [pageListParse] should be enough,
     * but in this case we override the method to directly ask the server for the chapter pages (images).
     *
     * When constructing a [Page] there are 4 properties you need to consider:
     *  - [index][Page.index] -> **ignored**: the list of pages should be returned already sorted!
     *  - [url][Page.url] -> by default used by [imageUrlRequest] to create a `GET(page.url, headers)` [Request].
     *  Of which the [Response] will be given to [imageUrlParse], responsible for retrieving the value of [Page.imageUrl].
     *  This property should be left blank if [Page.imageUrl] can already be extracted at this stage.
     *  - [imageUrl][Page.imageUrl] -> by default used by [imageRequest] to create a `GET(page.imageUrl, headers)` [Request].
     *  - [uri][Page.uri] -> **DEPRECATED**: do not use
     */
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val pathMatch: PathMatchResult = """${homepageUri.toASCIIString()}/${chapter.url}""".toHttpUrl().matchAgainst(chapterUrlPattern)
        if (!pathMatch.doesMatch) {
            reportErrorToUser { "chapter url ${chapter.url} does not match expected pattern" }
        }

        return client.newCall(ProjectSukiAPI.chapterPagesRequest(json, headers, pathMatch["bookid"]!!.value, pathMatch["chapterid"]!!.value))
            .asObservableSuccess()
            .map { ProjectSukiAPI.parseChapterPagesResponse(json, it) }
    }

    /**
     * Not used in this extension, as [Page.imageUrl] is set directly.
     */
    override fun imageUrlParse(response: Response): String = reportErrorToUser {
        // give a hint on who called this method
        "invalid ${Thread.currentThread().stackTrace.take(3)}"
    }

    /**
     * Not used in this extension, as we override [fetchPageList] to modify the default behaviour.
     */
    override fun pageListParse(response: Response): List<Page> = reportErrorToUser("ProjectSuki.pageListParse") {
        // give a hint on who called this method
        "invalid ${Thread.currentThread().stackTrace.asSequence().drop(1).take(3).toList()}"
    }

    companion object {
        private const val DESCRIPTION_DIVIDER: String = "/=/-/=/-/=/-/=/-/=/-/=/-/=/-/=/"
    }
}
