package eu.kanade.tachiyomi.extension.es.inmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class InManga : HttpSource() {

    override val name = "InManga"

    override val baseUrl = "https://inmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val postHeaders = headers.newBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    private val imageCDN = "https://cdn1.intomanga.com"

    private fun requestBodyBuilder(page: Int, isPopular: Boolean): RequestBody = "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=&filter%5Bskip%5D=${(page - 1) * 10}&filter%5Btake%5D=10&filter%5Bsortby%5D=${if (isPopular) "1" else "3"}&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d="
        .toRequestBody(null)

    override fun popularMangaRequest(page: Int): Request = POST(
        url = "$baseUrl/manga/getMangasConsultResult",
        headers = postHeaders,
        body = requestBodyBuilder(page, true),
    )

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = POST(
        url = "$baseUrl/manga/getMangasConsultResult",
        headers = postHeaders,
        body = requestBodyBuilder(page, false),
    )

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val skip = (page - 1) * 10
        val body =
            "filter%5Bgeneres%5D%5B%5D=-1&filter%5BqueryString%5D=$query&filter%5Bskip%5D=$skip&filter%5Btake%5D=10&filter%5Bsortby%5D=1&filter%5BbroadcastStatus%5D=0&filter%5BonlyFavorites%5D=false&d="
                .toRequestBody(null)

        return POST("$baseUrl/manga/getMangasConsultResult", postHeaders, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val elements = document.select("body > a")

        val mangas = elements.map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.select("h4.m0").text()
                thumbnail_url = element.select("img").attr("abs:data-src")
            }
        }

        return MangasPage(mangas, elements.size == 10)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        document.select("div.col-md-3 div.panel.widget").let { info ->
            thumbnail_url = info.select("img").attr("abs:src")
            status = parseStatus(info.select("a.list-group-item:contains(estado) span").text())
        }
        document.select("div.col-md-9").let { info ->
            title = info.select("h1").text()
            description = info.select("div.panel-body").text()
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("En emisión") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request = GET(
        url = "$baseUrl/chapter/getall?mangaIdentification=${manga.url.substringAfterLast("/")}",
        headers = headers,
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<InMangaResultDto>()
        if (data.data.isNullOrEmpty()) {
            return emptyList()
        }

        val result = data.data.parseAs<InMangaResultObjectDto<InMangaChapterDto>>()
        if (!result.success) {
            return emptyList()
        }

        return result.result
            .map { chapterFromObject(it) }
            .sortedByDescending { it.chapter_number }
    }

    private fun chapterFromObject(chapter: InMangaChapterDto) = SChapter.create().apply {
        url = "/chapter/chapterIndexControls?identification=${chapter.identification}"
        name = "Chapter ${chapter.friendlyChapterNumber}"
        chapter_number = chapter.number?.toFloat() ?: 0f
        date_upload = DATE_FORMATTER.tryParse(chapter.registrationDate)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterId = document.select("input#ChapterIdentification").attr("value")
        val mangaId = document.select("input#MangaIdentification").attr("value")

        return document.select("img.ImageContainer").mapIndexed { i, img ->
            Page(i, imageUrl = "$imageCDN/i/m/$mangaId/c/$chapterId/o/${img.attr("id")}.jpg")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
