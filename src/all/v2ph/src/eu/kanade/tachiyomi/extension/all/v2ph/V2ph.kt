package eu.kanade.tachiyomi.extension.all.v2ph

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class V2ph : HttpSource() {

    override val name = "V2PH"

    override val baseUrl = "https://www.v2ph.com"

    override val lang = "all"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category/best-quality?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".albums-list .card").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst("ul.pagination li.page-item a:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#latest-albums-title ~ .albums-list .card").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst("ul.pagination li.page-item a:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val category = filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart().orEmpty()
        val country = filters.firstInstanceOrNull<CountryFilter>()?.toUriPart().orEmpty()

        val url = when {
            category.isNotEmpty() -> "$baseUrl/category/$category?page=$page"
            country.isNotEmpty() -> "$baseUrl/country/$country?page=$page"
            else -> "$baseUrl/category/best-quality?page=$page"
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        response.checkPaywall()
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.selectFirst("dl dt:contains(Vendor) + dd a")?.text()
            artist = document.selectFirst("dl dt:contains(Model) + dd a")?.text()
            genre = document.select("dl dt:contains(Tags) + dd a").joinToString { it.text() }

            val photosCount = document.selectFirst("dl dt:contains(Photos) + dd")?.text()
            val intro = document.selectFirst(".album-intro")?.text()

            description = buildString {
                if (photosCount != null) {
                    append("Photos: $photosCount\n\n")
                }
                intro?.let(::append)
            }.trim()

            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        response.checkPaywall()
        val document = response.asJsoup()
        val dateStr = document.selectFirst("dl dt:contains(Date) + dd")?.text()

        val chapter = SChapter.create().apply {
            name = "Gallery"
            url = response.request.url.encodedPath
            date_upload = dateFormat.tryParse(dateStr)
        }
        return listOf(chapter)
    }

    // =============================== Pages ===============================
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter)).asObservableSuccess().map { response ->
            response.checkPaywall()
            val document = response.asJsoup()

            val photosCount = document.selectFirst("dl dt:contains(Photos) + dd")?.text()?.toIntOrNull() ?: 0
            val isGuest = document.selectFirst("a[href*='/login'], a[href*='/register']") != null

            if (isGuest && photosCount > 20) {
                throw Exception("V2PH Session expired. Please log in via WebView to view more than 20 images.")
            }

            val maxPage = (photosCount + 9) / 10

            val pages = document.select(".photos-list img").mapIndexed { index, img ->
                Page(index, imageUrl = img.attr("abs:src"))
            }.toMutableList()

            for (i in 2..maxPage) {
                val pageUrl = "$baseUrl${chapter.url}${if (chapter.url.contains("?")) "&" else "?"}page=$i"
                client.newCall(GET(pageUrl, headers)).execute().use { pageResponse ->
                    if (!pageResponse.isSuccessful) return@use
                    val offset = pages.size
                    pageResponse.asJsoup().select(".photos-list img").mapIndexedTo(pages) { index, img ->
                        Page(offset + index, imageUrl = img.attr("abs:src"))
                    }
                }
            }

            pages
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        Filter.Header("Note: Text Search ignores the filters below."),
        Filter.Header("If both Category and Country are set, Category takes precedence."),
        Filter.Separator(),
        CategoryFilter(),
        CountryFilter(),
    )

    // ============================= Utilities =============================
    private fun Response.checkPaywall() {
        if (request.url.encodedPath.startsWith("/user/")) {
            throw Exception("This album requires a V2PH premium account. Open in WebView to upgrade.")
        }
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("a.media-cover") ?: return null
        val titleEl = element.selectFirst(".card-body h6 a") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = titleEl.text()
            thumbnail_url = element.selectFirst(".card-cover img")?.attr("abs:src")
        }
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
