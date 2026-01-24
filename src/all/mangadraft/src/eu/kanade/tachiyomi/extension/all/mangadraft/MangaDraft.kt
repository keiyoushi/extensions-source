package eu.kanade.tachiyomi.extension.all.mangadraft
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangadraftCatalogResponseDto
import eu.kanade.tachiyomi.extension.all.mangadraft.dto.MangadraftPagesResponseDto
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.joinToString
import kotlin.getValue

class MangaDraft() : HttpSource() {
    override val baseUrl = "https://mangadraft.com"

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override val lang = "all"
    override val name = "MangaDraft"
    override val supportsLatest = true

    //region unused
    //endregion

    // Popular

    override fun getMangaUrl(manga: SManga): String {
        return super.getMangaUrl(manga)
    }

    private val json: Json by injectLazy()
    private var apiCookies: String = ""

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/catalog/projects?order=popular&type=all&page=$page&number=20", headers)
    override fun popularMangaParse(response: Response): MangasPage {
        val result = try {
            json.decodeFromString<MangadraftCatalogResponseDto>(response.body.string())
        } catch (e: Exception) {
            apiCookies = ""
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
    } // couldn't reach unable to resolve host no address asociated

    // latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/catalog/projects?order=news&type=all&page=$page&number=20", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addQueryParameter("kw", query)
                addQueryParameter("page", page.toString())
            }.build(),
            headers,
        )
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create().apply {
            with(response.asJsoup()) {
                title = select("h1>span:not([class])").text()
                description = select("div.line-clamp-2>div").text()

                genre = select("div.mt-5>a").joinToString(", ") {
                    it.text()
                }
            }
        }
    }

    fun chapterListSelector() = "div.mt-7 div a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val title = document.select("h1>span:not([class])").text()
        return document.select(chapterListSelector()).map { chapterFromElement(it, title) }
    }

    private fun chapterFromElement(element: Element, title: String): SChapter = SChapter.create().apply {
        val chapterId = element.attr("href").filter { it.isDigit() }
        setUrlWithoutDomain("$baseUrl/api/project-category/$chapterId/pages")
        name = element.selectFirst("h3")!!.text()

        date_upload = dateFormat.tryParse(element.selectFirst("div>span")!!.text())
        chapter_number = element.selectFirst("div>b")!!.text().filter { it.isDigit() }.toFloat()
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = try {
            json.decodeFromString<MangadraftPagesResponseDto>(response.body.string())
        } catch (e: Exception) {
            apiCookies = ""
            throw Exception("Failed to parse server response.")
        }
        val pagesData = result.data.reversed()
        return pagesData.map {
            Page(it.pageNumber, it.url, "${it.image?.img}?size=full")
        }
    }

    companion object {
        private fun getApiDateFormat() =
            SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)
        val dateFormat by lazy { getApiDateFormat() }
//        val chapterNameDateFormat by lazy { chapterNameDateFormat() }
    }
}

//    override fun chapterFromElement(element: Element) = SChapter.create().apply {
//        setUrlWithoutDomain(element.select("link[rel=canonical]").attr("abs:href"))
//        chapter_number = 0F
//        name = "GALLERY"
//        date_upload = getDate(element.select("time").text())
//    }

// Pages
//    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

//    private fun getDate(str: String): Long {
//        return try {
//            DATE_FORMAT.parse(str)?.time ?: 0L
//        } catch (e: ParseException) {
//            0L
//        }
//    }

//    companion object {
//        private val DATE_FORMAT by lazy {
//            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
//        }
//    }
