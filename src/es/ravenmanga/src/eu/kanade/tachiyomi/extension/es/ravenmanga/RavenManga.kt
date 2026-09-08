package eu.kanade.tachiyomi.extension.es.ravenmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.Calendar

@Source
abstract class RavenManga : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.host == baseUrl.toHttpUrl().host }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
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

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("section.flex > div.grid > figure").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                title = element.selectFirst("figcaption")?.text().orEmpty()
                element.selectFirst("a")?.attr("href")?.let { setUrlWithoutDomain(it) }
            }
        }

        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val isSearch = query.isNotEmpty()

        if (isSearch && query.length < 2) throw Exception("La búsqueda debe tener al menos 2 caracteres")
        val url = if (isSearch) "$baseUrl/comics" else "$baseUrl/comics?page=$page"

        val document = client.get(url).asJsoup()

        if (isSearch) {
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

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            throw Exception("URL no soportada")
        }

        if (url.pathSegments.size != 2 || url.pathSegments[0] != "sr2" || url.pathSegments[1].isBlank()) {
            throw Exception("URL no soportada")
        }

        val document = client.get(url).asJsoup()
        return parseMangaDetails(document).apply { setUrlWithoutDomain(url.toString()) }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        val mainElement = document.selectFirst("main.wrap-project")
        if (mainElement != null) {
            title = mainElement.attr("data-project")
        }

        thumbnail_url = document.selectFirst("#coverProject")?.attr("src")

        val container = document.selectFirst("section#section-sinopsis")
        if (container != null) {
            description = container.select("p").text()
            genre = container.select("div.flex:has(div:containsOwn(Géneros)) > div > a > span")
                .joinToString { it.text() }
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("section#section-list-cap div.grid > a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.selectFirst("div#name")?.text().orEmpty()
            date_upload = element.selectFirst("time")?.text()?.let { parseRelativeDate(it) } ?: 0L
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var document = client.get(baseUrl + chapter.url).asJsoup()
        val form = document.selectFirst("form#redirectForm[method=post]")
        if (form != null) {
            val url = form.absUrl("action")
            val headers = headersBuilder().set("Referer", document.location()).build()
            val body = FormBody.Builder()
            form.select("input").forEach {
                body.add(it.attr("name"), it.attr("value"))
            }
            document = client.post(url, headers, body.build()).asJsoup()
        }
        return document.select("main.contenedor-imagen > section img[src], main > img[src]").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
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
        private val JSON_PROJECT_LIST = """proyectos\s*=\s*(\[[\s\S]+?])\s*;""".toRegex()
        private val NUMBER_REGEX = """(\d+)""".toRegex()
    }
}
