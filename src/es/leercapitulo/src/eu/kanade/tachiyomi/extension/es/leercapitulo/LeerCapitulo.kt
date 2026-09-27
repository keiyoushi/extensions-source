package eu.kanade.tachiyomi.extension.es.leercapitulo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
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

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "az")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "za")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/manga/".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query)
        }

        filters.firstInstanceOrNull<GenreFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addQueryParameter("genre", it.toUriPart())
        }
        filters.firstInstanceOrNull<StatusFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addQueryParameter("status", it.toUriPart())
        }

        urlBuilder.addQueryParameter("page", page.toString())
        return GET(urlBuilder.build(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isBlank()) {
            return client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it) }
        }

        val autocompleteUrl = "$baseUrl/search-autocomplete?term=$query"
        return client.newCall(GET(autocompleteUrl, headers)).asObservableSuccess()
            .map { response ->
                val mangas = runCatching {
                    response.parseAs<List<Dto>>().map { it.toSManga() }
                }.getOrNull() ?: emptyList()

                if (mangas.isEmpty()) throw Exception("Empty autocomplete")
                MangasPage(mangas, false)
            }
            .onErrorResumeNext {
                client.newCall(searchMangaRequest(page, query, filters))
                    .asObservableSuccess()
                    .map { searchMangaParse(it) }
            }
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.lc-card").mapNotNull { card ->
            val link = card.selectFirst("a.lc-card-name") ?: card.selectFirst("a.lc-card-cover") ?: return@mapNotNull null
            val url = link.attr("abs:href")
            if (url.isBlank()) return@mapNotNull null

            val titleText = card.selectFirst("a.lc-card-name")?.text() ?: link.text()
            if (titleText.isBlank()) return@mapNotNull null

            val img = card.selectFirst("a.lc-card-cover img")

            SManga.create().apply {
                setUrlWithoutDomain(url)
                title = titleText.trim()
                thumbnail_url = img?.imgAttr()
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("ul.pagination li.active + li:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("article h1, h1")?.text()?.trim() ?: ""

            val altNames = document.selectFirst("article p.lc-muted")?.text()?.trim()
            val desc = document.selectFirst("#sinopsis p, #sinopsis")?.text()?.trim()
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterRows = document.select("#chapterList a.lc-chapter-row")

        return chapterRows.mapNotNull { element ->
            val url = element.attr("abs:href")
            if (url.isBlank()) return@mapNotNull null

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

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // Extracción directa de las imágenes en el nuevo visor (#lcPages o .lc-pages)
        val imageElements = document.select("#lcPages img, main.lc-pages img, .lc-pages img")
        val pages = imageElements.mapNotNull { element ->
            val src = element.imgAttr()
            if (src.startsWith("http")) src else null
        }

        if (pages.isNotEmpty()) {
            return pages.mapIndexed { i, imageUrl ->
                Page(i, imageUrl = imageUrl)
            }
        }

        // Fallback genérico por si cambian la clase contenedora
        val fallbackImages = document.select("main img, #chapter-content img, .chapter-content img")
            .mapNotNull { it.imgAttr().takeIf { src -> src.startsWith("http") } }

        if (fallbackImages.isNotEmpty()) {
            return fallbackImages.mapIndexed { i, imageUrl ->
                Page(i, imageUrl = imageUrl)
            }
        }

        throw Exception("No se encontraron páginas en este capítulo")
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("src") -> attr("abs:src")
        else -> ""
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Dto.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(link)
        title = label
        thumbnail_url = if (thumbnail.startsWith("http")) thumbnail else baseUrl + thumbnail
    }

    private fun String.toStatus() = when (this.lowercase().trim()) {
        "ongoing", "en emision" -> SManga.ONGOING
        "completed", "finalizado" -> SManga.COMPLETED
        "paused", "pausado" -> SManga.ON_HIATUS
        "cancelled", "cancelado" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        StatusFilter(),
    )
}
