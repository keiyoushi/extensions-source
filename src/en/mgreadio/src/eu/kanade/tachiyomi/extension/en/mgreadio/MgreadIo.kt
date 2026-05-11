package eu.kanade.tachiyomi.extension.en.mgreadio

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MgreadIo : HttpSource() {

    override val name = "Mgread.io"

    override val baseUrl = "https://mgread.io"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET(pageUrl("manga-ranking", page), headers)

    override fun popularMangaParse(response: Response): MangasPage = mangasPageFromHtml(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET(pageUrl("recently-updated", page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = mangasPageFromHtml(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-json/initlise/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query.trim())
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("advanced-filter")
            .apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addPathSegment("")
                addFilters(filters.ifEmpty { getFilterList() })
            }
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val isFilterRequest = response.request.url.encodedPath.contains("advanced-filter")

        val mangasPage = if (isFilterRequest) {
            val document = response.asJsoup()
            val mangas = document.select("$MANGA_GRID_SELECTOR, .manga-item-details")
                .map(::mangaFromGridElement)
                .distinctBy { it.url }

            MangasPage(mangas, document.selectFirst(NEXT_PAGE_SELECTOR) != null)
        } else {
            val mangas = response.parseAs<List<MgreadSearchDto>>()
                .mapNotNull(::mangaFromSearchDto)
                .distinctBy { it.url }

            MangasPage(mangas, false)
        }

        return mangasPage.withoutAnimeEntries()
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("#manga-title")?.ownText()
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" [Ch.")?.trim()
                ?: error("Title not found")

            thumbnail_url = document.selectFirst(".story-cover img, meta[property=og:image]")?.imageUrl()

            val descriptionText = document.selectFirst("#manga-description")?.wholeText()?.trim()
                ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

            genre = document.select("#genre-tags a[href*='/genre/']")
                .joinToString { it.ownText().ifEmpty { it.text() } }

            status = document.selectFirst("#manga-status")?.text().parseStatus()

            val metaRow = document.selectFirst("#manga-title + div")
            val metadata = buildList {
                metaRow?.ownText()
                    ?.substringBefore("Chapters")
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { add("Chapters: $it") }

                document.selectFirst("#comic-othername")?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { add("Alternative title: $it") }

                document.selectFirst(".init-review-info")?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { add("Rating: $it") }

                metaRow?.selectFirst(".init-plugin-suite-view-count-number")?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { add("Views: $it") }

                document.selectFirst("#last-updated")?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { add("Last updated: $it") }
            }

            description = buildString {
                if (descriptionText != null) append(descriptionText)
                if (metadata.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    metadata.joinTo(this, separator = "\n")
                }
            }

            url = document.location().toHttpUrl().encodedPath
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaPath = response.request.url.encodedPath

        val mangaId = document.select("#manga-title[data-id], #chapter-search-input[data-manga-id]")
            .firstOrNull()
            ?.let { element -> element.attr("data-id").ifEmpty { element.attr("data-manga-id") } }
            ?.toIntOrNull()

        val restChapters = mangaId
            ?.let { runCatching { fetchChapterList(it, mangaPath) }.getOrElse { emptyList() } }
            .orEmpty()

        return restChapters.ifEmpty {
            document.select(".chapter-list .chapter-item").map(::chapterFromElement)
        }
    }

    private fun fetchChapterList(mangaId: Int, mangaPath: String): List<SChapter> = buildList {
        var page = 1
        do {
            val url = "$baseUrl/wp-json/initmanga/v1/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("manga_id", mangaId.toString())
                .addQueryParameter("paged", page.toString())
                .addQueryParameter("per_page", "50")
                .build()

            val chapterList = client.newCall(GET(url, headers)).execute().use { restResponse ->
                restResponse.parseAs<ChapterListDto>()
            }

            chapterList.items.forEach { add(it.toSChapter(mangaPath)) }
            page++
        } while (page <= chapterList.totalPages)
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst("a[href*='/chapter-']")
            ?: error("Chapter link not found")
        val chapterUrl = urlElement.absUrl("href")

        url = chapterUrl.toHttpUrl().encodedPath
        name = element.selectFirst("h3")?.text()
            ?: chapterUrl.substringBeforeLast('/').substringAfterLast('/').replace('-', ' ').replaceFirstChar(Char::uppercase)

        chapter_number = CHAPTER_NUMBER_REGEX.find(chapterUrl)
            ?.groupValues
            ?.get(1)
            ?.toFloatOrNull()
            ?: -1f

        date_upload = element.selectFirst("time[datetime]")?.attr("datetime").parseChapterDate()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterUrl = document.location()
        return document.select("#chapter-content img[src]").mapIndexed { index, element ->
            Page(index, url = chapterUrl, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filters are only applied when the search text is empty."),
        TypeFilter(),
        StatusFilter(),
        AgeRatingFilter(),
        RatingMinFilter(),
        RatingMaxFilter(),
        SortFilter(),
        Filter.Separator(),
        GenreFilter(),
    )

    // Helpers

    private fun pageUrl(slug: String, page: Int): String = if (page == 1) {
        "$baseUrl/$slug/"
    } else {
        "$baseUrl/$slug/page/$page/"
    }

    private fun mangasPageFromHtml(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(MANGA_GRID_SELECTOR).map(::mangaFromGridElement)
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage).withoutAnimeEntries()
    }

    private fun mangaFromGridElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("h2 a[href*='/manga/']")
            ?: element.selectFirst("a[href*='/manga/']:not([href*='/chapter-'])")
            ?: error("Manga link not found in grid item")

        title = titleElement.text()
        url = titleElement.absUrl("href").toHttpUrl().encodedPath
        thumbnail_url = element.selectFirst("img")?.imageUrl()
    }

    private fun mangaFromSearchDto(dto: MgreadSearchDto): SManga? {
        val parsedTitle = Jsoup.parse(dto.title).text()
        val cleanUrl = dto.url.trim().takeIf(String::isNotEmpty) ?: return null

        return SManga.create().apply {
            title = parsedTitle
            thumbnail_url = dto.thumb
            url = cleanUrl.toHttpUrl().encodedPath
        }
    }

    private fun SManga.isAnimeEntry(): Boolean {
        val normalizedTitle = title.lowercase()
        return normalizedTitle.startsWith("anime -") ||
            normalizedTitle.startsWith("anime –") ||
            url.substringAfter("/manga/", "").startsWith("anime-")
    }

    private fun MangasPage.withoutAnimeEntries(): MangasPage = MangasPage(
        mangas.filterNot { it.isAnimeEntry() },
        hasNextPage,
    )

    private fun Element.imageUrl(): String? = when (normalName()) {
        "meta" -> attr("content")
        else -> attr("abs:data-src").ifEmpty {
            attr("abs:data-lazy-src").ifEmpty { attr("abs:src") }
        }
    }.takeIf(String::isNotEmpty)

    private fun String?.parseStatus(): Int = when (this?.lowercase(Locale.US)?.trim()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "season end", "source hiatus", "caught up" -> SManga.ON_HIATUS
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // Site emits HTML5 datetime "+07:00"; the 'X' format requires API 24,
    // so normalize to "+0700" / "+0000" for the API 21-compatible 'Z' pattern.
    private fun String?.parseChapterDate(): Long {
        if (isNullOrBlank()) return 0L
        val normalized = this
            .replace(ISO_TZ_REGEX, "$1$2")
            .replace(ZULU_SUFFIX_REGEX, "+0000")
        return chapterDateFormat.tryParse(normalized)
    }

    companion object {
        private const val MANGA_GRID_SELECTOR = ".manga-item-grid"
        private const val NEXT_PAGE_SELECTOR = "li:not(.uk-disabled) > a[aria-label='Next page']"

        private val CHAPTER_NUMBER_REGEX = Regex("""/chapter-(\d+(?:\.\d+)?)/""")
        private val ISO_TZ_REGEX = Regex("""([+-]\d{2}):(\d{2})$""")
        private val ZULU_SUFFIX_REGEX = Regex("""Z$""")

        private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
    }
}
