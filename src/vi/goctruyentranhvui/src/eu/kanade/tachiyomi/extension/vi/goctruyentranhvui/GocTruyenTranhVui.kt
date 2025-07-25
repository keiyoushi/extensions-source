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

class GocTruyenTranhVui : HttpSource() {
    override val lang = "vi"

    override val baseUrl = "https://goctruyentranhvui17.com"

    override val name = "Goc Truyen Tranh Vui"

    private val apiUrl = "$baseUrl/api/v2"

    private val apiComic = "$baseUrl/api/comic"

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("home/filter")
            addQueryParameter("p", (page - 1).toString())
            addQueryParameter("value", "recommend")
        }.build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<ListManga>()
        val hasNextPage = res.result.p != 100
        return MangasPage(res.result.data.map { it.toManga(baseUrl) }, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("home/filter")
            addQueryParameter("p", (page - 1).toString())
            addQueryParameter("value", "updated-date")
        }.build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = "$baseUrl${manga.url}"
        val mangaId = mangaUrl.toHttpUrl().fragment
            ?: throw Exception("mangaId bị thiếu trong url: $mangaUrl")
        val url = apiComic.toHttpUrl().newBuilder()
            .addPathSegments(mangaId)
            .addPathSegments("chapter")
            .addQueryParameter("limit", "-1")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.toString().substringAfterLast("/") // slug
        val chapterJson = response.parseAs<ListChapter>()
        val chapterList = chapterJson.result.chapters.map { it.toChapter(slug) }

        if (chapterList.isNotEmpty()) {
            return chapterList
        }
        return fallbackChapterFromHtml(slug)
    }

    private fun fallbackChapterFromHtml(slug: String): List<SChapter> {
        val mangaPageUrl = "$baseUrl/truyen/$slug"
        val res = client.newCall(GET(mangaPageUrl, headers)).execute().asJsoup()

        return res.select(".chapter-list .list .col-md-6").map { itm ->
            SChapter.create().apply {
                name = itm.select("a .chapter-info").text()
                date_upload = parseDate(itm.select(".col-md-6 .text--disabled .d-flex").text())
                itm.selectFirst("a")?.absUrl("href")?.let { setUrlWithoutDomain(it) }
            }
        }
    }

    private fun parseDate(date: String): Long {
        val calendar = Calendar.getInstance()
        val number = date.replace(Regex("[^0-9]"), "").trim().toInt()
        return when (date.replace(Regex("[0-9]"), "").trim()) {
            "phút trước" -> calendar.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            "giờ trước" -> calendar.apply { add(Calendar.HOUR, -number) }.timeInMillis
            "ngày trước" -> calendar.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            else -> dateFormat.tryParse(date)
        }
    }

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
        val pattern = Regex("chapterJson:\\s*`(.*?)`")
        val match = pattern.find(html)
        val jsonPage = match?.groups?.get(1)?.value ?: throw Exception("Không tìm thấy Json") // find json
        if (jsonPage.isEmpty()) throw Exception("Không có nội dung. Hãy đăng nhập trong WebView") // loginRequired
        val result = jsonPage.parseAs<ChapterWrapper>()
        val imageList = result.body.result.data
        return imageList.mapIndexed { i, url ->
            val finalUrl = if (url.startsWith("/image/")) {
                baseUrl + url
            } else {
                url
            }
            Page(i, imageUrl = finalUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search")
            addQueryParameter("p", (page - 1).toString())
            addQueryParameter("searchValue", query)
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is GenreList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("categories%5B%5D", it) }

                    is StatusList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("status%5B%5D", it) }

                    is SortByList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("orders%5B%5D", it) }

                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        StatusList(getStatusList()),
        SortByList(getSortByList()),
        GenreList(getGenreList()),
    )
}
