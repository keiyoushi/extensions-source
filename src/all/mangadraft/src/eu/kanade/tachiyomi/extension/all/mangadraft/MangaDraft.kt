package eu.kanade.tachiyomi.extension.all.mangadraft
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangaDraftCatalogResponseDto
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangaDraftPageDTO
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangaDraftProjectDto
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.PagesByCategory
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.joinToString
import kotlin.getValue

class MangaDraft : HttpSource() {
    override val name = "MangaDraft"
    override val baseUrl = "https://www.mangadraft.com"
    override val lang = "all"

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("api")
            addPathSegment("catalog")
            addPathSegment("projects")
            addQueryParameter("order", "popular")
            addQueryParameter("type", "all")
            addQueryParameter("page", page.toString())
            addQueryParameter("number", "20")
        }.build(),
        headers,
    )

    private fun parseMangaList(response: Response, isSearch: Boolean): MangasPage {
        val result = response.parseAs<MangaDraftCatalogResponseDto>()
        val mangas = result.data

        val mangaList = mangas.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.url)
                title = it.name
                thumbnail_url = it.avatar
                description = it.description
                genre = it.genres
            }
        }

        return MangasPage(
            mangaList,
            // No next page if text-search or less/equal to 16 results
            hasNextPage = if (isSearch) false else mangas.size >= 16,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response, isSearch = false)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("api")
            addPathSegment("catalog")
            addPathSegment("projects")
            addQueryParameter("number", "16")
            addQueryParameter("page", page.toString())
            addQueryParameter("order", "news")
            addQueryParameter("type", "all")
        }.build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        // Text-only Search
        GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("api")
                addPathSegment("search")
                addPathSegment("autocomplete")
                addQueryParameter("value", query)
            }.fragment("search").build().toString(),
            headers,
        )
    } else {
        // Filter-only Search
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val typeFilter = filterList.firstInstance<TypeFilter>()
        val orderFilter = filterList.firstInstance<OrderFilter>()
        val sectionFilter = filterList.firstInstance<SectionFilter>()
        val genreFilter = filterList.firstInstance<GenreFilter>()
        val formatFilter = filterList.firstInstance<FormatFilter>()
        val languageFilter = filterList.firstInstance<LanguageFilter>()
        val statusFilter = filterList.firstInstance<StatusFilter>()
        val sortFilter = filterList.firstInstance<SortFilter>()

        GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("api")
                addPathSegment("catalog")
                addPathSegment("projects")
                addQueryParameter("number", "16")
                addQueryParameter("page", page.toString())
                addQueryParameter("order", orderFilter.toUriPart())
                addQueryParameter("order_all", sortFilter.toUriPart())
                addQueryParameter("section", sectionFilter.toUriPart())
                addQueryParameter("status", statusFilter.toUriPart())
                addQueryParameter("genre", genreFilter.toUriPart())
                addQueryParameter("format", formatFilter.toUriPart())
                addQueryParameter("type", typeFilter.toUriPart())
                addQueryParameter("language", languageFilter.toUriPart())
            }.build(),
            headers,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val isTextSearch = response.request.url.queryParameter("value") != null

        return parseMangaList(response, isSearch = isTextSearch)
    }

    // Filters
    override fun getFilterList() = FilterList(
        Filter.Header("Ignored when using text search"),
        Filter.Separator(),
        SortFilter(),
        TypeFilter(),
        OrderFilter(),
        SectionFilter(),
        GenreFilter(),
        FormatFilter(),
        LanguageFilter(),
        StatusFilter(),
    )

    protected val regexWindowProject = Regex("""window\.project\s*=\s*(\{.*?\})\s*;""", RegexOption.DOT_MATCHES_ALL)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()

        // Find the <script> containing window.project
        val scriptContent = doc.selectFirst("script:containsData(window.project)")?.data()
            ?: throw Exception("Unable to find project script")

        // get the project part in the script
        val projectJson = regexWindowProject
            .find(scriptContent)
            ?.groups?.get(1)?.value
            ?: throw IllegalStateException("window.project not found")

        val project = projectJson.parseAs<MangaDraftProjectDto>()

        return SManga.create().apply {
            title = project.name
            description = project.description
            author = doc.select("[title=Auteur]").text()
            artist = doc.select("[title=créateur]").text()
            genre =
                project.genres.joinToString(", ") { it.name }
                    .orEmpty()
            status = parseStatus(project.projectStatusId)
        }
    }

    fun parseStatus(status: Int?) = when (status) {
        0 -> SManga.ONGOING
        1 -> SManga.COMPLETED
        2 -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    fun chapterListSelector() = "div.mt-7 div a:not(:has(img))"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterElements = document.select(chapterListSelector())

        val isNotOneShot = chapterElements[0].attr("href").contains("c.")

        return if (isNotOneShot) {
            chapterElements.mapIndexed { i, it ->
                chapterFromElement(it, i, true)
            }.reversed()
        } else {
            listOf(chapterFromElement(chapterElements[0], 0, false))
        }
    }

    private fun chapterFromElement(element: Element, index: Int, isNotOneShot: Boolean): SChapter = SChapter.create().apply {
        chapter_number = index.toFloat() + 1

        val titleText = element.selectFirst(".group-hover\\:text-secondary")?.ownText()?.trim() ?: ""

        name = if (isNotOneShot) {
            "Ch. ${index + 1}: $titleText"
        } else {
            titleText.ifEmpty { "Oneshot" }
        }

        url = element.absUrl("href")

        val dateText = element.selectFirst("div.flex.items-center span.md\\:inline")?.text()?.trim()

        if (!dateText.isNullOrBlank()) {
            date_upload = parseDate(dateText)
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (chapter.url.contains("c.")) {
            val request = GET(chapter.url, headers)

            // switch base url with actual url once we try to get the pages
            client.newCall(request).execute().use { response ->
                // final URL after redirects
                // get this api request with the id of the first page of the chapter after redirect
                val responseChapterNum =
                    response.request.url.toString().substringAfterLast('/').filter { it.isDigit() }
                chapter.setUrlWithoutDomain("$baseUrl/api/reader/listPages?first_page=$responseChapterNum&grouped_by_category=true")
            }
        } else {
            val chapterNum = chapter.url.substringAfterLast('/').filter { it.isDigit() }
            chapter.setUrlWithoutDomain("$baseUrl/api/reader/listPages?first_page=$chapterNum&grouped_by_category=true")
        }

        return super.fetchPageList(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PagesByCategory>()

        val pageList = findCategoryByPageId(result, response.request.url.toString().filter { it.isDigit() }.toLong())
        return pageList.map {
            Page(it.number, "${it.url}?size=full", "${it.url}?size=full")
        }
    }

    fun findCategoryByPageId(pagesByCategory: PagesByCategory, pageId: Long): List<MangaDraftPageDTO> = pagesByCategory.values
        .first { pageList -> pageList.any { it.id == pageId } }

    companion object {
        private val dateFormats = listOf(
            // Pattern for English: "June 25, 2022"
            SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
            // Pattern for French: "25 juin 2022"
            SimpleDateFormat("d MMMM yyyy", Locale.FRENCH),
        )

        fun parseDate(dateText: String): Long {
            for (format in dateFormats) {
                try {
                    return format.parse(dateText)?.time ?: 0L
                } catch (e: Exception) {
                    // Try the next format in the list
                }
            }
            return 0L
        }
    }
}
