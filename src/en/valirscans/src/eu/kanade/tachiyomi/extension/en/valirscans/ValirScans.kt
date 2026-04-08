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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ValirScans :
    HttpSource(),
    ConfigurableSource {

    override val versionId = 3

    override val name = "Valir Scans"

    override val baseUrl = "https://valirscans.org"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val baseHttpUrl = "$baseUrl/".toHttpUrl()

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun headersBuilder() = super.headersBuilder()
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

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val html = response.use { it.body.string() }
        val document = Jsoup.parse(html, response.request.url.toString())
        val pageData = document.extractSeriesPageData()
        val schema = document.select("script[type=application/ld+json]")
            .asSequence()
            .map { runCatching { it.data().parseAs<BookSchema>() }.getOrNull() }
            .firstOrNull { it?.type == "Book" }

        val detailData = pageData?.series

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
        val document = Jsoup.parse(html, response.request.url.toString())
        val seriesPath = response.request.url.encodedPath
        val firstPageData = document.extractSeriesPageData() ?: return emptyList()
        val chapters = buildList {
            addAll(firstPageData.chapters)

            for (page in (firstPageData.currentPage + 1)..firstPageData.totalPages) {
                val pageUrl = response.request.url.newBuilder()
                    .setQueryParameter("page", page.toString())
                    .build()
                val pageData = client.newCall(GET(pageUrl, headers)).execute().use { pageResponse ->
                    pageResponse.asSeriesPageData()
                } ?: continue
                addAll(pageData.chapters)
            }
        }

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
                    date_upload = dateFormat.tryParse(chapter.publishedAt)
                }
            }
            .toList()
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.extractNextJs<ChapterPageDto> { element ->
            element is JsonObject && "chapter" in element
        }?.chapter ?: error("Could not find chapter data")

        return chapter.pages
            .sortedBy { it.pageNumber }
            .mapIndexed { index, page ->
                Page(index, imageUrl = page.imageUrl.toAbsoluteUrl())
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    private fun Response.asSeriesPageData(): SeriesPageDto? {
        val html = body.string()
        val document = Jsoup.parse(html, request.url.toString())
        return document.extractSeriesPageData()
    }

    private fun org.jsoup.nodes.Document.extractSeriesPageData(): SeriesPageDto? = extractNextJs<SeriesPageDto> { element ->
        element is JsonObject && "series" in element && "chapters" in element
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
            setUrlWithoutDomain(detailLink.absUrl("href").substringBefore("?"))
            thumbnail_url = selectFirst("img[src], img[srcset]")?.extractThumbnailUrl()
        }
    }

    private fun Element.extractThumbnailUrl(): String {
        val candidate = attr("abs:src")
            .ifBlank { attr("abs:srcset").substringBefore(" ") }

        if (!candidate.contains("/_next/image?url=")) {
            return candidate
        }

        val encodedUrl = candidate.toHttpUrlOrNull()?.queryParameter("url") ?: return candidate
        val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
        return decodedUrl.toAbsoluteUrl(ownerDocument()?.location() ?: baseUrl)
    }

    private fun String.toAbsoluteUrl(base: String = baseUrl): String = resolveUrl(this, base.toHttpUrlOrNull() ?: baseHttpUrl)?.toString() ?: this

    private fun resolveUrl(path: String, base: HttpUrl = baseHttpUrl): HttpUrl? {
        path.toHttpUrlOrNull()?.let { return it }

        if (path.startsWith("//")) {
            return "${base.scheme}:$path".toHttpUrlOrNull()
        }

        return base.resolve(path)
    }

    private fun parseStatus(status: String?): Int = when (status?.uppercase(Locale.ENGLISH)) {
        "ONGOING" -> SManga.ONGOING
        "COMPLETED" -> SManga.COMPLETED
        "HIATUS" -> SManga.ON_HIATUS
        "CANCELLED", "CANCELED", "DROPPED" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun formatChapterNumber(number: Float): String = chapterNumberFormatter.format(number)

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

        private val TOTAL_RESULTS_REGEX =
            """Showing <!-- -->(\d+)<!-- --> of <!-- -->(\d+)<!-- --> results""".toRegex()

        private val chapterNumberFormatter = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
