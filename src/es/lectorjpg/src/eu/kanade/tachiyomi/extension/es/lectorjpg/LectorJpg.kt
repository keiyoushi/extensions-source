package eu.kanade.tachiyomi.extension.es.lectorjpg

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LectorJpg : KeiSource() {

    private val apiUrl = "https://api.visorjpg.lat"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds) {
        it.host == baseUrl.toHttpUrl().host
    }

    private var latestMangaCursor: String? = null
    private var searchMangaCursor: String? = null

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/home/trending").parseAs<SeriesQueryDto>()
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) latestMangaCursor = null
        val cursor = latestMangaCursor ?: createLatestCursor()
        val url = "$apiUrl/home/lastest-updates".toHttpUrl().newBuilder()
            .addQueryParameter("cursor", cursor)

        val response = client.get(url.build())

        val result = response.parseAs<SeriesQueryDto>()
        latestMangaCursor = result.nextCursor
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return mangaDetailsParse(client.get(url).asJsoup()).apply {
            this.url = slug
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (page == 1) searchMangaCursor = null

        val genresParam = filters
            .filterIsInstance<GenreFilter>()
            .flatMap { filter -> filter.state.filter { it.state }.map { it.key } }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(",")

        val cursor = searchMangaCursor ?: ""
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("cursor", cursor)
            .addQueryParameter("name", query)

        if (genresParam != null) {
            url.addQueryParameter("genres", genresParam)
        }

        return parseSearchManga(client.get(url.build()))
    }

    private fun parseSearchManga(response: Response): MangasPage {
        val result = response.parseAs<SeriesQueryDto>()
        searchMangaCursor = result.nextCursor
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(doc), chapterListParse(doc))
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.grid > h1")!!.text()
        thumbnail_url = document.selectFirst("div.bg_main.bg-cover")?.imageFromStyle()
        description = document.select("div.grid > div.container > p").text()
        status = document.selectFirst("div.grid:has(span:contains(Status)) > button").parseStatus()
        genre = document.select("a[href*=/series?genres] > span").joinToString { it.text() }
    }

    private fun chapterListParse(document: Document): List<SChapter> = document.select("div.grid > a.group").map { element ->
        SChapter.create().apply {
            name = element.selectFirst("span.truncate")!!.text()
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
            date_upload = element.selectFirst("span.w-fit")?.text()?.let { parseChapterDate(it) } ?: 0L
        }
    }

    private val pagesRegex = """images:(\[.*?])""".toRegex()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val scripts = document.select("script:containsData(svelteKit)").joinToString("\n") { it.data() }
        val match = pagesRegex.find(scripts) ?: return emptyList()
        val pagesJson = match.groupValues[1]
        val imageUrls = pagesJson.parseAs<List<String>>()
        return imageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        GenreFilter("Géneros", getGenreList()),
    )

    private val cursorDateFormat = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneOffset.UTC)

    private fun createLatestCursor(): String {
        val now: String? = cursorDateFormat.format(Instant.now())
        val json = """{"last_update_at":"$now","id":0,"_pointsToNextItems":true}"""
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun Element?.parseStatus(): Int = when (this?.text()) {
        "En emisión" -> SManga.ONGOING
        "Completado" -> SManga.COMPLETED
        "En pausa" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun Element.imageFromStyle(): String? {
        val style = this.attr("style").replace("&quot;", "\"")
        return style.substringAfterLast("url(").substringBefore(")").removeSurrounding("\"")
    }

    private val chapterDateFormat =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es"))

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

        return chapterDateFormat.tryParseDate(date)
    }
}
