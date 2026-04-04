package eu.kanade.tachiyomi.extension.en.valirscans

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ValirScans :
    HttpSource(),
    ConfigurableSource {

    override val versionId = 2

    override val name = "Valir Scans"

    override val baseUrl = "https://valirscans.org"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val baseHttpUrl by lazy { "$baseUrl/".toHttpUrl() }

    override fun headersBuilder() = Headers.Builder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series?sort=views&order=desc&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = response.parseBrowsePage()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series?sort=updated&order=desc&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = response.parseBrowsePage()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = response.parseBrowsePage()

    override fun getFilterList() = FilterList()

    override fun getMangaUrl(manga: SManga): String = baseUrl + normalizeSeriesPath(manga.url)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + normalizeSeriesPath(manga.url), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val html = response.use { it.body.string() }
        val document = Jsoup.parse(html, response.request.url.toString())
        val schema = document.select("script[type=application/ld+json]")
            .asSequence()
            .map { runCatching { it.data().parseAs<BookSchema>() }.getOrNull() }
            .firstOrNull { it?.type == "Book" }

        val detailData = runCatching {
            extractEscapedJsonValue(html, ESCAPED_SERIES_MARKER, '{')
                .unescapeJson()
                .parseAs<SeriesDetailsDto>()
        }.getOrNull()

        return SManga.create().apply {
            title = schema?.name ?: document.selectFirst("h1")!!.text()
            description = detailData?.description ?: schema?.description
            author = schema?.author?.name ?: detailData?.author
            artist = detailData?.artist
            status = parseStatus(detailData?.status)
            thumbnail_url = detailData?.coverImage?.toAbsoluteUrl(response.request.url.toString())
                ?: schema?.image?.toAbsoluteUrl(response.request.url.toString())
            genre = buildList {
                detailData?.type
                    ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
                    ?.also(::add)
                addAll(detailData?.genres.orEmpty().map { it.name })
                if (isEmpty()) {
                    addAll(schema?.genre.orEmpty())
                }
            }.distinct().joinToString()
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.use { it.body.string() }
        val seriesPath = normalizeSeriesPath(response.request.url.encodedPath)
        val chapters = extractEscapedJsonValue(html, ESCAPED_CHAPTERS_MARKER, '[')
            .unescapeJson()
            .parseAs<List<ChapterDto>>()

        return chapters
            .asSequence()
            .filter { preferences.showPaidChapters || !it.isLocked }
            .map { chapter ->
                SChapter.create().apply {
                    url = "$seriesPath/chapter/${formatChapterNumber(chapter.number)}"
                    name = buildString {
                        if (chapter.isLocked) append("🔒 ")
                        append(chapter.title.ifBlank { "Chapter ${formatChapterNumber(chapter.number)}" })
                    }
                    chapter_number = chapter.number
                    date_upload = chapter.publishedAt.parseDate()
                }
            }
            .toList()
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + normalizeChapterPath(chapter.url)

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + normalizeChapterPath(chapter.url), headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.use { it.body.string() }
        val chapter = extractEscapedJsonValue(html, ESCAPED_CHAPTER_MARKER, '{')
            .unescapeJson()
            .parseAs<ReaderChapterDto>()

        return chapter.pages.map { page ->
            Page(page.pageNumber - 1, imageUrl = page.imageUrl.toAbsoluteUrl())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    private fun Response.parseBrowsePage(): MangasPage {
        val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val html = use { it.body.string() }
        val document = Jsoup.parse(html, request.url.toString())
        val mangas = document.select("div[role=gridcell]").mapNotNull { it.toSManga() }

        val totalResults = TOTAL_RESULTS_REGEX.find(html)?.groupValues?.get(2)?.toIntOrNull()
        val hasNextPage = totalResults?.let { page * BROWSE_PAGE_SIZE < it } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSManga(): SManga? {
        val detailLink = selectFirst("a[href*='?ref=browse']")
            ?: select("a[href*='/series/comic/']")
                .firstOrNull { !it.attr("href").contains("/chapter/") }
            ?: return null

        val title = selectFirst("h3")?.text()
            ?.ifBlank { null }
            ?: selectFirst("img[alt]")?.attr("alt")
                ?.ifBlank { null }
            ?: return null

        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(detailLink.absUrl("href"))
            thumbnail_url = selectFirst("img[src], img[srcset]")?.extractThumbnailUrl()
        }
    }

    private fun Element.extractThumbnailUrl(): String {
        val candidate = attr("abs:src")
            .ifBlank { attr("abs:srcset").substringBefore(" ") }

        if (!candidate.contains("/_next/image?url=")) {
            return candidate
        }

        val encodedUrl = candidate.substringAfter("url=", "").substringBefore("&")
        val decodedUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8)
        return decodedUrl.toAbsoluteUrl(ownerDocument()?.location() ?: baseUrl)
    }

    private fun String.toAbsoluteUrl(base: String = baseUrl): String = resolveUrl(this, base.toHttpUrlOrNull() ?: baseHttpUrl)?.toString() ?: this

    private fun normalizeSeriesPath(path: String): String {
        val resolvedUrl = resolveUrl(path)
        val segments = resolvedUrl?.pathSegments.orEmpty().filter(String::isNotBlank)

        return when {
            segments.size >= 3 && segments[0] == "series" && segments[1] == "comic" ->
                "/series/comic/${segments[2]}"
            segments.size >= 2 && segments[0] == "comic" ->
                "/series/comic/${segments[1]}"
            segments.size >= 2 && segments[0] == "series" ->
                "/series/comic/${segments[1]}"
            segments.isNotEmpty() ->
                "/series/comic/${segments.last()}"
            else ->
                sanitizeRelativePath(path)
        }.trimEnd('/')
    }

    private fun normalizeChapterPath(path: String): String {
        val resolvedUrl = resolveUrl(path)
        val segments = resolvedUrl?.pathSegments.orEmpty().filter(String::isNotBlank)

        return when {
            segments.size >= 5 && segments[0] == "series" && segments[1] == "comic" && segments[3] == "chapter" ->
                "/series/comic/${segments[2]}/chapter/${segments[4]}"
            segments.size >= 4 && segments[0] == "comic" && segments[2] == "chapter" ->
                "/series/comic/${segments[1]}/chapter/${segments[3]}"
            segments.size >= 4 && segments[0] == "series" && segments[2] == "chapter" ->
                "/series/comic/${segments[1]}/chapter/${segments[3]}"
            else ->
                sanitizeRelativePath(path).substringBefore("?")
        }
    }

    private fun resolveUrl(path: String, base: HttpUrl = baseHttpUrl): HttpUrl? {
        path.toHttpUrlOrNull()?.let { return it }

        if (path.startsWith("//")) {
            return "${base.scheme}:$path".toHttpUrlOrNull()
        }

        return base.resolve(path)
    }

    private fun sanitizeRelativePath(path: String): String {
        val cleanPath = path.substringBefore('#')
        return "/" + cleanPath.removePrefix("/")
    }

    private fun extractEscapedJsonValue(html: String, marker: String, openChar: Char): String {
        val startIndex = html.indexOf(marker)
        check(startIndex >= 0) { "Could not find marker: $marker" }

        val valueStart = html.indexOf(openChar, startIndex + marker.length)
        check(valueStart >= 0) { "Could not find JSON start for marker: $marker" }

        val closeChar = if (openChar == '{') '}' else ']'
        var depth = 0

        for (index in valueStart until html.length) {
            when (html[index]) {
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) {
                        return html.substring(valueStart, index + 1)
                    }
                }
            }
        }

        error("Could not find JSON end for marker: $marker")
    }

    private fun String.unescapeJson(): String = "\"$this\"".parseAs<String>()

    private fun parseStatus(status: String?): Int = when (status?.uppercase(Locale.ENGLISH)) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        "CANCELLED", "CANCELED", "DROPPED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun formatChapterNumber(number: Float): String = chapterNumberFormatter.format(number)

    private fun String?.parseDate(): Long = dateFormat.tryParse(this)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = "Show paid chapters"
            summaryOn = "Paid chapters will be shown in the chapter list."
            summaryOff = "Paid chapters will be hidden from the chapter list."
            setDefaultValue(SHOW_PAID_CHAPTERS_DEFAULT)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.showPaidChapters: Boolean
        get() = getBoolean(SHOW_PAID_CHAPTERS_PREF, SHOW_PAID_CHAPTERS_DEFAULT)

    companion object {
        private const val BROWSE_PAGE_SIZE = 24

        private const val SHOW_PAID_CHAPTERS_PREF = "pref_show_paid_chap"
        private const val SHOW_PAID_CHAPTERS_DEFAULT = false

        private const val ESCAPED_SERIES_MARKER = "\\\"series\\\":"
        private const val ESCAPED_CHAPTERS_MARKER = "\\\"chapters\\\":"
        private const val ESCAPED_CHAPTER_MARKER = "\\\"chapter\\\":"

        private val TOTAL_RESULTS_REGEX =
            """Showing <!-- -->(\d+)<!-- --> of <!-- -->(\d+)<!-- --> results""".toRegex()

        private val chapterNumberFormatter = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
