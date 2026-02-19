package eu.kanade.tachiyomi.extension.pt.mangalivreblog

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.util.Calendar

class MangaLivreBlog : HttpSource() {

    override val name = "Manga Livre Blog"

    override val baseUrl = "https://mangalivre.blog"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1)
        .build()

    private var nonce: String? = null

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")

    // ============================== Popular ================================
    override fun popularMangaRequest(page: Int): Request {
        if (nonce == null) {
            val homeResponse = client.newCall(GET(baseUrl, headers)).execute()
            val homeDoc = homeResponse.asJsoup()
            val script = homeDoc.select("script").find { element -> element.html().contains("slimeReadPopular") }?.html()
            nonce = script?.let { html ->
                val regex = """"nonce":"([^"]+)"""".toRegex()
                regex.find(html)?.groupValues?.get(1)
            }
        }

        val nonceValue = nonce ?: throw Exception("Failed to extract nonce from home page")

        val formBody = FormBody.Builder()
            .add("action", "get_popular_manga")
            .add("period", "month")
            .add("nonce", nonceValue)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, formBody)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = response.parseAs<PopularResponse>()

        val document = Jsoup.parse(json.data.html)

        val mangas = document.select("div.popular-manga-item").map { el ->
            SManga.create().apply {
                title = el.selectFirst("h4.popular-manga-title a")!!.text()
                setUrlWithoutDomain(el.selectFirst("a")!!.attr("abs:href"))
                thumbnail_url = el.selectFirst("img")?.attr("abs:src")?.let(::cleanThumbnailUrl)
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val section = document.selectFirst("section.latest-section")

        val mangas = section?.select(".manga-card-modern")?.map { el ->
            SManga.create().apply {
                title = el.selectFirst("h3.manga-title-modern a")!!.text()
                setUrlWithoutDomain(el.selectFirst("a.manga-cover-link")!!.attr("abs:href"))
                thumbnail_url = el.selectFirst("img")?.attr("abs:src")?.let(::cleanThumbnailUrl)
            }
        } ?: emptyList()

        val hasNextPage = document.selectFirst("a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            } else {
                addPathSegment("pesquisa")
                addQueryParameter("s", "")
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> if (filter.state != 0) addQueryParameter("genre", filter.selectedValue())
                    is StatusFilter -> if (filter.state != 0) addQueryParameter("status", filter.selectedValue())
                    is RatingMinFilter -> addQueryParameter("rating_min", filter.selectedValue())
                    is SortFilter -> addQueryParameter("sort", filter.selectedValue())
                    is OrderFilter -> addQueryParameter("order", filter.selectedValue())
                    else -> {}
                }
            }

            if (page > 1) {
                addQueryParameter("paged", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".manga-grid.search-results-grid .manga-card").map { el ->
            SManga.create().apply {
                title = el.selectFirst("h3.manga-card-title")!!.text()
                setUrlWithoutDomain(el.selectFirst("a.manga-card-link")!!.attr("abs:href"))
                thumbnail_url = el.selectFirst("img")?.attr("abs:src")?.let(::cleanThumbnailUrl)
            }
        }

        val hasNextPage = document.selectFirst("a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ================================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.manga-title")!!.text()
            description = document.selectFirst(".synopsis-content")?.text()
            genre = document.select(".manga-tag").joinToString { it.text() }

            document.select(".manga-meta-item").forEach { item ->
                val label = item.selectFirst(".meta-label")?.text() ?: return@forEach
                val value = item.selectFirst(".meta-value")?.text() ?: return@forEach

                when (label) {
                    "Status:" -> status = parseStatus(value)
                    "Autor:" -> author = value
                    "Artista:" -> artist = value
                }
            }
        }
    }

    private fun parseStatus(status: String) = when (status) {
        "Em Lançamento", "Em Andamento" -> SManga.ONGOING
        "Completo" -> SManga.COMPLETED
        "Cancelado" -> SManga.CANCELLED
        "Pausado", "Hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ================================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapters-list .chapter-item").map { el ->
            SChapter.create().apply {
                name = el.selectFirst(".chapter-number")?.text() ?: "Capítulo"
                setUrlWithoutDomain(el.selectFirst(".chapter-link")!!.attr("abs:href"))
                date_upload = parseRelativeDate(el.selectFirst(".chapter-date")?.text())
            }
        }
    }

    // ============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".chapter-image-container img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = ""

    // ============================== Helpers ================================
    private fun cleanThumbnailUrl(url: String): String = url.replace("-150x150", "")

    private fun parseRelativeDate(date: String?): Long {
        if (date.isNullOrEmpty()) return 0L

        val number = Regex("""\d+""").find(date)?.value?.toIntOrNull() ?: return 0L
        val cal = Calendar.getInstance()

        return when {
            date.contains("ano", true) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            date.contains("mês", true) || date.contains("meses", true) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("semana", true) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
            date.contains("dia", true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hora", true) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minuto", true) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            else -> 0L
        }
    }

    override fun getFilterList() = getFilters()
}
