package eu.kanade.tachiyomi.extension.tr.tenshimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class TenshiManga : HttpSource() {
    override val name = "Tenshi Manga"

    override val baseUrl = "https://tenshimanga.com"

    // CDN used for search API responses and images
    private val CDN_URL = "https://manga1.efsaneler.can.re"

    override val lang = "tr"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/search?page=$page&search=&order=4")

    private fun popularMangaSelector() = "section[aria-label='series area'] .card"

    private fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = hasNextPage(document)
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/search?page=$page&search=&order=3")

    private fun latestUpdatesSelector() = popularMangaSelector()
    private fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = hasNextPage(document)
        return MangasPage(mangas, hasNextPage)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val url = "$baseUrl/manga/${query.substringAfter(URL_SEARCH_PREFIX)}"
            return client.newCall(GET(url, headers)).asObservableSuccess().map { response ->
                val document = response.asJsoup()
                when {
                    isMangaPage(document) -> MangasPage(listOf(mangaDetailsParse(response)), false)
                    else -> MangasPage(emptyList(), false)
                }
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$CDN_URL/series/search/navbar".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<List<SearchDto>>()
        val mangas = dto.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = CDN_URL + it.image
                url = "/manga/${it.id}/${title.lowercase().trim().replace(" ", "-")}"
            }
        }

        return MangasPage(mangas, false)
    }

    // Not used (JSON-based search)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        with(document.selectFirst("#content")!!) {
            title = selectFirst("h1")!!.text()
            thumbnail_url = selectFirst("img")?.absUrl("src")
            genre = select("a[href^='search?categories']").joinToString { it.text() }
            description = selectFirst("div.grid h2 + p")?.text()
            val pageStatus = selectFirst("span:contains(Durum) + span")?.text() ?: ""
            status = when {
                pageStatus.contains("Devam Ediyor", "Birakildi") -> SManga.ONGOING
                pageStatus.contains("Tamamlandi") -> SManga.COMPLETED
                pageStatus.contains("Ara Veridi") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            setUrlWithoutDomain(document.location())
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.list-episode a").map { element ->
            SChapter.create().apply {
                name = element.selectFirst("h3")!!.text()
                date_upload = dateFormat.tryParse(element.selectFirst("span")?.text())
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.select("script")
            .map { it.html() }.firstOrNull { pageRegex.find(it) != null }
            ?: return emptyList()

        val results = pageRegex.findAll(script).toList()
        return results.mapIndexed { index, result ->
            val url = result.groups.get(1)!!.value
            Page(index, imageUrl = "$CDN_URL/$url")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun isMangaPage(document: Document): Boolean =
        document.selectFirst("div.grid h2 + p") != null

    private fun hasNextPage(document: Document): Boolean {
        val navigation = document.selectFirst("section[aria-label='navigation']") ?: return false

        // Mevcut aktif sayfa numarasını bul (!bg-gray-200 !text-gray-800 class'larına sahip)
        val currentPageElement = navigation.selectFirst("a[class*='!bg-gray-200'][class*='!text-gray-800']")
        val currentPage = currentPageElement?.text()?.toIntOrNull() ?: return false

        // Tüm sayfa numaralarını topla
        val pageNumbers = navigation.select("a[href*='page=']")
            .mapNotNull { it.text().toIntOrNull() }
            .filter { it > 0 }

        // Eğer mevcut sayfadan büyük sayfa numarası varsa, sonraki sayfa var demektir
        return pageNumbers.any { it > currentPage }
    }

    private fun String.contains(vararg fragment: String): Boolean =
        fragment.any { trim().contains(it, ignoreCase = true) }

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
        val dateFormat = SimpleDateFormat("MMM d ,yyyy", Locale("tr"))
        val pageRegex = """\\"path\\":\\"([^"]+)\\""".trimIndent().toRegex()
    }
}

@Serializable
class SearchDto(val id: Int, val name: String, val image: String)
