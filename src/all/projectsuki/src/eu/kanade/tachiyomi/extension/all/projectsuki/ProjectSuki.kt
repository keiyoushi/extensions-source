package eu.kanade.tachiyomi.extension.all.projectsuki

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
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
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
internal inline val EXTENSION_INFO: Nothing get() = error("EXTENSION_INFO")

internal const val SHORT_FORM_ID: String = """ps"""

internal val homepageUrl: HttpUrl = "https://projectsuki.com".toHttpUrl()
internal val homepageUri: URI = homepageUrl.toUri()

/** PATTERN: `https://projectsuki.com/book/<bookid>`  */
internal val bookUrlPattern = PathPattern(
    """book""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
)

/** PATTERN: `https://projectsuki.com/browse/<pagenum>` */
@Suppress("unused")
internal val browsePattern = PathPattern(
    """browse""".toRegex(RegexOption.IGNORE_CASE),
    """(\d+)""".toRegex(RegexOption.IGNORE_CASE),
)

/**
 * PATTERN: `https://projectsuki.com/read/<bookid>/<chapterid>/<startpage>`
 */
internal val chapterUrlPattern = PathPattern(
    """read""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
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
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(\d+-)?thumb(?:\.(.+))?""".toRegex(RegexOption.IGNORE_CASE),
)

/** PATTERN: `https://projectsuki.com/images/gallery/<bookid>/<uuid>/<pagenum>` */
internal val pageUrlPattern = PathPattern(
    """images""".toRegex(RegexOption.IGNORE_CASE),
    """gallery""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
)

