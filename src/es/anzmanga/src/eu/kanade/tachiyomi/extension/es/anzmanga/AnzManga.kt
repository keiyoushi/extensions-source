package eu.kanade.tachiyomi.extension.es.anzmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class AnzManga : HttpSource() {

    override val name = "AnzManga"

    override val baseUrl = "https://www.anzmanga25.com"

    override val lang = "es"

    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("dd MMM. yyyy", Locale.ENGLISH)
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/filterList?page=$page&sortBy=views&asc=false", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.col-sm-6 > div.media").map { element ->
            SManga.create().apply {
                val a = element.selectFirst(".media-heading a")!!
                title = a.text()
                setUrlWithoutDomain(a.attr("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-release?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.mangalist div.manga-item").map { element ->
            SManga.create().apply {
                val a = element.selectFirst("h3.manga-heading > a")!!
                title = a.text()
                setUrlWithoutDomain(a.attr("href"))
                // Construct thumbnail url since it's missing in the latest list
                val slug = url.substringAfterLast("/")
                thumbnail_url = "$baseUrl/uploads/manga/$slug/cover/cover_250x350.jpg"
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================
    override fun getFilterList() = FilterList(
        Filter.Header("La búsqueda de texto ignora los filtros."),
        CategoryFilter(),
        SortFilter(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/filterList".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        var sortBy = "views"
        var asc = "false"

        filters.firstInstanceOrNull<CategoryFilter>()?.let {
            url.addQueryParameter("cat", it.toUriPart())
        }

        filters.firstInstanceOrNull<SortFilter>()?.let {
            sortBy = it.toUriPart()
            asc = it.isAscending().toString()
        }

        url.addQueryParameter("alpha", "")
        url.addQueryParameter("sortBy", sortBy)
        url.addQueryParameter("asc", asc)
        url.addQueryParameter("author", "")
        url.addQueryParameter("artist", "")
        url.addQueryParameter("tag", "")

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.last() == "search") {
            val dto = response.parseAs<SearchResponseDto>()
            val mangas = dto.suggestions.map { it.toSManga(baseUrl) }
            // Auto-complete API does not provide pagination info
            return MangasPage(mangas, false)
        }

        // For filterList
        return popularMangaParse(response)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2.widget-title")?.text() ?: "Unknown"
            thumbnail_url = document.selectFirst(".boxed img")?.absUrl("src")
            author = document.selectFirst("dt:contains(Autor) + dd a")?.text()
            artist = document.selectFirst("dt:contains(Artist) + dd a")?.text()

            val statusText = document.selectFirst("dt:contains(Estado) + dd span")?.text()
            status = when (statusText?.lowercase()) {
                "publicándose" -> SManga.ONGOING
                "completado" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            genre = document.select("dt:contains(Categorías) + dd a").joinToString { it.text() }
            description = document.selectFirst("div.well p")?.text()
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.chapters > li").map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("h5.chapter-title-rtl a")!!
                val em = element.selectFirst("h5.chapter-title-rtl em")

                setUrlWithoutDomain(a.attr("href"))
                name = a.text() + if (em != null) " : ${em.text()}" else ""

                val dateText = element.selectFirst("div.date-chapter-title-rtl")?.text()
                date_upload = dateText?.let { dateFormat.tryParse(it) } ?: 0L
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div#all img.img-responsive").mapIndexed { index, img ->
            val url = img.attr("data-src").ifEmpty { img.attr("src") }.trim()
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
