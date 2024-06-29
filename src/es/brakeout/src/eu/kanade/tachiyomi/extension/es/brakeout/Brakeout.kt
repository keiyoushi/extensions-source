package eu.kanade.tachiyomi.extension.es.brakeout

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.Calendar

class Brakeout : ParsedHttpSource() {

    override val name = "Brakeout"

    override val baseUrl = "https://brakeout.xyz"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/top", headers)

    override fun popularMangaSelector(): String = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<TopSeriesPayloadDto>(response.body.string())
        val series = result.data.map { it.project.toSManga() }
        return MangasPage(series, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "body > main > div:eq(0) div.grid > div.flex"

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("a > img")!!.attr("abs:src")
        title = element.selectFirst("a > h1")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    var mangaList = listOf<SeriesDto>()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.isEmpty()) return super.fetchSearchManga(page, query, filters)
        if (mangaList.isEmpty()) {
            val request = searchMangaRequest(page, query, filters)
            return client.newCall(request).asObservableSuccess().map { response ->
                searchMangaParse(response, query)
            }
        } else {
            return Observable.just(parseMangaList(query))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            if (query.length > 1) return GET("$baseUrl/comics", headers)
            throw Exception("La búsqueda debe tener al menos 2 caracteres")
        }
        return GET("$baseUrl/comics?page=$page", headers)
    }

    override fun searchMangaSelector(): String = "section.flex > div.grid > figure"

    override fun searchMangaNextPageSelector(): String = "main.container section.flex > div > a:containsOwn(Siguiente)"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
        title = element.selectFirst("figcaption")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val docString = response.body.string()
        val jsonString = JSON_PROJECT_LIST.find(docString)?.destructured?.toList()?.get(0).orEmpty()
        mangaList = json.decodeFromString<List<SeriesDto>>(jsonString)
        return parseMangaList(query)
    }

    private fun parseMangaList(query: String): MangasPage {
        val mangas = mangaList.filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
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

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

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

    companion object {
        private val JSON_PROJECT_LIST = """proyectos\s*=\s*(\[[\s\S]+?\])\s*;""".toRegex()
    }
}
