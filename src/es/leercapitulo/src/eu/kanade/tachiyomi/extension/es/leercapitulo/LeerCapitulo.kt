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
import keiyoushi.utils.firstInstanceOrNull
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

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== POPULARES ==============================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.lc-slide").mapNotNull { element ->
            val link = element.selectFirst("a.lc-slide-name") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.text()
                thumbnail_url = element.selectFirst("a.lc-slide-cover img")?.imgAttr()
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== RECIENTES ==============================
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.lc-release").mapNotNull { element ->
            val link = element.selectFirst("a.lc-release-name, a[href^='/manga/']:not(.lc-release-cover)")
                ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.text()
                thumbnail_url = element.selectFirst("a.lc-release-cover img, img")?.imgAttr()
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    // =============================== BÚSQUEDA ==============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment("")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query.trim())
        }

        filters.firstInstanceOrNull<GenreFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addQueryParameter("genre", it.toUriPart())
        }
        filters.firstInstanceOrNull<StatusFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addQueryParameter("status", it.toUriPart().lowercase())
        }
        filters.firstInstanceOrNull<AlphabeticFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addQueryParameter("initial", it.toUriPart())
        }

        if (page > 1) {
            urlBuilder.addQueryParameter("page", page.toString())
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.lc-card").mapNotNull { element ->
            val link = element.selectFirst("a.lc-card-name, a[href^='/manga/']:not(.lc-card-cover)")
                ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.attr("abs:href"))
                title = link.text()
                thumbnail_url = element.selectFirst("a.lc-card-cover img, img")?.imgAttr()
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("ul.pagination > li.active + li:not(.disabled), a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        AlphabeticFilter(),
        StatusFilter(),
    )

    // =========================== DETALLES DEL MANGA ========================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.selectFirst("ul.lc-facts > li:has(span.k:contains(Autor)) > span:not(.k)")?.text()
            artist = document.selectFirst("ul.lc-facts > li:has(span.k:contains(Dibujo)) > span:not(.k)")?.text()

            val altNames = document.selectFirst("p.lc-muted")?.text()
            val desc = document.selectFirst("#example2, .lc-synopsis, section:has(h2:contains(Sinopsis)) p")?.text()
            description = buildString {
                if (!desc.isNullOrEmpty()) append(desc)
                if (!altNames.isNullOrEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Títulos alternativos: ")
                    append(altNames)
                }
            }

            genre = document.select("a[href*='/manga/?genre='], a[href*='/manga/?theme=']")
                .map { it.text() }
                .distinct()
                .joinToString()

            status = document.selectFirst("ul.lc-facts > li:has(span.k:contains(Estado)) a")?.text()?.toStatus()
                ?: SManga.UNKNOWN

            thumbnail_url = document.selectFirst(".lc-cover-lg > img")?.imgAttr()
        }
    }

    // ============================== CAPÍTULOS ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

        return document.select("#chapterList > a.lc-chapter-row").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                name = element.selectFirst("span.n")?.text() ?: element.text()
                val dateStr = element.selectFirst("span.d")?.text()
                date_upload = if (dateStr != null) {
                    runCatching { dateFormat.parse(dateStr)?.time }.getOrNull() ?: 0L
                } else {
                    0L
                }
            }
        }
    }

    // =============================== PÁGINAS ===============================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#lcPages > img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun String.toStatus() = when (this.lowercase()) {
        "ongoing", "en emisión", "activo" -> SManga.ONGOING
        "paused", "pausado" -> SManga.ON_HIATUS
        "completed", "completado", "finalizado" -> SManga.COMPLETED
        "cancelled", "cancelado" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}
