package eu.kanade.tachiyomi.extension.es.tenkaiscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class FalcoScan : HttpSource() {

    // Site change theme from Madara to custom theme
    override val versionId = 3

    override val name = "Falco Scan"

    override val baseUrl = "https://falcoscan.net"

    override val lang = "es"

    override val supportsLatest = true

    override val id = 5992780069311625546

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es"))

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.trending div.row div.card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(
                    element.attr("onclick")
                        .substringAfter("window.location.href='")
                        .substringBefore("'"),
                )
                title = element.selectFirst("div.name > h4.color-white")!!.text()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.trending div.row a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst("div.content > h4.color-white")!!.text()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/comics".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("search", query)
        } else {
            filters.firstInstanceOrNull<AlphabeticFilter>()?.let {
                if (it.state != 0) urlBuilder.addQueryParameter("filter", it.toUriPart())
            }
            filters.firstInstanceOrNull<GenreFilter>()?.let {
                if (it.state != 0) urlBuilder.addQueryParameter("gen", it.toUriPart())
            }
            filters.firstInstanceOrNull<StatusFilter>()?.let {
                if (it.state != 0) urlBuilder.addQueryParameter("status", it.toUriPart())
            }
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.trending div.row > div.col-xxl-9 > div.row > div > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst("div.content > h4.color-white")!!.text()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        return MangasPage(mangas, false)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTA: Los filtros serán ignorados si se realiza una búsqueda por texto."),
        Filter.Header("Solo se puede aplicar un filtro a la vez."),
        AlphabeticFilter(),
        GenreFilter(),
        StatusFilter(),
    )

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        document.selectFirst("div.page-content div.text-details")?.let { element ->
            title = element.selectFirst("div.name-rating")?.text() ?: ""
            description = element.select("p.sec:not(div.soft-details p)").text()
            thumbnail_url = element.selectFirst("img.img-details")?.imgAttr()
            element.selectFirst("div.soft-details")?.let { details ->
                author = details.selectFirst("p:has(span:contains(Autor))")?.ownText()
                artist = details.selectFirst("p:has(span:contains(Artista))")?.ownText()
                status = details.selectFirst("p:has(span:contains(Status))")?.ownText()?.parseStatus() ?: SManga.UNKNOWN
                genre = details.selectFirst("p:has(span:contains(Generos))")?.ownText()
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.page-content div.card-caps").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(
                    element.attr("onclick")
                        .substringAfter("window.location.href='")
                        .substringBefore("'"),
                )
                name = element.selectFirst("div.text-cap span.color-white")!!.text()
                date_upload = dateFormat.tryParse(element.selectFirst("div.text-cap span.color-medium-gray")?.text())
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.page-content div.img-blade img").mapIndexed { i, element ->
            Page(i, imageUrl = element.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String = when {
        this.hasAttr("data-src") -> this.absUrl("data-src")
        else -> this.absUrl("src")
    }

    private fun String.parseStatus() = when (this.lowercase()) {
        "en emisión" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        "cancelado" -> SManga.CANCELLED
        "en espera" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}
