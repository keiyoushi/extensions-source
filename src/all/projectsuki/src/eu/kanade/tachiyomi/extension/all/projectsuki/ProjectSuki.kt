package eu.kanade.tachiyomi.extension.all.projectsuki

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
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
    KeiSource(),
    ConfigurableSource {

    private val sharedPreferences by getPreferencesLazy()
    private val preferences by lazy { ProjectSukiPreferences(sharedPreferences) }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
        with(preferences) { screen.configure() }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2, 1.seconds)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = setRandomUserAgent()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = homepageUrl.newBuilder()
            .addPathSegment("browse")
            .addPathSegment((page - 1).toString())
            .build()

        return searchMangaParse(client.get(url).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchMangaParse(client.get(homepageUrl).asJsoup(), overrideHasNextPage = false)

    private inline fun <reified T> HttpUrl.Builder.applyPSFilter(
        from: FilterList,
    ): HttpUrl.Builder where T : Filter<*>, T : ProjectSukiFilters.ProjectSukiFilter = apply {
        from.firstNotNullOfOrNull { it as? T }?.run { applyFilter() }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != homepageUrl.host) return MangasPage(emptyList(), hasNextPage = false)

        val bookUrlMatch = url.matchAgainst(bookUrlPattern)
        val readUrlMatch = url.matchAgainst(chapterUrlPattern)

        val bookid: BookID? = when {
            bookUrlMatch.doesMatch -> bookUrlMatch.group(1)
            readUrlMatch.doesMatch -> readUrlMatch.group(1)
            else -> null
        }

        if (bookid != null) {
            val rawSManga = SManga.create().apply {
                this.url = bookid.bookIDToURL().rawRelative ?: reportErrorToUser { "Could not create relative url for bookID: $bookid" }
            }
            val manga = fetchMangaUpdate(rawSManga, emptyList(), fetchDetails = true, fetchChapters = false).manga

            return MangasPage(listOf(manga), hasNextPage = false)
        }

        if (url.pathSegments.firstOrNull() == "search") {
            val urlQuery = url.encodedQuery
            if (urlQuery.isNullOrBlank()) throw Exception("Empty search query!")

            val searchUrl = homepageUrl.newBuilder()
                .addPathSegment("search")
                .encodedQuery(urlQuery)
                .build()

            return searchMangaParse(client.get(searchUrl).asJsoup(), overrideHasNextPage = false)
        }

        return MangasPage(emptyList(), hasNextPage = false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchMode: ProjectSukiFilters.SearchMode = filters.firstInstanceOrNull<ProjectSukiFilters.SearchModeFilter>()
            ?.state
            ?.let { ProjectSukiFilters.SearchMode.entries[it] } ?: ProjectSukiFilters.SearchMode.SMART

        return when (searchMode) {
            ProjectSukiFilters.SearchMode.SMART -> SmartBookSearchHandler(query, ProjectSukiAPI.fetchBookSearch(client, headers)).mangasPage

            ProjectSukiFilters.SearchMode.SIMPLE -> ProjectSukiAPI.fetchBookSearch(client, headers).simpleSearchMangasPage(query)

            ProjectSukiFilters.SearchMode.FULL_SITE -> {
                val url = homepageUrl.newBuilder()
                    .addPathSegment("search")
                    .addQueryParameter("page", (page - 1).toString())
                    .addQueryParameter("q", query)
                    .applyPSFilter<ProjectSukiFilters.Origin>(from = filters)
                    .applyPSFilter<ProjectSukiFilters.Status>(from = filters)
                    .applyPSFilter<ProjectSukiFilters.Author>(from = filters)
                    .applyPSFilter<ProjectSukiFilters.Artist>(from = filters)
                    .build()

                searchMangaParse(client.get(url).asJsoup())
            }
        }
    }

    private fun filterList(vararg sequences: Sequence<Filter<*>>): FilterList = FilterList(sequences.asSequence().flatten().toList())

    override fun getFilterList(data: JsonElement?): FilterList = filterList(
        ProjectSukiFilters.headersSequence(preferences),
        ProjectSukiFilters.filtersSequence(preferences),
        ProjectSukiFilters.footersSequence(preferences),
    )

    private fun searchMangaParse(document: Document, overrideHasNextPage: Boolean? = null): MangasPage {
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document: Document = client.get(getMangaUrl(manga)).asJsoup()
        val extractor = DataExtractor(document)

        return SMangaUpdate(
            manga = mangaDetailsParse(manga, extractor),
            chapters = chapterListParse(extractor),
        )
    }

    private fun mangaDetailsParse(manga: SManga, extractor: DataExtractor): SManga {
        val data: DataExtractor.PSBookDetails = extractor.bookDetails

        return manga.apply {
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

    private fun chapterListParse(extractor: DataExtractor): List<SChapter> {
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pathMatch: PathMatchResult = (baseUrl + chapter.url).toHttpUrl().matchAgainst(chapterUrlPattern)
        if (!pathMatch.doesMatch) {
            reportErrorToUser { "chapter url ${chapter.url} does not match expected pattern" }
        }

        return ProjectSukiAPI.fetchChapterPages(client, headers, pathMatch.group(1)!!, pathMatch.group(2)!!)
    }

    companion object {
        private const val DESCRIPTION_DIVIDER: String = "/=/-/=/-/=/-/=/-/=/-/=/-/=/-/=/"
    }
}
