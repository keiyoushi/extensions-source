package eu.kanade.tachiyomi.extension.all.mangadraft
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangaDraftCatalogResponseDto
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangaDraftPageDTO
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangaDraftProjectDto
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.PagesByCategory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaDraft : KeiSource() {

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = catalogParse(
        client.get(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("api")
                addPathSegment("catalog")
                addPathSegment("projects")
                addQueryParameter("order", "popular")
                addQueryParameter("type", "all")
                addQueryParameter("page", page.toString())
                addQueryParameter("number", "20")
            }.build(),
        ),
    )

    private fun catalogParse(response: Response): MangasPage {
        val result = response.parseAs<MangaDraftCatalogResponseDto>()

        val mangas = result.data
        return MangasPage(
            mangas.map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.url)
                    title = it.name
                    thumbnail_url = it.avatar
                    description = it.description
                    genre = it.genres
                }
            },
            // if there is less than 20 received there won't be a next page
            mangas.count() >= 20,
        )
    }

    // latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = catalogParse(
        client.get(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("api")
                addPathSegment("catalog")
                addPathSegment("projects")
                addQueryParameter("order", "news")
                addQueryParameter("type", "all")
                addQueryParameter("page", page.toString())
                addQueryParameter("number", "20")
            }.build(),
        ),
    )

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val typeFilter = filters.firstInstance<TypeFilter>()
        val orderFilter = filters.firstInstance<OrderFilter>()
        val sectionFilter = filters.firstInstance<SectionFilter>()
        val genreFilter = filters.firstInstance<GenreFilter>()
        val formatFilter = filters.firstInstance<FormatFilter>()
        val languageFilter = filters.firstInstance<LanguageFilter>()
        val statusFilter = filters.firstInstance<StatusFilter>()
        val sortFilter = filters.firstInstance<SortFilter>()

        return catalogParse(
            client.get(
                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("api")
                    addPathSegment("catalog")
                    addPathSegment("projects")
                    addQueryParameter("type", typeFilter.toUriPart())
                    addQueryParameter("order", orderFilter.toUriPart())
                    addQueryParameter("section", sectionFilter.toUriPart())
                    addQueryParameter("genre", genreFilter.toUriPart())
                    addQueryParameter("format", formatFilter.toUriPart())
                    addQueryParameter("language", languageFilter.toUriPart())
                    addQueryParameter("status", statusFilter.toUriPart())
                    addQueryParameter("order_all", sortFilter.toUriPart())
                    addQueryParameter("page", page.toString())
                    addQueryParameter("number", "20")
                }.build(),
            ),
        )
    }

    // filters
    override fun getFilterList(data: JsonElement?) = FilterList(
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    // Details
    private fun mangaDetailsParse(doc: Document): SManga {
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

    private fun chapterListParse(document: Document): List<SChapter> {
        val chapterElements = document.select(chapterListSelector())

        val isNotOneShot = chapterElements[0].attr("href").contains("c.")
        var chapterList: List<SChapter>
        if (isNotOneShot) {
            chapterList = chapterElements.mapIndexed { i, it ->
                chapterFromElement(it, i)
            }.reversed()
        } else {
            chapterList = listOf<SChapter>(chapterFromElement(chapterElements[0], 0))
        }

        return chapterList
    }

    private fun chapterFromElement(element: Element, index: Int): SChapter = SChapter.create().apply {
        chapter_number = index.toFloat()
        name = "$chapter_number. ${element.selectFirst(".group-hover\\:text-secondary")?.text() ?: ""}"

        url = "${element.absUrl("href")}"

        val dateText = element.selectFirst("div>span")?.text()
        if (!dateText.isNullOrBlank()) {
            name = name.substringBefore(dateText)
            date_upload = dateFormat.tryParseDate(dateText)
        }
    }

    // chapter urls are stored as absolute urls
    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val firstPage = if (chapter.url.contains("c.")) {
            // the reader redirects to the id of the first page of the chapter
            client.get(chapter.url).use { response ->
                response.request.url.toString().substringAfterLast('/').filter { it.isDigit() }
            }
        } else {
            chapter.url.substringAfterLast('/').filter { it.isDigit() }
        }

        val result = client.get("$baseUrl/api/reader/listPages?first_page=$firstPage&grouped_by_category=true")
            .parseAs<PagesByCategory>()

        val pageList = findCategoryByPageId(result, firstPage.toLong())
        return pageList.map {
            Page(it.number, "${it.url}?size=full", "${it.url}?size=full")
        }
    }
    fun findCategoryByPageId(pagesByCategory: PagesByCategory, pageId: Long): List<MangaDraftPageDTO> = pagesByCategory.values
        .first { pageList -> pageList.any { it.id == pageId } }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
    }
}
