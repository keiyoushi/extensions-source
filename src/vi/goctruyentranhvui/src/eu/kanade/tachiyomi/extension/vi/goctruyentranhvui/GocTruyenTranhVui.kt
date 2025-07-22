package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import eu.kanade.tachiyomi.network.GET
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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GocTruyenTranhVui() : HttpSource() {
    override val lang = "vi"

    override val baseUrl = "https://goctruyentranhvui17.com"

    override val name = "Goc Truyen Tranh Vui"

    private val apiUrl = "$baseUrl/api/v2"

    private val searchUrl = "$baseUrl/api/comic/search"

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.asJsoup()
        val mangaUrl = response.request.url.toString()
        val mangaId = res.selectFirst("input[id=comic-id-comment]")!!.attr("value")
        val chapter = client.newCall(GET("$baseUrl/api/comic/$mangaId/chapter?offset=21&limit=-1", headers)).execute().parseAs<ListChapter>()

        return chapter.result.chapters.map { it.toChapter(mangaUrl) }.ifEmpty {
            res.select(".chapter-list .list .col-md-6").map { itm ->
                SChapter.create().apply {
                    name = itm.select("a .chapter-info").text()
                    date_upload = parseDate(itm.select(".col-md-6 .text--disabled .d-flex").text())
                    setUrlWithoutDomain(itm.selectFirst("a")!!.absUrl("href"))
                }
            }
        }
    }

    private fun parseDate(date: String): Long = runCatching {
        val calendar = Calendar.getInstance()
        val number = date.replace(Regex("[^0-9]"), "").trim().toInt()
        when (date.replace(Regex("[0-9]"), "").trim()) {
            "phút trước" -> calendar.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            "giờ trước" -> calendar.apply { add(Calendar.HOUR, -number) }.timeInMillis
            "ngày trước" -> calendar.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            else -> dateFormat.tryParse(date)
        }
    }.getOrNull() ?: 0L

    override fun latestUpdatesParse(response: Response): MangasPage {
        val element = response.asJsoup()
        val manga = element.select(".row .col-md-2").map { itm ->
            SManga.create().apply {
                setUrlWithoutDomain(itm.select("a.mt-1").attr("href"))
                title = itm.select("a.mt-1").text()
                thumbnail_url = itm.selectFirst("img.lazy")!!.absUrl("data-original")
            }
        }
        val hasNextPage = element.select(".ma-3 a").text().isNotEmpty()
        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("truyen-cap-nhat")
            addQueryParameter("p", page.toString())
        }.build(),
        headers,
    )

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select(".v-card-title").text()
        genre = document.select(".group-content > .v-chip-link").joinToString { it.text().trim(',', ' ') }
        thumbnail_url = document.selectFirst("img.image")!!.absUrl("src")
        status = parseStatus(document.select(".mb-1:contains(Trạng thái:) span").text())
        author = document.select(".mb-1:contains(Tác giả:) span").text()
        description = document.select(".v-card-text").text()
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang thực hiện", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val pattern = Regex("chapterJson:\\s*`(.*?)`", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(html)
        val jsonString = match?.groups?.get(1)?.value ?: error("Không tìm thấy chapterJson")
        if (jsonString.isEmpty()) error("Không có nội dung. Hãy đăng nhập trong WebView") // loginRequired
        val result = jsonString.parseAs<ChapterWrapper>()
        val imageList = result.body.result.data
        return imageList.mapIndexed { i, url ->
            val finalUrl = if (url.startsWith("/image/")) {
                "$baseUrl$url"
            } else {
                url
            }
            Page(i, imageUrl = finalUrl)
        }
    }
    override fun imageUrlParse(response: Response): String = ""

    override fun popularMangaRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("home/filter")
            addQueryParameter("p", page.toString())
            addQueryParameter("value", "recommend")
        }.build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<ListManga>()
        val hasNextPage = res.result.p != 100
        return MangasPage(res.result.data.map { it.toManga(baseUrl) }, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query)
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val res = response.parseAs<SearchDTO>().result.map { it.toManga(baseUrl) }
        return MangasPage(res, hasNextPage = false)
    }
}
