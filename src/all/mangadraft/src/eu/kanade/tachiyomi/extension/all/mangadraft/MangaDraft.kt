package eu.kanade.tachiyomi.extension.all.mangadraft
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangadraftCatalogResponseDto
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangadraftPageDTO
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.PagesByCategory
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.joinToString
import kotlin.getValue

class MangaDraft() : HttpSource() {
    override val name = "MangaDraft"
    override val baseUrl = "https://mangadraft.com"
    override val lang = "all"

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override val supportsLatest = true

    //make client follow redirects
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .followRedirects(true)
        .build()

    private val json: Json by injectLazy()

    // Popular
    override fun popularMangaRequest(page: Int) : Request {
        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("api")
                addPathSegment("catalog")
                addPathSegment("projects")
                addQueryParameter("order", "popular")
                addQueryParameter("type", "all")
                addQueryParameter("page", page.toString())
                addQueryParameter("number", "20")
            }.build(),
            headers
        )
    }
    override fun popularMangaParse(response: Response): MangasPage {
        val result = try {
            json.decodeFromString<MangadraftCatalogResponseDto>(response.body.string())
        } catch (e: Exception) {
            throw Exception("Failed to parse server response.")
        }
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
            true,
        )
    }

    // latest
    override fun latestUpdatesRequest(page: Int) : Request {
        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("api")
                addPathSegment("catalog")
                addPathSegment("projects")
                addQueryParameter("order", "news")
                addQueryParameter("type", "all")
                addQueryParameter("page", page.toString())
                addQueryParameter("number", "20")
            }.build(),
            headers
        )
    }
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    protected inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val typeFilter = filterList.findInstance<TypeFilter>()!!
        val orderFilter = filterList.findInstance<OrderFilter>()!!
        val sectionFilter = filterList.findInstance<SectionFilter>()!!
        val genreFilter = filterList.findInstance<GenreFilter>()!!
        val formatFilter = filterList.findInstance<FormatFilter>()!!
        val languageFilter = filterList.findInstance<LanguageFilter>()!!
        val statusFilter = filterList.findInstance<StatusFilter>()!!
        val sortFilter = filterList.findInstance<SortFilter>()!!

        return GET(
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
            headers,
        )
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // filters
    override fun getFilterList() = FilterList(
        TypeFilter(),
        OrderFilter(),
        SectionFilter(),
        GenreFilter(),
        FormatFilter(),
        LanguageFilter(),
        StatusFilter(),
        SortFilter(),
    )


    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()

        // Find the <script> containing window.project
        val scriptContent = doc.select("script")
            .firstOrNull { it.data().contains("window.project") }
            ?.data()
            ?: return SManga.create() // fallback if script not found

        // get the project part in the script
        val projectJson =
            Regex("""window\.project\s*=\s*(\{.*?\})\s*;""", RegexOption.DOT_MATCHES_ALL)
                .find(scriptContent)
                ?.groups?.get(1)?.value
                ?: "{}"

        val project = json.parseToJsonElement(projectJson).jsonObject

        return SManga.create().apply {
            title = project["name"]?.jsonPrimitive?.content.orEmpty()
            description = project["description"]?.jsonPrimitive?.content.orEmpty()
            author = doc.select("[title=Auteur]").text()
            artist = doc.select("[title=créateur]").text()
            genre =
                project["genres"]?.jsonArray?.joinToString(", ") { it.jsonObject["name"]?.jsonPrimitive?.content.orEmpty() }
                    .orEmpty()
            status = parseStatus(project["project_status"]?.jsonPrimitive?.content?.toInt())
        }
    }

    fun parseStatus(status: Int?) = when (status){
        0 -> SManga.ONGOING
        1 -> SManga.COMPLETED
        2 -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    fun chapterListSelector() = "div.mt-7 div a:not(:has(img))"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapterElements = document.select(chapterListSelector())
        return chapterElements.mapIndexed { i, it ->
            chapterFromElement(it, i)
        }.reversed()
    }

    private fun chapterFromElement(element: Element, index: Int): SChapter =
        SChapter.create().apply {
            chapter_number = index.toFloat() + 1
            try {
                name = "$chapter_number. ${
                element.selectFirst(".group-hover\\:text-secondary")?.text()
                }" ?: "Chapter $chapter_number"

                val request = Request.Builder()
                    .url("$baseUrl${element.attr("href")}")
                    .build()

                client.newCall(request).execute().use { response -> // final URL after redirects
                    // get this api request with the id of the first page of the chapter after redirect
                    setUrlWithoutDomain("$baseUrl/api/reader/listPages?first_page=${response.request.url.toString().substringAfterLast('/')}&grouped_by_category=true")
                }

                val dateText = element.selectFirst("div>span")?.text()
                if (!dateText.isNullOrBlank()) {
                    name = name.substringBefore(dateText)
                    date_upload = dateFormat.tryParse(dateText)
                }
            } catch (e: Exception) {
                throw Exception("One-Shots are not yet supported, please read in browser.")
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        val result = try {
            json.decodeFromString<PagesByCategory>(response.body.string())
        } catch (e: Exception) {
            throw Exception("Error parsing pages ${e.stackTrace}.")
        }

        val pageList = findCategoryByPageId(result, response.request.url.toString().filter { it.isDigit() }.toLong())
        return pageList.map {
            Page(it.number, "${it.url}?size=full", "${it.url}?size=full")
        }
    }
    fun findCategoryByPageId(pagesByCategory: PagesByCategory, pageId: Long): List<MangadraftPageDTO> {
        return pagesByCategory.values
            .first { pageList -> pageList.any { it.id == pageId } }
    }

    companion object {
        private fun getApiDateFormat() =
            SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)

        val dateFormat by lazy { getApiDateFormat() }
    }
}
