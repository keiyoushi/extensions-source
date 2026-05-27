package eu.kanade.tachiyomi.extension.en.bbato

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Bbato : HttpSource() {

    override val name = "Bbato"

    override val baseUrl = "https://bbato.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/") // Enforce root Referer, bypasses CDN 403 blocks for images

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // Site splits popular mangas into tabs: day, week, month. We merge and remove duplicates.
        val mangas = document.select("#most-viewed .tab-content .swiper-slide.unit").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                title = element.selectFirst("span")?.text() ?: throw Exception("Missing title")
                thumbnail_url = element.selectFirst("img")?.getImageUrl()
            }
        }.distinctBy { it.url }

        // Home page section doesn't natively paginate.
        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page == 1) "/updated" else "/updated/page/$page"
        return GET("$baseUrl$path", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".original.card-lg .unit").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.poster")!!.attr("abs:href"))
                title = element.selectFirst(".info > a")?.text() ?: throw Exception("Missing title")
                thumbnail_url = element.selectFirst("a.poster img")?.getImageUrl()
            }
        }

        val hasNext = document.selectFirst(".pagination a[rel=next]") != null
        return MangasPage(mangas, hasNext)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/filter".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", query)

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

            filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("type[]", it.value) }
            filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("genre[]", it.value) }
            filters.firstInstanceOrNull<StatusFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("status[]", it.value) }
            filters.firstInstanceOrNull<YearFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("year[]", it.value) }

            filters.firstInstanceOrNull<MinChapterFilter>()?.selectedValue?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("minchap", it)
            }

            filters.firstInstanceOrNull<SortFilter>()?.selectedValue?.let {
                addQueryParameter("sort", it)
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1[itemprop=name]")?.text() ?: throw Exception("Missing title")
            author = document.select(".meta div:has(span:contains(Author)) a").joinToString { it.text() }
            description = document.selectFirst(".description")?.text()
            genre = document.select(".meta div:has(span:contains(Genres)) a").joinToString { it.text() }
            status = document.selectFirst(".info > p")?.text().toStatus()
            thumbnail_url = document.selectFirst(".poster img")?.getImageUrl()
        }
    }

    private fun String?.toStatus(): Int = when (this?.lowercase(Locale.ENGLISH)) {
        "ongoing", "releasing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "on hiatus" -> SManga.ON_HIATUS
        "discontinued", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ==============================

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val chapterHeaders = headersBuilder()
            .set("Accept", "application/json, text/javascript, */*; q=0.01")
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", "$baseUrl${manga.url}")
            .build()
        return GET("$baseUrl/get-chapter-list?slug=$slug", chapterHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaSlug = response.request.url.queryParameter("slug") ?: ""
        val responseDto = response.parseAs<ChapterListResponse>()

        return responseDto.toSChapterList(mangaSlug, dateFormat)
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".pages .page:not(.notice-page) img").mapIndexedNotNull { index, img ->
            img.getImageUrl()?.let { Page(index, imageUrl = it) }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = getFilters()

    // ============================= Utilities =============================

    private fun Element.getImageUrl(): String? = attr("abs:data-src").ifEmpty { attr("abs:src") }.takeIf { it.isNotEmpty() }
}
