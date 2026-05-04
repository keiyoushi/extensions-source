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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MgreadIo :
    InitManga(
        "Mgread.io",
        "https://mgread.io",
        "en",
        mangaUrlDirectory = "manga",
        popularUrlSlug = "manga-ranking",
        latestUrlSlug = "recently-updated",
        versionId = 2,
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
        title = document.selectFirst("#manga-title")?.ownText()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" [Ch.")?.trim()
            ?: ""

        thumbnail_url = document.selectFirst(".story-cover img, meta[property=og:image]")?.imageUrl()

        val descriptionText = document.selectFirst("#manga-description")?.wholeText()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        genre = document.select("#genre-tags a[href*='/genre/']")
            .joinToString { it.ownText().ifBlank { it.text() }.trim() }

        status = document.selectFirst("#manga-status")?.text().parseStatus()

        val metadata = buildList {
            document.selectFirst("#manga-title + div")?.ownText()?.trim()
                ?.substringBefore("Chapters")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add("Chapters: $it") }

            document.selectFirst("#comic-othername")?.text()?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add("Alternative title: $it") }

            document.selectFirst(".init-review-info")?.text()?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add("Rating: $it") }

            document.selectFirst("#manga-title + div .init-plugin-suite-view-count-number")?.text()?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add("Views: $it") }

            document.selectFirst("#last-updated")?.text()?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { add("Last updated: $it") }
        }

        description = listOfNotNull(
            descriptionText,
            metadata.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        ).joinToString("\n\n")

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
                .addQueryParameter("per_page", CHAPTER_REST_PAGE_SIZE.toString())
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
        name = element.selectFirst("h3")?.text()?.trim()
            ?: chapterUrl.substringBeforeLast('/').substringAfterLast('/').replace('-', ' ').replaceFirstChar(Char::uppercase)

        chapter_number = CHAPTER_NUMBER_REGEX.find(chapterUrl)
            ?.groupValues
            ?.get(1)
            ?.toFloatOrNull()
            ?: -1f

        date_upload = element.selectFirst("time[datetime]")?.attr("datetime").parseChapterDate()
    }

    private fun ChapterDto.toSChapter(mangaPath: String): SChapter = SChapter.create().apply {
        val chapterName = number.toChapterNamePart()
        val cleanMangaPath = mangaPath.substringBefore("/chapter/").trimEnd('/')

        setUrlWithoutDomain("$cleanMangaPath/$slug/")
        name = title.takeIf(String::isNotBlank)
            ?.let { "Chapter $chapterName - $it" }
            ?: "Chapter $chapterName"
        chapter_number = number
        date_upload = createdAt.parseRestChapterDate()
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

        title = titleElement.text().trim()
        setUrlWithoutDomain(titleElement.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.imageUrl()
    }

    private fun mangaFromSearchDto(dto: MgreadSearchDto): SManga? {
        val title = Jsoup.parse(dto.title).text().trim()
        val url = dto.url.trim().takeIf(String::isNotBlank) ?: return null

        return SManga.create().apply {
            this.title = title
            thumbnail_url = dto.thumb
            setUrlWithoutDomain(
                url.toHttpUrlOrNull()
                    ?.encodedPath
                    ?: url,
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
}
