package eu.kanade.tachiyomi.extension.es.brakeout

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.lang.IllegalArgumentException
import java.util.Calendar

class Brakeout : ParsedHttpSource() {

    override val name = "Brakeout"

    override val baseUrl = "https://brakeout.xyz"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div#div-diario figure, div#div-semanal figure, div#div-mensual figure"

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaParse(response: Response): MangasPage {
        val mangasPage = super.popularMangaParse(response)
        val distinctList = mangasPage.mangas.distinctBy { it.url }

        return MangasPage(distinctList, mangasPage.hasNextPage)
    }

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
        title = element.selectFirst("figcaption")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "section.flex > div.grid > figure"

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
        title = element.selectFirst("figcaption")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            if (query.length > 1) return GET("$baseUrl/comics#$query", headers)
            throw Exception("La búsqueda debe tener al menos 2 caracteres")
        }
        return GET("$baseUrl/comics?page=$page", headers)
    }

    override fun searchMangaSelector(): String = "section.flex > div.grid > figure"

    override fun searchMangaNextPageSelector(): String = "main.container section.flex > div > a:containsOwn(Siguiente)"

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment ?: return super.searchMangaParse(response)
        val document = response.asJsoup()
        val mangas = parseMangaList(document, query)
        return MangasPage(mangas, false)
    }

    private fun parseMangaList(document: Document, query: String): List<SManga> {
        val docString = document.toString()
        val mangaListJson = JSON_PROJECT_LIST.find(docString)?.destructured?.toList()?.get(0).orEmpty()

        return try {
            json.decodeFromString<List<SerieDto>>(mangaListJson)
                .filter { it.title.contains(query, ignoreCase = true) }
                .map {
                    SManga.create().apply {
                        title = it.title
                        thumbnail_url = it.thumbnail
                        url = "/ver/${it.id}/${it.slug}"
                    }
                }
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
        title = element.selectFirst("figcaption")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        with(document.select("section#section-sinopsis")) {
            description = select("p").text()
            genre = select("div.flex:has(div:containsOwn(Géneros)) > div > a > span").joinToString { it.text() }
        }
    }

    override fun chapterListSelector(): String = "section#section-list-cap div.grid-capitulos > div > a.group"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("div#name")!!.text()
        date_upload = parseRelativeDate(element.selectFirst("time")!!.text())
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("section > div > img.readImg").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used!")

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Limpie la barra de búsqueda y haga click en 'Filtrar' para mostrar todas las series."),
        )
    }

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("segundo").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("minuto").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("hora").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("día", "dia").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("semana").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            WordSet("mes").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WordSet("año").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    class WordSet(private vararg val words: String) {
        fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
    }

    @Serializable
    data class SerieDto(
        val id: Int,
        @SerialName("nombre") val title: String,
        val slug: String,
        @SerialName("portada") val thumbnail: String,
    )

    companion object {
        private val JSON_PROJECT_LIST = """proyectos\s*=\s*(\[[\s\S]+?\])\s*;""".toRegex()
    }
}
