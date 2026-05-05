package eu.kanade.tachiyomi.extension.en.mgreadio

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MgreadIo :
    InitManga(
        "Mgread.io",
        "https://mgread.io",
        "en",
        mangaUrlDirectory = "manga",
        popularUrlSlug = "manga-ranking",
        latestUrlSlug = "recently-updated",
    ) {

    override val client = network.cloudflareClient

    // Popular

    override fun popularMangaSelector() = MANGA_GRID_SELECTOR

    override fun popularMangaParse(response: Response): MangasPage = super.popularMangaParse(response).withoutAnimeEntries()

    override fun popularMangaFromElement(element: Element): SManga = mangaFromGridElement(element)

    override fun popularMangaNextPageSelector() = NEXT_PAGE_SELECTOR

    // Latest

    override fun latestUpdatesParse(response: Response): MangasPage = super.latestUpdatesParse(response).withoutAnimeEntries()

    override fun latestUpdatesSelector() = MANGA_GRID_SELECTOR

    override fun latestUpdatesFromElement(element: Element): SManga = mangaFromGridElement(element)

    override fun latestUpdatesNextPageSelector() = NEXT_PAGE_SELECTOR

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
        val bodyText = response.body.string()
        val trimmedBody = bodyText.trimStart()

        val mangasPage = if (trimmedBody.startsWith("<") || trimmedBody.startsWith("<!")) {
            val document = Jsoup.parse(bodyText, baseUrl)
            val mangas = document.select(searchMangaSelector())
                .map(::searchMangaFromElement)
                .distinctBy { it.url }

            MangasPage(mangas, document.selectFirst(searchMangaNextPageSelector()) != null)
        } else {
            val mangas = bodyText.parseAs<List<MgreadSearchDto>>()
                .mapNotNull(::mangaFromSearchDto)
                .distinctBy { it.url }

            MangasPage(mangas, false)
        }

        return mangasPage.withoutAnimeEntries()
    }

    override fun searchMangaSelector() = "$MANGA_GRID_SELECTOR, .manga-item-details"

    override fun searchMangaFromElement(element: Element): SManga = mangaFromGridElement(element)

    override fun searchMangaNextPageSelector() = NEXT_PAGE_SELECTOR

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("#manga-title")?.ownText()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" [Ch.")?.trim()
            ?: error("Title not found")

        thumbnail_url = document.selectFirst(".story-cover img, meta[property=og:image]")?.imageUrl()

        val descriptionText = document.selectFirst("#manga-description")?.wholeText()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        genre = document.select("#genre-tags a[href*='/genre/']")
            .joinToString { it.ownText().ifBlank { it.text() } }

        status = document.selectFirst("#manga-status")?.text().parseStatus()

        val metadata = buildList {
            document.selectFirst("#manga-title + div")?.ownText()
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

            document.selectFirst("#manga-title + div .init-plugin-suite-view-count-number")?.text()
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

        setUrlWithoutDomain(document.location())
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.select("#manga-title[data-id], #chapter-search-input[data-manga-id]")
            .firstOrNull()
            ?.let { element -> element.attr("data-id").ifBlank { element.attr("data-manga-id") } }
            ?.toIntOrNull()

        val restChapters = mangaId
            ?.let { runCatching { fetchChapterList(it, response.request.url.encodedPath) }.getOrElse { emptyList() } }
            .orEmpty()

        return restChapters.ifEmpty {
            document.select(chapterListSelector()).map(::chapterFromElement)
        }
    }

    private fun fetchChapterList(mangaId: Int, mangaPath: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
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

            chapters += chapterList.items.map { it.toSChapter(mangaPath) }
            page++
        } while (page <= chapterList.totalPages)

        return chapters
    }

    override fun chapterListSelector() = ".chapter-list .chapter-item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst("a[href*='/chapter-']")!!
        val chapterUrl = urlElement.absUrl("href")

        setUrlWithoutDomain(chapterUrl)
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

    override fun pageListParse(document: Document): List<Page> = document.select("#chapter-content img[src]").mapIndexed { index, element ->
        Page(index, document.location(), element.absUrl("src"))
    }

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

    private fun mangaFromGridElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("h2 a[href*='/manga/']")
            ?: element.selectFirst("a[href*='/manga/']:not([href*='/chapter-'])")!!

        title = titleElement.text()
        setUrlWithoutDomain(titleElement.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.imageUrl()
    }

    private fun mangaFromSearchDto(dto: MgreadSearchDto): SManga? {
        val parsedTitle = Jsoup.parse(dto.title).text()
        val cleanUrl = dto.url.trim().takeIf(String::isNotEmpty) ?: return null

        return SManga.create().apply {
            title = parsedTitle
            thumbnail_url = dto.thumb
            setUrlWithoutDomain(
                cleanUrl.toHttpUrlOrNull()?.encodedPath ?: cleanUrl,
            )
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
