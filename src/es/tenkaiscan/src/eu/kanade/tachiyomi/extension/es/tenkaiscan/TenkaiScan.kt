package eu.kanade.tachiyomi.extension.es.tenkaiscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale

class TenkaiScan : ParsedHttpSource() {

    // Site change theme from Madara to custom theme
    override val versionId = 3

    override val name = "TenkaiScan"

    override val baseUrl = "https://tenkaiscan.net"

    override val lang = "es"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es"))

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking", headers)

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaSelector(): String = "section.trending div.row div.card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(
            element.attr("onclick")
                .substringAfter("window.location.href='")
                .substringBefore("'"),
        )
        title = element.selectFirst("div.name > h4.color-white")!!.text()
        thumbnail_url = element.select("img").imgAttr()
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesSelector(): String = "section.trending div.row a"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("div.content > h4.color-white")!!.text()
        thumbnail_url = element.select("img").imgAttr()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/comics".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("search", query)
            return GET(urlBuilder.build(), headers)
        } else {
            for (filter in filters) {
                when (filter) {
                    is AlphabeticFilter -> {
                        if (filter.state != 0) {
                            urlBuilder.addQueryParameter("filter", filter.toUriPart())
                            break
                        }
                    }
                    is GenreFilter -> {
                        if (filter.state != 0) {
                            urlBuilder.addQueryParameter("gen", filter.toUriPart())
                            break
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state != 0) {
                            urlBuilder.addQueryParameter("status", filter.toUriPart())
                            break
                        }
                    }
                    else -> {}
                }
            }
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaSelector(): String = "section.trending div.row > div.col-xxl-9 > div.row > div > a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("div.content > h4.color-white")!!.text()
        thumbnail_url = element.select("img").imgAttr()
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTA: Los filtros serán ignorados si se realiza una búsqueda por texto."),
        Filter.Header("Solo se puede aplicar un filtro a la vez."),
        AlphabeticFilter(),
        GenreFilter(),
        StatusFilter(),
    )

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        document.selectFirst("div.page-content div.text-details")!!.let { element ->
            title = element.selectFirst("div.name-rating")!!.text()
            description = element.select("p.sec:not(div.soft-details p)").text()
            thumbnail_url = element.selectFirst("img.img-details")!!.imgAttr()
            element.selectFirst("div.soft-details")?.let { details ->
                author = details.selectFirst("p:has(span:contains(Autor))")!!.ownText()
                artist = details.selectFirst("p:has(span:contains(Artista))")!!.ownText()
                status = details.selectFirst("p:has(span:contains(Status))")!!.ownText().parseStatus()
                genre = details.selectFirst("p:has(span:contains(Generos))")!!.ownText()
            }
        }
    }

    override fun chapterListSelector(): String = "div.page-content div.card-caps"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(
            element.attr("onclick")
                .substringAfter("window.location.href='")
                .substringBefore("'"),
        )
        name = element.selectFirst("div.text-cap span.color-white")!!.text()
        date_upload = try {
            element.selectFirst("div.text-cap span.color-medium-gray")?.text()?.let {
                dateFormat.parse(it)?.time ?: 0
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-content div.img-blade img").mapIndexed { i, element ->
            Page(i, imageUrl = element.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private class AlphabeticFilter : UriPartFilter(
        "Ordenar por",
        arrayOf(
            Pair("<Seleccionar>", ""),
            Pair("A", "a"),
            Pair("B", "b"),
            Pair("C", "c"),
            Pair("D", "d"),
            Pair("E", "e"),
            Pair("F", "f"),
            Pair("G", "g"),
            Pair("H", "h"),
            Pair("I", "i"),
            Pair("J", "j"),
            Pair("K", "k"),
            Pair("L", "l"),
            Pair("M", "m"),
            Pair("N", "n"),
            Pair("O", "o"),
            Pair("P", "p"),
            Pair("Q", "q"),
            Pair("R", "r"),
            Pair("S", "s"),
            Pair("T", "t"),
            Pair("U", "u"),
            Pair("V", "v"),
            Pair("W", "w"),
            Pair("X", "x"),
            Pair("Y", "y"),
            Pair("Z", "z"),
        ),
    )

    private class GenreFilter : UriPartFilter(
        "Género",
        arrayOf(
            Pair("<Seleccionar>", ""),
            Pair("Adaptación de Novela", "Adaptación de Novela"),
            Pair("Aventuras", "Aventuras"),
            Pair("Bondage", "Bondage"),
            Pair("Comedia", "Comedia"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Escolar", "Escolar"),
            Pair("Fantasía", "Fantasía"),
            Pair("Hardcore", "Hardcore"),
            Pair("Harem", "Harem"),
            Pair("Isekai", "Isekai"),
            Pair("MILF", "MILF"),
            Pair("Netorare", "Netorare"),
            Pair("Novela", "Novela"),
            Pair("Recuentos de la vida", "Recuentos de la vida"),
            Pair("Romance", "Romance"),
            Pair("Seinen", "Seinen"),
            Pair("Sistemas", "Sistemas"),
            Pair("Venganza", "Venganza"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("Completed", "Completed"),
            Pair("En Libertad", "En Libertad"),
            Pair("Canceled", "Canceled"),
        ),
    )

    private fun Elements.imgAttr(): String = this.first()!!.imgAttr()

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

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