/** PATTERN: `https://projectsuki.com/genre/<genre>` */
internal val genreSearchUrlPattern = PathPattern(
    """genre""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
)

/** PATTERN: `https://projectsuki.com/group/<groupid>` */
@Suppress("unused")
internal val groupUrlPattern = PathPattern(
    """group""".toRegex(RegexOption.IGNORE_CASE),
    """(.+)""".toRegex(RegexOption.IGNORE_CASE),
)

@Suppress("unused")
internal val emptyImageUrl: HttpUrl = homepageUrl.newBuilder()
    .addPathSegment("images")
    .addPathSegment("gallery")
    .addPathSegment("empty.jpg")
    .build()

internal val HttpUrl.rawRelative: String?
    get() {
        val uri = toUri()
        val relative = homepageUri.relativize(uri)
        return when {
            uri === relative -> null
            else -> {
                val str = relative.toASCIIString()
                if (str.startsWith("/")) str else "/$str"
            }
        }
    }

internal val reportPrefix: String
    get() = """Error! Report on GitHub (tachiyomiorg/tachiyomi-extensions)"""

internal class ProjectSukiException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal inline fun reportErrorToUser(locationHint: String? = null, message: () -> String): Nothing = throw ProjectSukiException(
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

internal const val UNKNOWN_LANGUAGE: String = "unknown"

@Suppress("unused")
@Source
abstract class ProjectSuki :
    HttpSource(),
    ConfigurableSource {

    private val sharedPreferences by getPreferencesLazy()
    private val preferences by lazy { ProjectSukiPreferences(sharedPreferences) }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
        with(preferences) { screen.configure() }
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(2, 1.seconds)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override fun popularMangaRequest(page: Int) = GET(
        homepageUrl.newBuilder()
            .addPathSegment("browse")
            .addPathSegment((page - 1).toString())
            .build(),
        headers,
    )

    override val supportsLatest: Boolean get() = true

    override fun latestUpdatesRequest(page: Int) = GET(homepageUrl, headers)

    private inline fun <reified T> HttpUrl.Builder.applyPSFilter(
        from: FilterList,
    ): HttpUrl.Builder where T : Filter<*>, T : ProjectSukiFilters.ProjectSukiFilter = apply {
        from.firstNotNullOfOrNull { it as? T }?.run { applyFilter() }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
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
            hasNextPage = mangas.size >= 30,
        )
    }

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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val searchMode: ProjectSukiFilters.SearchMode = filters.filterIsInstance<ProjectSukiFilters.SearchModeFilter>()
            .singleOrNull()
            ?.state
            ?.let { ProjectSukiFilters.SearchMode.entries[it] } ?: ProjectSukiFilters.SearchMode.SMART

        fun BookID.toMangasPageObservable(): Observable<MangasPage> {
            val rawSManga = SManga.create().apply {
                url = bookIDToURL().rawRelative ?: reportErrorToUser { "Could not create relative url for bookID: $this" }
            }

            return client.newCall(mangaDetailsRequest(rawSManga))
                .asObservableSuccess()
                .map { response -> mangaDetailsParse(response) }
                .map { manga -> MangasPage(listOf(manga), hasNextPage = false) }
        }

        val queryAsURL: HttpUrl? by unexpectedErrorCatchingLazy { query.toHttpUrlOrNull() ?: """$homepageUri$query""".toHttpUrlOrNull() }
        val bookUrlMatch by unexpectedErrorCatchingLazy { queryAsURL?.matchAgainst(bookUrlPattern) }
        val readUrlMatch by unexpectedErrorCatchingLazy { queryAsURL?.matchAgainst(chapterUrlPattern) }
        val isSearchUrl = queryAsURL?.host == homepageUrl.host && queryAsURL?.pathSegments?.firstOrNull() == "search"

        return when {
            query.startsWith(INTENT_SEARCH_QUERY_PREFIX) -> {
                val urlQuery = query.removePrefix(INTENT_SEARCH_QUERY_PREFIX)
                if (urlQuery.isBlank()) throw Exception("Empty search query!")

                val rawUrl = """${homepageUri.toASCIIString()}/search?$urlQuery"""
                val url = rawUrl.toHttpUrlOrNull() ?: reportErrorToUser { "Invalid search url: $rawUrl" }

                client.newCall(GET(url, headers))
                    .asObservableSuccess()
                    .map { response -> searchMangaParse(response, overrideHasNextPage = false) }
            }

            query.startsWith(INTENT_BOOK_QUERY_PREFIX) -> {
                val bookid = query.removePrefix(INTENT_BOOK_QUERY_PREFIX)
                if (bookid.isBlank()) throw Exception("Empty bookid!")

                bookid.toMangasPageObservable()
            }

            query.startsWith(INTENT_READ_QUERY_PREFIX) -> {
                val bookid = query.removePrefix(INTENT_READ_QUERY_PREFIX)
                if (bookid.isBlank()) throw Exception("Empty bookid!")

                bookid.toMangasPageObservable()
            }

            bookUrlMatch?.doesMatch == true -> {
                val bookid = bookUrlMatch!!.group(1)!!
                if (bookid.isBlank()) throw Exception("Empty bookid!")

                bookid.toMangasPageObservable()
            }

            readUrlMatch?.doesMatch == true -> {
                val bookid = readUrlMatch!!.group(1)!!
                if (bookid.isBlank()) throw Exception("Empty bookid!")

                bookid.toMangasPageObservable()
            }

            isSearchUrl -> {
                val urlQuery = queryAsURL!!.query ?: throw Exception("Empty search query!")
                if (urlQuery.isBlank()) throw Exception("Empty search query!")

                val rawUrl = """${homepageUri.toASCIIString()}/search?$urlQuery"""
                val url = rawUrl.toHttpUrlOrNull() ?: reportErrorToUser { "Invalid search url: $rawUrl" }

                client.newCall(GET(url, headers))
                    .asObservableSuccess()
                    .map { response -> searchMangaParse(response, overrideHasNextPage = false) }
            }

            searchMode == ProjectSukiFilters.SearchMode.SMART -> {
                client.newCall(ProjectSukiAPI.bookSearchRequest(headers))
                    .asObservableSuccess()
                    .map { response -> ProjectSukiAPI.parseBookSearchResponse(response) }
                    .map { data -> SmartBookSearchHandler(query, data).mangasPage }
            }

            searchMode == ProjectSukiFilters.SearchMode.SIMPLE -> {
                client.newCall(ProjectSukiAPI.bookSearchRequest(headers))
                    .asObservableSuccess()
                    .map { response -> ProjectSukiAPI.parseBookSearchResponse(response) }
                    .map { data -> data.simpleSearchMangasPage(query) }
            }

            else -> client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response -> searchMangaParse(response) }
        }
    }

    private fun filterList(vararg sequences: Sequence<Filter<*>>): FilterList = FilterList(sequences.asSequence().flatten().toList())

    override fun getFilterList(): FilterList = filterList(
        ProjectSukiFilters.headersSequence(preferences),
        ProjectSukiFilters.filtersSequence(preferences),
        ProjectSukiFilters.footersSequence(preferences),
    )

    override fun searchMangaParse(response: Response): MangasPage = searchMangaParse(response, null)

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
            status = when (data.details[DataExtractor.BookDetail.Status]?.detailData?.lowercase(Locale.US)) {
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

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

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
                    date_upload = bookChapter.chapterDateAdded
                    scanlator = """${bookChapter.chapterGroup} | ${bookChapter.chapterLanguage.replaceFirstChar(Char::uppercaseChar)}"""
                    chapter_number = bookChapter.chapterNumber!!.let { (main, sub) ->
                        if (sub == 0u) return@let main.toFloat()

                        val subD = sub.toDouble()
                        val digits: Double = 1.0 + floor(log10(subD))
                        val fractional: Double = subD / 10.0.pow(digits)
                        (main.toDouble() + fractional).toFloat()
                    }
                }
            }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val pathMatch: PathMatchResult = (baseUrl + chapter.url).toHttpUrl().matchAgainst(chapterUrlPattern)
        if (!pathMatch.doesMatch) {
            reportErrorToUser { "chapter url ${chapter.url} does not match expected pattern" }
        }

        return client.newCall(ProjectSukiAPI.chapterPagesRequest(headers, pathMatch.group(1)!!, pathMatch.group(2)!!))
            .asObservableSuccess()
            .map { ProjectSukiAPI.parseChapterPagesResponse(it) }
    }

    override fun imageUrlParse(response: Response): String = reportErrorToUser {
        "invalid ${Thread.currentThread().stackTrace.take(3)}"
    }

    override fun pageListParse(response: Response): List<Page> = reportErrorToUser("ProjectSuki.pageListParse") {
        "invalid ${Thread.currentThread().stackTrace.asSequence().drop(1).take(3).toList()}"
    }

    companion object {
        private const val DESCRIPTION_DIVIDER: String = "/=/-/=/-/=/-/=/-/=/-/=/-/=/-/=/"
    }
}

internal const val INTENT_SEARCH_QUERY_PREFIX: String = "$$SHORT_FORM_ID-search:"
internal const val INTENT_BOOK_QUERY_PREFIX: String = "$$SHORT_FORM_ID-book:"
internal const val INTENT_READ_QUERY_PREFIX: String = "$$SHORT_FORM_ID-read:"
