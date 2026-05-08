package eu.kanade.tachiyomi.extension.es.ravenmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.util.Calendar

class RavenManga : HttpSource() {

    override val name = "RavenManga"

    override val baseUrl = "https://raventard.xyz"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div#div-diario figure, div#div-semanal figure, div#div-mensual figure")
            .map { element ->
                SManga.create().apply {
                    thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    title = element.selectFirst("figcaption")?.text().orEmpty()
                    element.selectFirst("a")?.attr("href")?.let { setUrlWithoutDomain(it) }
                }
            }
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.flex > div.grid > figure").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                title = element.selectFirst("figcaption")?.text().orEmpty()
                element.selectFirst("a")?.attr("href")?.let { setUrlWithoutDomain(it) }
            }
        }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            if (query.length > 1) return GET("$baseUrl/comics#$query", headers)
            throw Exception("La búsqueda debe tener al menos 2 caracteres")
        }
        return GET("$baseUrl/comics?page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment
        val document = response.asJsoup()

        if (query != null) {
            val mangas = parseMangaList(document, query)
            return MangasPage(mangas, false)
        }

        val mangas = document.select("section.flex > div.grid > figure").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                title = element.selectFirst("figcaption")?.text().orEmpty()
                element.selectFirst("a")?.attr("href")?.let { setUrlWithoutDomain(it) }
            }
        }
        val hasNextPage = document.selectFirst("nav > ul.pagination > li > a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun parseMangaList(document: Document, query: String): List<SManga> {
        val docString = document.toString()
        val mangaListJson = JSON_PROJECT_LIST.find(docString)?.groupValues?.get(1).orEmpty()

        return try {
            mangaListJson.parseAs<List<Dto>>()
                .filter { it.title.contains(query, ignoreCase = true) }
                .map { it.toSManga() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val container = document.selectFirst("section#section-sinopsis")
            if (container != null) {
                description = container.select("p").text()
                genre = container.select("div.flex:has(div:containsOwn(Géneros)) > div > a > span")
                    .joinToString { it.text() }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("section#section-list-cap div.grid > a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.selectFirst("div#name")?.text().orEmpty()
                date_upload = element.selectFirst("time")?.text()?.let { parseRelativeDate(it) } ?: 0L
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        var doc = response.asJsoup()
        val form = doc.selectFirst("form#redirectForm[method=post]")
        if (form != null) {
            val url = form.absUrl("action")
            val headers = headersBuilder().set("Referer", doc.location()).build()
            val body = FormBody.Builder()
            form.select("input").forEach {
                body.add(it.attr("name"), it.attr("value"))
            }
            doc = client.newCall(POST(url, headers, body.build())).execute().asJsoup()
        }
        return doc.select("main.contenedor-imagen > section img[src], main > img[src]").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Limpie la barra de búsqueda y haga click en 'Filtrar' para mostrar todas las series."),
    )

    private fun parseRelativeDate(date: String): Long {
        val number = NUMBER_REGEX.find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.containsWord("segundo") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            date.containsWord("minuto") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.containsWord("hora") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.containsWord("día", "dia") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.containsWord("semana") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            date.containsWord("mes") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.containsWord("año") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    private fun String.containsWord(vararg words: String): Boolean = words.any { this.contains(it, ignoreCase = true) }

    companion object {
        private val JSON_PROJECT_LIST = """proyectos\s*=\s*(\[[\s\S]+?\])\s*;""".toRegex()
        private val NUMBER_REGEX = """(\d+)""".toRegex()
    }
}
