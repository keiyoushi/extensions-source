package eu.kanade.tachiyomi.extension.es.inmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class InManga : ParsedHttpSource() {

    override val name = "InManga"

    override val baseUrl = "https://inmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val postHeaders = headers.newBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    private val json: Json by injectLazy()

    private val imageCDN = "https://pack-yak.intomanga.com/"

    /**
     * Returns RequestBody to retrieve latest or populars Manga.
     *
     * @param page Current page number.
     * @param isPopular If is true filter sortby = 1 else sortby = 3
     * sortby = 1: Populars
     * sortby = 3: Latest
     */
    private fun requestBodyBuilder(page: Int, isPopular: Boolean): RequestBody = "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=&filter%5Bskip%5D=${(page - 1) * 10}&filter%5Btake%5D=10&filter%5Bsortby%5D=${if (isPopular) "1" else "3"}&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d=".toRequestBody(null)

    override fun popularMangaRequest(page: Int) = POST(
        url = "$baseUrl/manga/getMangasConsultResult",
        headers = postHeaders,
        body = requestBodyBuilder(page, true),
    )

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "body"

    override fun latestUpdatesRequest(page: Int) = POST(
        url = "$baseUrl/manga/getMangasConsultResult",
        headers = postHeaders,
        body = requestBodyBuilder(page, false),
    )

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element = element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val skip = (page - 1) * 10
        val body =
            "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=$query&filter%5Bskip%5D=$skip&filter%5Btake%5D=10&filter%5Bsortby%5D=1&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d=".toRequestBody(
                null,
            )

        return POST("$baseUrl/manga/getMangasConsultResult", postHeaders, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val document = response.asJsoup()
        document.select(searchMangaSelector()).map { mangas.add(searchMangaFromElement(it)) }

        return MangasPage(mangas, document.select(searchMangaSelector()).count() == 10)
    }

    override fun searchMangaSelector() = "body > a"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.select("h4.m0").text()
        thumbnail_url = element.select("img").attr("abs:data-src")
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select("div.col-md-3 div.panel.widget").let { info ->
            thumbnail_url = info.select("img").attr("abs:src")
            status = parseStatus(info.select(" a.list-group-item:contains(estado) span").text())
        }
        document.select("div.col-md-9").let { info ->
            title = info.select("h1").text()
            description = info.select("div.panel-body").text()
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("En emisión") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga) = GET(
        url = "$baseUrl/chapter/getall?mangaIdentification=${manga.url.substringAfterLast("/")}",
        headers = headers,
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        // The server returns a JSON with data property that contains a string with the JSON,
        // so is necessary to decode twice.
        val data = json.decodeFromString<InMangaResultDto>(response.body.string())
        if (data.data.isNullOrEmpty()) {
            return emptyList()
        }

        val result = json.decodeFromString<InMangaResultObjectDto<InMangaChapterDto>>(data.data)
        if (!result.success) {
            return emptyList()
        }

        return result.result
            .map { chap -> chapterFromObject(chap) }
            .sortedBy { it.chapter_number.toInt() }.reversed()
    }

    override fun chapterListSelector() = "not using"

    private fun chapterFromObject(chapter: InMangaChapterDto) = SChapter.create().apply {
        url = "/chapter/chapterIndexControls?identification=${chapter.identification}"
        name = "Chapter ${chapter.friendlyChapterNumber}"
        chapter_number = chapter.number!!.toFloat()
        date_upload = parseChapterDate(chapter.registrationDate)
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    private fun parseChapterDate(string: String): Long {
        return DATE_FORMATTER.parse(string)?.time ?: 0L
    }

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val ch = document.select("[id=\"FriendlyChapterNumberUrl\"]").attr("value")
        val title = document.select("[id=\"FriendlyMangaName\"]").attr("value")

        document.select("img.ImageContainer").forEachIndexed { i, img ->
            add(Page(i, "", "$imageCDN/images/manga/$title/chapter/$ch/page/${i + 1}/${img.attr("id")}"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()

    companion object {
        val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }
}
