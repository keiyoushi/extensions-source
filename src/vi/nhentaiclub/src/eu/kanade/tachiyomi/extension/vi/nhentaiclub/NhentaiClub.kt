package eu.kanade.tachiyomi.extension.vi.nhentaiclub

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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NhentaiClub : HttpSource() {

    override val name = "NhentaiClub"

    override val lang = "vi"

    override val baseUrl = "https://nhentaiclub.site"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = listingRequest(
        page = page,
        genreSlug = "all",
        sort = "view",
    )

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = listingRequest(
        page = page,
        genreSlug = "all",
        sort = "recent-update",
    )

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var genreSlug = "all"
        var sort = "recent-update"
        var status: String? = null

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genreSlug = filter.toUriPart()
                is SortFilter -> sort = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                else -> {}
            }
        }

        return listingRequest(
            page = page,
            genreSlug = genreSlug,
            sort = sort,
            query = query.takeIf { it.isNotBlank() },
            status = status,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    override fun getFilterList(): FilterList = getFilters()

    private fun listingRequest(
        page: Int,
        genreSlug: String,
        sort: String,
        query: String? = null,
        status: String? = null,
    ): Request {
        val url = "$baseUrl/genre/$genreSlug".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", sort)
            addQueryParameter("page", page.toString())
            query?.let { addQueryParameter("search", it) }
            status?.let { addQueryParameter("status", it) }
        }.build()

        return GET(url, headers)
    }

    private fun parseMangaListPage(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("a[href^=/g/], a[href^=$baseUrl/g/]")
            .filter { it.selectFirst("img[alt]") != null }
            .distinctBy { it.absUrl("href") }
            .map { mangaFromElement(it) }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasExplicitNext = document.select("a[href*=\"page=${currentPage + 1}\"]").isNotEmpty()
        val hasNextPage = hasExplicitNext || mangaList.size >= 24

        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("img")!!.attr("alt").trim()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("div.flex.mb-8 img[alt][src*=thumbnail]")?.absUrl("src")

            author = document.metadataValue("Tác giả")
            description = document.selectFirst("#introduction-wrap p")?.text()?.trim()?.ifEmpty { null }

            genre = document.select("div.flex.flex-wrap.gap-2.my-2 a[href^=/genre/]")
                .map { it.text().trim() }
                .joinToString()
                .ifEmpty { null }

            status = when (document.metadataValue("Trạng thái")) {
                "Đang tiến hành" -> SManga.ONGOING
                "Hoàn thành" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun Document.metadataValue(label: String): String? {
        val row = select("div.flex.items-center")
            .firstOrNull { it.selectFirst("div span")?.text() == label }
            ?: return null

        return row.selectFirst("a")?.text()?.trim()?.ifEmpty { null }
            ?: row.select("span").lastOrNull()?.text()?.trim()?.ifEmpty { null }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".chapter-list #chapter-list-link a[href*=/read/]")
            .map { element ->
                val chapterUrl = element.absUrl("href").toHttpUrl().newBuilder()
                    .setQueryParameter("lang", "VI")
                    .build()
                    .toString()

                SChapter.create().apply {
                    setUrlWithoutDomain(chapterUrl)
                    name = element.selectFirst("span")!!.text()
                    date_upload = element.select("span").lastOrNull()?.text()?.let { dateFormat.tryParse(it) } ?: 0L
                }
            }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document.select("img.read-image")
            .map { element ->
                element.absUrl("src").ifEmpty {
                    element.absUrl("data-src")
                }
            }
            .filter { it.isNotEmpty() }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
