package eu.kanade.tachiyomi.extension.id.bacami

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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Bacami : HttpSource() {

    override val name = "Bacami"
    override val baseUrl = "https://bacami.net"
    override val lang = "id"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.ENGLISH)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/custom-search/orderby/score/page/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.genre-card").map { element ->
            searchMangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("div.paginate a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.genre-info > a")!!.text()
        setUrlWithoutDomain(element.selectFirst("div.genre-cover > a")!!.attr("href"))
        thumbnail_url = element.selectFirst("div.genre-cover > a > img")?.let {
            it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
        }
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/custom-search/orderby/latest/page/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return GET("$baseUrl/search/$query/page/$page/", headers)
        }

        filters.firstInstanceOrNull<NewKomikFilter>()?.let {
            if (it.state) {
                return GET("$baseUrl/komik-baru/", headers)
            }
        }

        val orderby = filters.firstInstanceOrNull<OrderByFilter>()?.toUriPart() ?: "latest"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: "all"
        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: "all"
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: "all"

        val url = buildString {
            append("$baseUrl/custom-search/")
            if (orderby != "latest") append("orderby/$orderby/")
            if (status != "all") append("status/$status/")
            if (type != "all") append("type/$type/")
            if (genre != "all") append("genre/$genre/")
            append("page/$page/")
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val content = document.selectFirst("#komik > section.manga-content")!!
            title = content.selectFirst("header > h1")!!.text()
            thumbnail_url = content.selectFirst("figure .image-wrap img")?.let {
                it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
            }
            author = content.selectFirst(".info-item:contains(Author) .info-value")?.text()
                ?.ifEmpty { content.selectFirst("div > div > div:nth-child(3) > span.info-value")?.text() }
            genre = content.select("nav > span > a").joinToString { it.text() }
            status = parseStatus(document)

            val altTitle = content.select("p.manga-altname").text()
            description = content.select("p.manga-description").text().let {
                if (altTitle.isNotEmpty()) {
                    "$it\n\nAlternative Title: $altTitle".trim()
                } else {
                    it
                }
            }
        }
    }

    private fun parseStatus(document: Document): Int = when {
        document.selectFirst(".hot-tag, .project-tag") != null -> SManga.ONGOING
        document.selectFirst(".tamat-tag") != null -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ol.chapter-list > li").map { element ->
            SChapter.create().apply {
                val link = element.selectFirst("a.ch-link")!!
                name = link.text().substringAfter("–").trim()
                setUrlWithoutDomain(link.attr("href"))
                date_upload = dateFormat.tryParse(element.select("span.ch-date").text())
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scriptContent = document.selectFirst("script:containsData(imageUrls)")?.data()
            ?: return emptyList()

        val jsonString = scriptContent.substringAfter("imageUrls:").substringBefore("],").plus("]")
        val imageUrls = jsonString.parseAs<List<String>>()
        return imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filter diabaikan jika menggunakan pencarian teks."),
        Filter.Separator(),
        OrderByFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
        Filter.Separator(),
        Filter.Header("Centang 'Komik Baru' akan mengabaikan filter lain."),
        NewKomikFilter(),
    )
}
