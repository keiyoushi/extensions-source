package eu.kanade.tachiyomi.extension.vi.vihentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ViHentai : HttpSource() {

    override val name = "ViHentai"

    override val baseUrl = "https://vi-hentai.moe"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach?sort=-views&page=$page&filter[status]=2,1", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("div.manga-vertical").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("div.p-2 a")!!
                setUrlWithoutDomain(linkElement.absUrl("href"))
                title = linkElement.text()
                thumbnail_url = element.selectFirst("div.cover")?.extractBackgroundImage()
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
                    is SortFilter -> addQueryParameter("sort", filter.toUriPart())
                    is StatusFilter -> {
                        val status = filter.toUriPart()
                        if (status.isNotEmpty()) {
                            addQueryParameter("filter[status]", status)
                        }
                    }
                    is GenreFilter -> {
                        val selectedGenres = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.id }
                        if (selectedGenres.isNotEmpty()) {
                            addQueryParameter("filter[accept_genres]", selectedGenres)
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList(): FilterList = getFilters()

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("span.grow.text-lg")!!.text()
            author = document.selectFirst("a[href*=/tac-gia/]")?.text()
            genre = document.select("div.mt-2.flex.flex-wrap.gap-1 a[href*=/the-loai/]").joinToString { it.text() }
            thumbnail_url = document.selectFirst("div.cover-frame div.cover")?.extractBackgroundImage()
            val plot = document.selectFirst("div.mg-plot")
            if (plot != null) {
                description = plot.select("p")
                    .drop(1)
                    .joinToString("\n") { it.text() }
                    .trim()
            }

            status = document.selectFirst("a[href*='filter[status]'] span")?.text()?.let { statusText ->
                when {
                    statusText.contains("Đã hoàn thành") -> SManga.COMPLETED
                    statusText.contains("Đang tiến hành") -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            } ?: SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("ul a[href*=/truyen/]").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("span.text-ellipsis")!!.text()
                date_upload = element.selectFirst("span.timeago[datetime]")
                    ?.attr("datetime")
                    .let { dateFormat.tryParse(it) }
            }
        }
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val packedScript = document.select("script").map { it.data() }
            .firstOrNull { it.contains("eval(function(h,u,n,t,e,r)") }
            ?: throw Exception("Could not find packed script with image data")

        return ViHentaiPacker.extractImageUrls(packedScript).mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private fun Element.extractBackgroundImage(): String? {
        val style = attr("style")
        return BACKGROUND_IMAGE_REGEX.find(style)?.groupValues?.get(1)
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    companion object {
        private val BACKGROUND_IMAGE_REGEX = Regex("""background-image:\s*url\(['"]?(.*?)['"]?\)""")
    }
}
