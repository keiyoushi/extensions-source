package eu.kanade.tachiyomi.extension.tr.mangadusleri

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Mangadusleri : HttpSource() {
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl$BROWSE_PATH")

    private val loginCheckInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.request.url.toString().contains("register.php") ||
            response.request.url.toString().contains("login")
        ) {

            response.close()

            throw IOException("Bu içeriği görüntülemek için lütfen WebView'ı (küre simgesini) açın ve giriş yapın!")
        }

        response
    }

    override val client = network.client.newBuilder()
        .addInterceptor(loginCheckInterceptor)
        .rateLimit(3)
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl$BROWSE_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("sort", POPULAR_SORT)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseBrowsePage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl$BROWSE_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("sort", LASTEST_SORT)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseBrowsePage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
        val order = filterList.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: LASTEST_SORT

        val url = if (query.isNotBlank()) {
            if (KNOWN_GENRES.any { it.equals(query, ignoreCase = true) }) {
                "$baseUrl$GENRE_PATH".toHttpUrl().newBuilder()
                    .addQueryParameter("name", query)
                    .addQueryParameter("page", page.toString())
                    .build()
            } else {
                "$baseUrl$SEARCH_PATH".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("page", page.toString())
                    .build()
            }
        } else {
            "$baseUrl$BROWSE_PATH".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .apply {
                    if (order != LASTEST_SORT) {
                        addQueryParameter("sort", order)
                    }
                }
                .build()
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseBrowsePage(response)

    // ============================== Manga List ============================

    private fun parseBrowsePage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".recent-grid .recent-card").mapNotNull { element ->
            val mangaLink = element.selectFirst("a, .recent-card-body a") ?: return@mapNotNull null
            val mangaTitle = element.selectFirst(".recent-card-body a .recent-card-title")?.text() ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(mangaLink.absUrl("href"))
                title = mangaTitle
                thumbnail_url = element.selectFirst("img.recent-card-img")?.let { img ->
                    img.absUrl("data-src").ifEmpty { img.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst("a.page-link:contains(İleri)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("div.info-col h1")!!.text()
            thumbnail_url = document.selectFirst("img.manga-cover-big")?.absUrl("src")
            genre = document.select("a[href*=genre.php] span")
                .joinToString { it.text().trim() }
                .ifEmpty { null }
            author = document.select("div:containsOwn(Yazar)").first()?.nextElementSibling()?.text()
            artist = document.select("div:containsOwn(Çizer)").first()?.nextElementSibling()?.text()
            description = document.selectFirst("div.info-col .manga-summary-box")?.text()?.trim()
            status = parseStatus(document.selectFirst(".status-badge"))
        }
    }

    private fun parseStatus(element: Element?): Int = when {
        element?.hasClass("status-ongoing") == true -> SManga.ONGOING
        element?.hasClass("status-completed") == true -> SManga.COMPLETED

        element?.text()?.contains("Devam Ediyor", ignoreCase = true) == true -> SManga.ONGOING
        element?.text()?.contains("Tamamlandı", ignoreCase = true) == true -> SManga.COMPLETED

        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val chapterElements = document.select("#chapterList a.chapter-grid-item-new")

        return chapterElements.map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))

                name = element.selectFirst("div > span")!!.text().trim()

                val dateText = element.select("div > span").last()?.text()

                dateText?.let { date_upload = parseDate(it) }
            }
        }.reversed()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterUrl = response.request.url.toString()
        val images = document.select("img[alt*='Manga Sayfası'], img[alt*='Resmi']")

        return images.mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-src")
                .ifEmpty { element.absUrl("data-lazy-src") }
                .ifEmpty { element.absUrl("src") }
                .replace("\r", "")
                .ifEmpty { null }
                ?.normalizeImageUrl()
                ?: return@mapIndexedNotNull null

            Page(index, chapterUrl, imageUrl)
        }.distinctBy { it.imageUrl }
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Helpers ===============================

    private fun String.normalizeImageUrl(): String = if (startsWith("//")) "https:$this" else this

    private fun parseDate(dateStr: String): Long = dateFormat.tryParse(dateStr)

    companion object {
        private const val SEARCH_PATH = "/search.php"
        private const val BROWSE_PATH = "/latest.php"
        private const val GENRE_PATH = "/genre.php"
        private const val LASTEST_SORT = "latest"
        private const val POPULAR_SORT = "popular"

        private val KNOWN_GENRES = listOf(
            "+18", "Aksiyon", "Cinsellik", "Drama", "Kavga",
            "Manhwa", "Romantic", "Romantik", "Romatik", "roamtik",
            "Smut", "Tarih", "Yaoi", "Yetişkin",
        )
    }
}
