package eu.kanade.tachiyomi.extension.es.lectorjpg

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LectorJpg : HttpSource() {

    override val versionId = 3

    override val name = "LectorJPG"

    override val lang = "es"

    override val baseUrl = "https://lectorjpg.com"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .build()

    class LimitedCache<K, V>() : LinkedHashMap<K, V>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
            return size > 8
        }
    }

    data class SearchKey(val page: Int, val query: String, val filters: String?)

    private val latestMangaCursor = LimitedCache<Int, String?>()
    private val searchMangaCursor = LimitedCache<SearchKey, String?>()

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.relative div.flex.w-fit article").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                url = element.selectFirst("a")!!.attr("href").substringAfterLast("/series/").removeSuffix("/")
                thumbnail_url = element.selectFirst("div.bg-cover")?.imageFromStyle()
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val cursor = latestMangaCursor[page - 1] ?: createLatestCursor()
        val url = "$baseUrl/serie-query".toHttpUrl().newBuilder()
            .addQueryParameter("cursor", cursor)
            .addQueryParameter("perPage", "35")
            .addQueryParameter("type", "updated")
            .fragment(page.toString())

        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val page = response.request.url.fragment!!.toInt()
        val result = response.parseAs<SeriesQueryDto>()
        latestMangaCursor[page] = result.nextCursor
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genresParam = filters
            .filterIsInstance<GenreFilter>()
            .flatMap { filter -> filter.state.filter { it.state }.map { it.key } }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(",")

        val searchKey = SearchKey(page - 1, query, genresParam)

        val cursor = searchMangaCursor[searchKey] ?: ""
        val url = "$baseUrl/serie-query".toHttpUrl().newBuilder()
            .addQueryParameter("cursor", cursor)
            .addQueryParameter("perPage", "35")
            .addQueryParameter("type", "query")
            .addQueryParameter("name", query)
            .fragment(page.toString())

        if (genresParam != null) {
            url.addQueryParameter("genres", genresParam)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.url.fragment!!.toInt()
        val query = response.request.url.queryParameter("name") ?: ""
        val genresParam = response.request.url.queryParameter("genres")

        val searchKey = SearchKey(page, query, genresParam)

        val result = response.parseAs<SeriesQueryDto>()
        searchMangaCursor[searchKey] = result.nextCursor
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            GenreFilter("Géneros", getGenreList()),
        )
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("div.grid > h1")!!.text()
            thumbnail_url = document.selectFirst("div.bg_main.bg-cover")?.imageFromStyle()
            description = document.select("div.grid > div.container > p").text()
            status = document.selectFirst("div.grid:has(>div.flex:has(>span:contains(Status))) > div:last-child").parseStatus()
            genre = document.select("a[href*=/series?genres] > span").joinToString { it.text() }
        }
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.grid > a.group").map { element ->
            SChapter.create().apply {
                name = element.selectFirst("span.truncate")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                date_upload = element.selectFirst("span.w-fit")?.text()?.let { parseChapterDate(it) } ?: 0L
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.grid > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private val cursorDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun createLatestCursor(): String {
        val now: String? = cursorDateFormat.format(Date())
        val json = """{"last_update_at":"$now","id":0,"_pointsToNextItems":true}"""
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun Element?.parseStatus(): Int {
        return when (this?.text()?.lowercase()) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Element.imageFromStyle(): String? {
        val style = this.attr("style").replace("&quot;", "\"")
        return style.substringAfterLast("url(").substringBefore(")").removeSurrounding("\"")
    }

    private val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es"))

    private fun parseChapterDate(date: String): Long {
        if (date.contains("hace")) {
            val cleanDate = date.substringAfter("hace").trim()
            when {
                "hora" in cleanDate -> {
                    val hours = cleanDate.substringBefore("hora").trim().toIntOrNull() ?: return 0L
                    return System.currentTimeMillis() - hours * 60 * 60 * 1000
                }

                "minuto" in cleanDate -> {
                    val minutes = cleanDate.substringBefore("minuto").trim().toIntOrNull() ?: return 0L
                    return System.currentTimeMillis() - minutes * 60 * 1000
                }

                "segundo" in cleanDate -> {
                    val seconds = cleanDate.substringBefore("segundo").trim().toIntOrNull() ?: return 0L
                    return System.currentTimeMillis() - seconds * 1000
                }

                "día" in cleanDate -> {
                    val days = cleanDate.substringBefore("día").trim().toIntOrNull() ?: return 0L
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, -days)
                    return calendar.timeInMillis
                }

                else -> {
                    return 0L
                }
            }
        }

        if (date.equals("ayer", true)) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            return calendar.timeInMillis
        }

        return chapterDateFormat.tryParse(date)
    }

    private fun getGenreList() = listOf(
        Genre("BDSM", "bdsm"),
        Genre("Bebes", "bebes"),
        Genre("Bestias", "bestias"),
        Genre("BL Sin Censura", "bl-sin-censura"),
        Genre("Boys Love", "boys-love"),
        Genre("Che Tenete Un Poco De Amor Propio", "che-tenete-un-poco-de-amor-propio"),
        Genre("Ciencia Ficción", "ciencia-ficcion"),
        Genre("Comedia", "comedia"),
        Genre("Crimen", "crimen"),
        Genre("Del Campo", "del-campo"),
        Genre("Demonios", "demonios"),
        Genre("Deportes", "deportes"),
        Genre("Drama", "drama"),
        Genre("Escolar", "escolar"),
        Genre("Espacial", "espacial"),
        Genre("Fantasía", "fantasia"),
        Genre("Furro", "furro"),
        Genre("Harem", "harem"),
        Genre("Harem Inverso", "harem-inverso"),
        Genre("Historia", "historia"),
        Genre("Josei", "josei"),
        Genre("Juego", "juego"),
        Genre("Mafia", "mafia"),
        Genre("Magia", "magia"),
        Genre("Manhwa +19", "manhwa-19"),
        Genre("Militar", "militar"),
        Genre("Moderno", "moderno"),
        Genre("Morocho Hermoso", "morocho-hermoso"),
        Genre("Mucho Gogogo", "mucho-gogogo"),
        Genre("Música", "musica"),
        Genre("Novela", "novela"),
        Genre("Odio-Amor", "odio-amor"),
        Genre("Omegaverse", "omegaverse"),
        Genre("Psicológico", "psicologico"),
        Genre("Reencarnación", "reencarnacion"),
        Genre("Relación Por Convivencia", "relacion-por-convivencia"),
        Genre("Romance", "romance"),
        Genre("Smut", "smut"),
        Genre("Telenovela", "telenovela"),
        Genre("Tetón", "teton"),
        Genre("Toxicidad", "toxicidad"),
        Genre("Toxicidad Nivel Chernóbil", "toxicidad-nivel-chernobil"),
        Genre("Universitario", "universitario"),
        Genre("Venganza", "venganza"),
        Genre("Shoujo", "Shoujo"),
        Genre("Shounen", "Shounen"),
        Genre("Seinen", "Seinen"),
        Genre("+18 Sin Censura", "+ 18 Sin Censura"),
        Genre("NoBL\uD83D\uDC8C", "nobl"),
        Genre("Girls Love", "gl"),
        Genre("Adulto", "adulto"),
        Genre("+18", "18"),
        Genre("Sistema", "sistema"),
        Genre("PuchiLovers", "puchilovers"),
        Genre("Goheart Scan", "goheart-scan"),
        Genre("Acción", "Acción"),
        Genre("Aventura", "Aventura"),
        Genre("Sobrenatural", "Sobrenatural"),
        Genre("Transmigración", "Transmigración"),
    )
}
