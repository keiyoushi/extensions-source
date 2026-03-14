package eu.kanade.tachiyomi.extension.en.mangamob

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Mangamob : ParsedHttpSource() {

    override val name = "MangaMob"
    override val baseUrl = "https://mangamob.com"
    override val lang = "en"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun getFilterList(): FilterList = getMangamobFilters()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var sort = RANDOM_FILTER
        val genres = mutableListOf<String>()

        if (query.isBlank()) {
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> sort = filter.selected
                    is GenreFilter -> genres += filter.state.filter { it.state }.map { it.name }
                    else -> {}
                }
            }
        }

        return browseRequest(
            page = page,
            query = query,
            sort = sort,
            genres = genres,
        )
    }

    private fun browseRequest(page: Int, query: String, sort: String, genres: List<String>): Request {
        val url = "$baseUrl/browse-comics/".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
            } else {
                addQueryParameter("filter", sort)
                if (genres.isNotEmpty()) {
                    addQueryParameter("genre_included", genres.joinToString(","))
                }
            }
            if (page > 1) {
                addQueryParameter("results", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaRequest(page: Int): Request = browseRequest(
        page = page,
        query = "",
        sort = POPULAR_FILTER,
        genres = emptyList(),
    )

    override fun latestUpdatesRequest(page: Int): Request = browseRequest(
        page = page,
        query = "",
        sort = LATEST_FILTER,
        genres = emptyList(),
    )

    override fun popularMangaSelector() = BROWSE_MANGA_SELECTOR
    override fun latestUpdatesSelector() = BROWSE_MANGA_SELECTOR
    override fun searchMangaSelector() = BROWSE_MANGA_SELECTOR

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.manga-poster") ?: return@apply
        val titleElement = element.selectFirst("h3.manga-name a")
        val imageElement = link.selectFirst("img")

        setUrlWithoutDomain(link.attr("href"))
        title = titleElement?.text()?.trim().orEmpty().ifBlank { link.attr("title").trim() }
        thumbnail_url = imageElement?.absUrl("src").takeUnless { it.isNullOrBlank() }
            ?: imageElement?.attr("src")
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseBrowseMangaPage(
        response = response,
        selector = latestUpdatesSelector(),
        skipKnownBrokenUpdatedEntries = true,
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val isUpdatedFilter = response.request.url.queryParameter("filter")
            ?.equals(LATEST_FILTER, ignoreCase = true) == true
        return parseBrowseMangaPage(
            response = response,
            selector = searchMangaSelector(),
            skipKnownBrokenUpdatedEntries = isUpdatedFilter,
        )
    }

    private fun parseBrowseMangaPage(
        response: Response,
        selector: String,
        skipKnownBrokenUpdatedEntries: Boolean,
    ): MangasPage {
        val document = response.asJsoup()
        val browseItems = document.select(selector)
        val rawMangas = browseItems.mapNotNull { element ->
            val manga = searchMangaFromElement(element)
            manga.takeUnless { manga.title.isBlank() || manga.url.isBlank() }
        }
        val filteredMangas = if (skipKnownBrokenUpdatedEntries) {
            // Temporary site-side workaround:
            // "Updated" can include entries that currently resolve to HTTP 404.
            // Keep this denylist explicit and fast to avoid extra per-item network checks.
            rawMangas.filterNot { it.url.normalizedMangaPath() in KNOWN_BROKEN_UPDATED_MANGA_URLS }
        } else {
            rawMangas
        }

        val hasNextPage = document.select(NEXT_PAGE_SELECTOR).isNotEmpty()

        return MangasPage(filteredMangas, hasNextPage)
    }

    private fun String.normalizedMangaPath(): String = trim().trimEnd('/')

    override fun popularMangaNextPageSelector() = NEXT_PAGE_SELECTOR
    override fun latestUpdatesNextPageSelector() = NEXT_PAGE_SELECTOR
    override fun searchMangaNextPageSelector() = NEXT_PAGE_SELECTOR

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h2.manga-name")?.text()?.trim().orEmpty()

        val summary = document.selectFirst(".sort-desc .description")?.ownText()?.trim().orEmpty()
        val altName = document.selectFirst(".manga-name-or")?.text()?.trim().orEmpty()
        description = buildString {
            if (summary.isNotBlank()) {
                append(summary)
            }
            if (altName.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(ALT_NAME, "\n", altName)
            }
        }

        genre = document.select(".sort-desc .genres a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        author = parseAuthors(document)
        status = parseStatus(document.infoValue("Status"))

        val thumbnail = document.selectFirst(".anisc-poster .manga-poster img")
        thumbnail_url = thumbnail?.absUrl("src").takeIf { !it.isNullOrBlank() } ?: thumbnail?.attr("src")
    }

    private fun parseAuthors(document: Document): String? {
        val authors = document.select(".anisc-info .item")
            .firstOrNull { it.selectFirst(".item-head")?.text()?.trim()?.startsWith("Authors", true) == true }
            ?.select("a")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() && it.lowercase() != "updating" }
            .orEmpty()

        return authors.joinToString(", ").ifBlank { null }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun Document.infoValue(label: String): String? {
        val normalizedLabel = label.lowercase()
        val items = select(".anisc-info .item")
        for (item in items) {
            val head = item.selectFirst(".item-head")?.text()?.trim()?.removeSuffix(":")?.lowercase()
            if (head == normalizedLabel) {
                val raw = item.selectFirst(".name")?.text()?.trim()
                    ?: item.select("a").joinToString(", ") { it.text().trim() }
                return raw.ifBlank { null }
            }
        }
        return null
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = MANGA_ID_REGEX.find(document.html())?.groupValues?.get(1)
            ?: throw Exception("Could not find manga ID")

        val chaptersResponse = client.newCall(GET("$baseUrl/get/chapters/?manga_id=$mangaId", headers))
            .execute()
            .use { apiResponse ->
                if (!apiResponse.isSuccessful) {
                    throw Exception("Failed to fetch chapters (HTTP ${apiResponse.code})")
                }
                apiResponse.parseAs<ChaptersResponseDto>()
            }

        return chaptersResponse.chapters.mapNotNull { chapter ->
            val chapterSlug = chapter.chapter_slug.trim()
            if (chapterSlug.isEmpty()) return@mapNotNull null

            val chapterNumber = chapter.chapter_number.trim().removeSuffix("-eng-li")
            SChapter.create().apply {
                setUrlWithoutDomain("/chapter/en/$chapterSlug/")
                name = chapterNumber.ifBlank { chapterSlug }.let { value ->
                    if (value.startsWith("Chapter ", true)) value else "Chapter $value"
                }
            }
        }
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        val allImageUrls = document.select("#chapter-images img.lazy[data-src]")
            .mapNotNull { image ->
                image.absUrl("data-src").takeIf { it.isNotBlank() } ?: image.attr("data-src").takeIf { it.isNotBlank() }
            }

        val mangaImageUrls = allImageUrls.filter { url -> "/cdn_mangaraw/" in url }
        val selectedImageUrls = if (mangaImageUrls.isNotEmpty()) mangaImageUrls else allImageUrls

        return selectedImageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    companion object {
        private const val ALT_NAME = "Alternative Name:"
        private const val BROWSE_MANGA_SELECTOR = "#main-wrapper.page-az.page-filter .manga_list-sbs .item.item-spc"
        private const val NEXT_PAGE_SELECTOR = ".pre-pagination .pagination a[title=Next]"
        private const val RANDOM_FILTER = "Random"
        private const val LATEST_FILTER = "Updated"
        private const val POPULAR_FILTER = "Views"
        private val KNOWN_BROKEN_UPDATED_MANGA_URLS = setOf(
            "/manga/item/xo4e-even-though-im-a-level-0-useless-explorer-im-actually-the-strongest-in-the-world",
            "/manga/item/mentally-fight",
            "/manga/item/j8gy-saikyou-demodori-chuunen-boukensha-wa-imasara-inochi-nante-kaketakunai",
        )
        private val MANGA_ID_REGEX = Regex("""/get/chapters/\?manga_id=(\d+)""")
    }
}

@Serializable
private data class ChaptersResponseDto(
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
private data class ChapterDto(
    val chapter_number: String = "",
    val chapter_slug: String = "",
)
