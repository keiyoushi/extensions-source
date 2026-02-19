package eu.kanade.tachiyomi.extension.vi.mimihentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MiMiHentai : HttpSource() {

    override val name = "MiMiHentai"

    override val baseUrl = "https://mimihentai.net"

    override val lang = "vi"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 14, 1, TimeUnit.MINUTES)
        .addNetworkInterceptor {
            val request = it.request()
            val response = it.proceed(request)

            if (request.url.toString().startsWith(baseUrl)) {
                if (response.code == 429) {
                    throw IOException("Bạn đang request quá nhanh!")
                }
            }
            response
        }
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach?sort=-views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("a.group").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("h1")!!.text()
                thumbnail_url = element.selectFirst("img")?.let {
                    it.absUrl("data-src")
                        .ifEmpty { it.absUrl("src") }
                }
            }
        }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = document.selectFirst("a[href*='page=${currentPage + 1}']") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val selectedGenres = filter.state.filter { it.state }.joinToString(",") { it.id }
                        if (selectedGenres.isNotEmpty()) {
                            addQueryParameter("filter[accept_genres]", selectedGenres)
                        }
                    }

                    is StatusFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("filter[status]", filter.toUriPart())
                        }
                    }

                    is SortFilter -> {
                        addQueryParameter("sort", filter.toUriPart())
                    }

                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("div.title p")!!.text()
            thumbnail_url = document.selectFirst("img.rounded.shadow-md.w-full")?.let {
                it.absUrl("data-src")
                    .ifEmpty { it.absUrl("src") }
            }
            author = document.selectFirst("a[href*='/tac-gia/']")?.text()
            genre = document.select("a[href*='/the-loai/']").joinToString { it.text() }

            val bodyText = document.body().text()
            status = when {
                bodyText.contains("Đã hoàn thành") -> SManga.COMPLETED
                bodyText.contains("Đang tiến hành") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }

            description = document.selectFirst("div.mt-4")?.ownText()
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div.chapter-list a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("h1")?.text()
                    ?: element.attr("title")
                    ?: element.text()

                val dateText = element.parent()?.selectFirst("span.timeago")?.text()
                    ?: element.parent()?.parent()?.selectFirst("span.timeago")?.text()
                date_upload = parseRelativeDate(dateText)
            }
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance()
        val number = NUMBER_REGEX.find(dateStr)?.value?.toIntOrNull() ?: return 0L

        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    companion object {
        private val NUMBER_REGEX = Regex("\\d+")
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("img.lazy").mapIndexed { index, element ->
            val imageUrl = element.absUrl("src").ifEmpty {
                element.absUrl("data-src")
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Related ================================
    // dirty hack to disable suggested mangas on Komikku due to heavy rate limit
    // https://github.com/komikku-app/komikku/blob/4323fd5841b390213aa4c4af77e07ad42eb423fc/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt#L176-L184
    @Suppress("Unused")
    @JvmName("getDisableRelatedMangasBySearch")
    fun disableRelatedMangasBySearch() = true

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()
}
