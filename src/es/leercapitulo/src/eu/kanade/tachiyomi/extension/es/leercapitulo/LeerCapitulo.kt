package eu.kanade.tachiyomi.extension.es.leercapitulo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeerCapitulo : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(1, 3.seconds) { it.host == baseUrlHost }
        .build()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("h2:contains(Populares), h3:contains(Populares), .title:contains(Populares)")
            .first()?.parent()?.select(".lc-side-item")
            ?.mapNotNull { it.toSManga() } ?: emptyList()

        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("h2:contains(Ultimos), h3:contains(Ultimos), h2:contains(Últimos), h3:contains(Últimos)")
            .first()?.parent()?.select(".lc-side-item")
            ?.mapNotNull { it.toSManga() } ?: emptyList()

        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/manga/".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query)
        }

        for (filter in filters) {
            if (filter is GenreFilter && filter.state != 0) {
                urlBuilder.addQueryParameter("genre", filter.toUriPart())
            } else if (filter is StatusFilter && filter.state != 0) {
                urlBuilder.addQueryParameter("status", filter.toUriPart())
            }
        }

        urlBuilder.addQueryParameter("page", page.toString())
        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.lc-card").mapNotNull { it.toSManga() }.distinctBy { it.url }
        val hasNextPage = document.selectFirst("ul.pagination li.active + li:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSManga(): SManga? {
        val link = selectFirst("a.lc-card-name") ?: selectFirst("a.lc-card-cover") ?: selectFirst("a") ?: return null
        val url = link.attr("abs:href")
        if (url.isBlank()) return null

        val titleText = selectFirst("a.lc-card-name, .lc-side-name")?.text() ?: selectFirst(".n, .title")?.text() ?: link.attr("title").takeIf { it.isNotBlank() } ?: link.text()
        if (titleText.isBlank()) return null

        val img = selectFirst("img")

        return SManga.create().apply {
            setUrlWithoutDomain(url)
            title = titleText.trim()
            thumbnail_url = img?.imgAttr()
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("article h1, h1")?.text()?.trim() ?: ""

            val altNames = document.selectFirst("article p.lc-muted")?.text()?.trim()
            val desc = document.selectFirst("#sinopsis p")?.text()?.trim()
            description = buildString {
                if (!desc.isNullOrEmpty()) append(desc)
                if (!altNames.isNullOrEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alt name(s): ")
                    append(altNames)
                }
            }

            genre = document.select("article .badge").joinToString { it.text().trim() }

            val facts = document.select("ul.lc-facts li")
            author = facts.firstOrNull { it.selectFirst("span.k")?.text()?.contains("Autor", true) == true }
                ?.selectFirst("span:not(.k)")?.text()?.trim()
            artist = facts.firstOrNull { it.selectFirst("span.k")?.text()?.contains("Dibujo", true) == true }
                ?.selectFirst("span:not(.k)")?.text()?.trim()

            val statusText = facts.firstOrNull { it.selectFirst("span.k")?.text()?.contains("Estado", true) == true }
                ?.selectFirst("a, span:not(.k)")?.text()
            status = statusText?.toStatus() ?: SManga.UNKNOWN

            thumbnail_url = document.selectFirst(".lc-cover-lg img, article img")?.imgAttr()
        }
    }

    override fun chapterListParse(response: Response) = response.asJsoup()
        .select("#chapterList a.lc-chapter-row")
        .mapNotNull { element ->
            val url = element.attr("abs:href")
            if (url.isBlank()) null else {
                val nameText = element.selectFirst("span.n")?.text() ?: element.text()
                val dateText = element.selectFirst("span.d")?.text()
                
                SChapter.create().apply {
                    setUrlWithoutDomain(url)
                    name = nameText.trim()
                    date_upload = dateText?.let {
                        runCatching { dateFormat.parse(it)?.time }.getOrNull()
                    } ?: 0L
                }
            }
        }

    override fun pageListParse(response: Response) = response.asJsoup()
        .select("#lcPages img, main.lc-pages img, .lc-pages img")
        .mapNotNull { element ->
            val src = element.imgAttr()
            if (src.startsWith("http")) src else null
        }.also {
            if (it.isEmpty()) throw Exception("No se encontraron páginas en este capítulo")
        }.mapIndexed { i, imageUrl ->
            Page(i, imageUrl = imageUrl)
        }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("src") -> attr("abs:src")
        else -> ""
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun String.toStatus() = when (this.lowercase().trim()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "paused", "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        StatusFilter(),
    )
}
