package eu.kanade.tachiyomi.extension.es.kumanga

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class Kumanga : HttpSource() {

    override val name = "Kumanga"

    override val baseUrl = "https://www.kumanga.com"

    override val lang = "es"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient
        .newBuilder()
        .followRedirects(true)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            if (originalRequest.url.toString().endsWith("token=")) {
                getKumangaToken()
                val url = originalRequest.url.toString() + kumangaToken
                val newRequest = originalRequest.newBuilder().url(url).build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(originalRequest)
            }
        }
        .build()

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    private var kumangaToken = ""

    private fun encodeAndReverse(dtValue: String): String {
        return Base64.encodeToString(dtValue.toByteArray(), Base64.DEFAULT).reversed().trim()
    }

    private fun decodeBase64(encodedString: String): String {
        return Base64.decode(encodedString, Base64.DEFAULT).toString(charset("UTF-8"))
    }

    private fun getKumangaToken(): String {
        val body = client.newCall(GET("$baseUrl/mangalist?&page=1", headers)).execute().asJsoup()
        val dt = body.select("#searchinput").attr("dt").toString()
        val kumangaTokenKey = encodeAndReverse(encodeAndReverse(dt))
            .replace("=", "k")
            .lowercase(Locale.ROOT)
        kumangaToken = body.select("div.input-group [type=hidden]").attr(kumangaTokenKey)
        return kumangaToken
    }

    private fun getMangaCover(mangaId: String) = "$baseUrl/kumathumb.php?src=$mangaId"

    private fun getMangaUrl(mangaId: String, mangaSlug: String, page: Int) = "/manga/$mangaId/p/$page/$mangaSlug#cl"

    private fun parseMangaFromJson(jsonObj: JsonObject) = SManga.create().apply {
        title = jsonObj["name"]!!.jsonPrimitive.content
        description = jsonObj["description"]!!.jsonPrimitive.content.replace("\\", "")
        url = getMangaUrl(jsonObj["id"]!!.jsonPrimitive.content, jsonObj["slug"]!!.jsonPrimitive.content, 1)
        thumbnail_url = getMangaCover(jsonObj["id"]!!.jsonPrimitive.content)
        genre = jsonObj["categories"]!!.jsonArray
            .joinToString { it.jsonObject["name"]!!.jsonPrimitive.content }
    }

    override fun popularMangaRequest(page: Int): Request {
        getKumangaToken() // Get new token every request (prevent http 400)
        return POST("$baseUrl/backend/ajax/searchengine.php?page=$page&perPage=10&keywords=&retrieveCategories=true&retrieveAuthors=false&contentType=manga&token=$kumangaToken", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonResult = json.parseToJsonElement(response.body.string()).jsonObject

        val mangaList = jsonResult["contents"]!!.jsonArray
            .map { jsonEl -> parseMangaFromJson(jsonEl.jsonObject) }

        val hasNextPage = jsonResult["retrievedCount"]!!.jsonPrimitive.int == 10

        return MangasPage(mangaList, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw Exception("Not Used")

    override fun latestUpdatesParse(response: Response) = throw Exception("Not Used")

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val body = response.asJsoup()
        thumbnail_url = body.selectFirst("div.km-img-gral-2 img")?.attr("abs:src")
        body.select("div#tab2").let {
            status = parseStatus(it.select("span").text().orEmpty())
            author = it.select("p:nth-child(3) > a").text()
            artist = it.select("p:nth-child(4) > a").text()
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Activo") -> SManga.ONGOING
        status.contains("Finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseChapterDate(date: String): Long = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        .parse(date)?.time ?: 0L

    private fun chapterSelector() = "div#accordion .title"

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.select("a:has(i)").let {
            setUrlWithoutDomain(it.attr("abs:href").replace("/c/", "/leer/"))
            name = it.text()
            date_upload = parseChapterDate(it.attr("title"))
        }
        scanlator = element.select("span.pull-right.greenSpan").text()
    }

    override fun chapterListParse(response: Response): List<SChapter> = mutableListOf<SChapter>().apply {
        var document = response.asJsoup()
        val params = document.select("script:containsData(totCntnts)").toString()

        val numberChapters = params.substringAfter("totCntnts=").substringBefore(";").toIntOrNull()
        val mangaId = params.substringAfter("mid=").substringBefore(";")
        val mangaSlug = params.substringAfter("slg='").substringBefore("';")

        if (numberChapters != null) {
            // Calculating total of pages, Kumanga shows 10 chapters per page, total_pages = #chapters / 10
            val numberOfPages = (numberChapters / 10.toDouble() + 0.4).roundToInt()
            document.select(chapterSelector()).map { add(chapterFromElement(it)) }
            var page = 2

            while (page <= numberOfPages) {
                document = client.newCall(GET(baseUrl + getMangaUrl(mangaId, mangaSlug, page))).execute().asJsoup()
                document.select(chapterSelector()).map { add(chapterFromElement(it)) }
                page++
            }
        } else {
            throw Exception("No fue posible obtener los capítulos")
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val form = document.selectFirst("form#myForm[action]")
        if (form != null) {
            val url = form.attr("action")
            val bodyBuilder = FormBody.Builder()
            val inputs = form.select("input")
            inputs.map { input ->
                bodyBuilder.add(input.attr("name"), input.attr("value"))
            }
            return pageListParse(client.newCall(POST(url, headers, bodyBuilder.build())).execute())
        } else {
            val imagesJsonRaw = document.select("script:containsData(var pUrl=)").firstOrNull()
                ?.data()
                ?.substringAfter("var pUrl=")
                ?.substringBefore(";")
                ?.let { decodeBase64(decodeBase64(it).reversed().dropLast(10).drop(10)) }
                ?: throw Exception("imagesJsonListStr null")

            val jsonResult = json.parseToJsonElement(imagesJsonRaw).jsonArray

            return jsonResult.mapIndexed { i, jsonEl ->
                val jsonObj = jsonEl.jsonObject
                val imagePath = jsonObj["imgURL"]!!.jsonPrimitive.content.replace("\\", "")
                val docUrl = document.location()
                val baseUrl = URL(docUrl).protocol + "://" + URL(docUrl).host // For some reason baseUri returns the full url
                Page(i, baseUrl, "$baseUrl/$imagePath")
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = Headers.Builder()
            .add("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response) = throw Exception("Not Used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        getKumangaToken()
        val url = "$baseUrl/backend/ajax/searchengine.php?page=$page&perPage=10&keywords=$query&retrieveCategories=true&retrieveAuthors=false&contentType=manga&token=$kumangaToken".toHttpUrlOrNull()!!.newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is TypeList -> {
                    filter.state
                        .filter { type -> type.state }
                        .forEach { type -> url.addQueryParameter("type_filter[]", type.id) }
                }
                is StatusList -> {
                    filter.state
                        .filter { status -> status.state }
                        .forEach { status -> url.addQueryParameter("status_filter[]", status.id) }
                }
                is GenreList -> {
                    filter.state
                        .filter { genre -> genre.state }
                        .forEach { genre -> url.addQueryParameter("category_filter[]", genre.id) }
                }
                else -> {}
            }
        }

        return POST(url.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        TypeList(getTypeList()),
        Filter.Separator(),
        StatusList(getStatusList()),
        Filter.Separator(),
        GenreList(getGenreList()),
    )

    private class Type(name: String, val id: String) : Filter.CheckBox(name)
    private class TypeList(types: List<Type>) : Filter.Group<Type>("Filtrar por tipos", types)

    private class Status(name: String, val id: String) : Filter.CheckBox(name)
    private class StatusList(status: List<Status>) : Filter.Group<Status>("Filtrar por estado", status)

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Filtrar por géneros", genres)

    private fun getTypeList() = listOf(
        Type("Manga", "1"),
        Type("Manhwa", "2"),
        Type("Manhua", "3"),
        Type("One shot", "4"),
        Type("Doujinshi", "5"),
    )

    private fun getStatusList() = listOf(
        Status("Activo", "1"),
        Status("Finalizado", "2"),
        Status("Inconcluso", "3"),
    )

    private fun getGenreList() = listOf(
        Genre("Acción", "1"),
        Genre("Artes marciales", "2"),
        Genre("Automóviles", "3"),
        Genre("Aventura", "4"),
        Genre("Ciencia Ficción", "5"),
        Genre("Comedia", "6"),
        Genre("Demonios", "7"),
        Genre("Deportes", "8"),
        Genre("Doujinshi", "9"),
        Genre("Drama", "10"),
        Genre("Ecchi", "11"),
        Genre("Espacio exterior", "12"),
        Genre("Fantasía", "13"),
        Genre("Gender bender", "14"),
        Genre("Gore", "46"),
        Genre("Harem", "15"),
        Genre("Hentai", "16"),
        Genre("Histórico", "17"),
        Genre("Horror", "18"),
        Genre("Josei", "19"),
        Genre("Juegos", "20"),
        Genre("Locura", "21"),
        Genre("Magia", "22"),
        Genre("Mecha", "23"),
        Genre("Militar", "24"),
        Genre("Misterio", "25"),
        Genre("Música", "26"),
        Genre("Niños", "27"),
        Genre("Parodia", "28"),
        Genre("Policía", "29"),
        Genre("Psicológico", "30"),
        Genre("Recuentos de la vida", "31"),
        Genre("Romance", "32"),
        Genre("Samurai", "33"),
        Genre("Seinen", "34"),
        Genre("Shoujo", "35"),
        Genre("Shoujo Ai", "36"),
        Genre("Shounen", "37"),
        Genre("Shounen Ai", "38"),
        Genre("Sobrenatural", "39"),
        Genre("Súperpoderes", "41"),
        Genre("Suspenso", "40"),
        Genre("Terror", "47"),
        Genre("Tragedia", "48"),
        Genre("Vampiros", "42"),
        Genre("Vida escolar", "43"),
        Genre("Yaoi", "44"),
        Genre("Yuri", "45"),
    )
}
