package eu.kanade.tachiyomi.extension.tr.uzaymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class UzayManga : ParsedHttpSource() {
    override val name = "Uzay Manga"

    override val baseUrl = "https://uzaymanga.com"

    override val lang = "tr"

    override val supportsLatest = true

    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/search?page=$page&search=&order=4")

    override fun popularMangaNextPageSelector() =
        "section[aria-label='navigation'] li:has(a[class~='!text-gray-800']) + li > a:not([href~='#'])"

    override fun popularMangaSelector() = "section[aria-label='series area'] .card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun latestUpdatesRequest(page: Int) =
        GET(baseUrl)

    override fun latestUpdatesNextPageSelector() = null

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException("Not used")

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // Ana sayfadaki grid içinde bulunan manga kartlarını seçiyoruz
        val elements =
            document.select("div.grid.grid-cols-1.lg\\:grid-cols-2.xl\\:grid-cols-3 > div")

        val mangas = mutableListOf<SManga>()

        for (i in 0 until elements.size) {
            try {
                val element = elements[i]

                // Manga başlığı
                val titleElement = element.select("h2")
                if (titleElement.isEmpty()) continue
                val title = titleElement[0].text()

                // Manga URL'si
                val linkElements = element.select("a")
                if (linkElements.isEmpty()) continue
                val mangaUrl = linkElements[0].attr("href")
                if (!mangaUrl.contains("/manga/")) continue

                // Manga kapak resmi
                val imgElements = element.select("div.card-image img")
                val thumbnail = if (imgElements.isNotEmpty()) imgElements[0].attr("src") else ""

                // En son bölüm bilgisi
                val chapterElements = element.select("div.flex.pt-0.flex-col.gap-2\\.5 > a")
                val hasNewChapter = chapterElements.isNotEmpty()

                if (hasNewChapter) {
                    val manga = SManga.create().apply {
                        this.title = title
                        setUrlWithoutDomain(mangaUrl)
                        thumbnail_url = when {
                            thumbnail.startsWith("https://") || thumbnail.startsWith("http://") -> thumbnail
                            thumbnail.startsWith("//") -> "https:$thumbnail"
                            thumbnail.isEmpty() -> null
                            thumbnail.startsWith("/") -> baseUrl + thumbnail
                            else -> "https://cdn1.uzaymanga.com/$thumbnail"
                        }
                    }
                    mangas.add(manga)
                }
            } catch (e: Exception) {
                // Hata durumunda pas geç
            }
        }

        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val url = "$baseUrl/manga/${query.substringAfter(URL_SEARCH_PREFIX)}"
            return client.newCall(GET(url, headers)).asObservableSuccess().map { response ->
                val document = response.asJsoup()
                when {
                    isMangaPage(document) -> MangasPage(listOf(mangaDetailsParse(document)), false)
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
        val dto = json.decodeFromString<List<SearchDto>>(response.body.string())
        val mangas = dto.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = CDN_URL + it.image
                url = "/manga/${it.id}/${title.lowercase().trim().replace(" ", "-")}"
            }
        }

        return MangasPage(mangas, false)
    }

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaSelector() = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
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

    override fun chapterListSelector() = "div.list-episode a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("h3")!!.text()
        date_upload = element.selectFirst("span")?.text()?.toDate() ?: 0L
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script")
            .map { it.html() }.firstOrNull { pageRegex.find(it) != null }
            ?: return emptyList()

        return pageRegex.findAll(script).mapIndexed { index, result ->
            val url = result.groups.get(1)!!.value
            Page(index, document.location(), "$CDN_URL/upload/series/$url")
        }.toList()
    }

    override fun imageUrlParse(document: Document) = ""

    private fun isMangaPage(document: Document): Boolean =
        document.selectFirst("div.grid h2 + p") != null

    private fun String.toDate(): Long =
        try {
            dateFormat.parse(this)!!.time
        } catch (_: Exception) {
            0L
        }

    private fun String.contains(vararg fragment: String): Boolean =
        fragment.any { trim().contains(it, ignoreCase = true) }

    companion object {
        const val CDN_URL = "https://cdn1.uzaymanga.com"
        const val URL_SEARCH_PREFIX = "slug:"
        val dateFormat = SimpleDateFormat("MMM d ,yyyy", Locale("tr"))
        val pageRegex = """\\"path\\":\\"([^"]+)\\""".trimIndent().toRegex()
    }
}

@Serializable
class SearchDto(val id: Int, val name: String, val image: String)
