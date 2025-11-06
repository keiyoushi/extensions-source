package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class LycanToons : ParsedHttpSource() {

    override val name = "Lycan Toons"
    override val baseUrl = "https://lycantoons.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    private val json by injectLazy<Json>()

    companion object {
        const val CDN_URL = "https://cdn.lycantoons.com"
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/api/metrics/popular?limit=20&page=$page", headers)

    override fun popularMangaSelector() = ""

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaParse(response: Response) = parseMangaPage(response)

    // ============================= Latest Updates ==========================
    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/api/metrics/recently-updated?limit=20&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response) = parseMangaPage(response)

    // =============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val seriesTypeFilter = filters.find { it is SeriesTypeFilter } as? SeriesTypeFilter
        val statusFilter = filters.find { it is StatusFilter } as? StatusFilter
        val tags = (filters.find { it is GenreList } as? GenreList)?.state?.filter { it.state }?.map { it.id }

        val jsonBody = buildString {
            append("{\"limit\":20,\"page\":$page")
            if (query.isNotEmpty()) {
                append(",\"search\":\"${query.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            }
            if (seriesTypeFilter != null && seriesTypeFilter.state > 0) {
                append(",\"seriesType\":\"${seriesTypeFilter.toUriPart()}\"")
            }
            if (statusFilter != null && statusFilter.state > 0) {
                append(",\"status\":\"${statusFilter.toUriPart()}\"")
            }
            if (!tags.isNullOrEmpty()) {
                append(",\"tags\":[${tags.joinToString(",") { "\"$it\"" }}]")
            }
            append("}")
        }

        return POST("$baseUrl/api/series", headers, jsonBody.toRequestBody("application/json".toMediaType()))
    }

    override fun searchMangaSelector() = ""

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector() = null

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonResponse = json.parseToJsonElement(response.body.string()).jsonObject
        val series = jsonResponse["series"]?.jsonArray ?: jsonResponse["data"]?.jsonArray ?: emptyList()
        val mangas = series.map { it.jsonObject.toSManga() }
        val hasNext = jsonResponse["pagination"]?.jsonObject?.get("hasNextPage")?.let {
            runCatching { it.jsonPrimitive.content.toBoolean() }.getOrDefault(false)
        } ?: false
        return MangasPage(mangas, hasNext)
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val jsonResponse = json.parseToJsonElement(response.body.string()).jsonObject
        val data = jsonResponse["data"]?.jsonArray ?: emptyList()
        val mangas = data.map { it.jsonObject.toSManga() }
        val hasNext = jsonResponse["pagination"]?.jsonObject?.get("hasNextPage")?.let {
            runCatching { it.jsonPrimitive.content.toBoolean() }.getOrDefault(false)
        } ?: false
        return MangasPage(mangas, hasNext)
    }

    // ============================== Filters ================================
    override fun getFilterList() = FilterList(
        SeriesTypeFilter(),
        StatusFilter(),
        GenreList(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("Ação", "action"),
        Genre("Aventura", "adventure"),
        Genre("Comédia", "comedy"),
        Genre("Drama", "drama"),
        Genre("Fantasia", "fantasy"),
        Genre("Terror", "horror"),
        Genre("Mistério", "mystery"),
        Genre("Romance", "romance"),
        Genre("Escola", "school_life"),
        Genre("Sci-Fi", "sci_fi"),
        Genre("Slice of Life", "slice_of_life"),
        Genre("Esportes", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragédia", "tragedy"),
    )

    // ============================ Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga {
        val jsonLd = document.select("script[type=application/ld+json]")
            .mapNotNull { runCatching { json.parseToJsonElement(it.data()).jsonObject }.getOrNull() }
            .firstOrNull { it["@context"]?.jsonPrimitive?.content == "https://schema.org" }
            ?: return SManga.create()

        return SManga.create().apply {
            title = jsonLd["name"]?.jsonPrimitive?.content ?: ""
            description = jsonLd["description"]?.jsonPrimitive?.content
            thumbnail_url = jsonLd["image"]?.jsonPrimitive?.content
            author = jsonLd["author"]?.jsonObject?.get("name")?.jsonPrimitive?.content
            genre = jsonLd["genre"]?.jsonArray?.joinToString { it.jsonPrimitive.content }
            status = SManga.UNKNOWN
        }
    }

    // ============================== Chapters ===============================
    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseBody = response.body.string()
        val slug = Regex("""/series/([^/?#]+)""").find(response.request.url.toString())?.groupValues?.get(1) ?: return emptyList()
        val match = Regex("seriesData\\\\?\":\\{.*?\\\\?\"capitulos\\\\?\":\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
            .find(responseBody) ?: return emptyList()

        val capitulosJson = "[${match.groupValues[1].replace("\\\"", "\"")}]"
        val capitulos = json.parseToJsonElement(capitulosJson).jsonArray

        return capitulos.mapNotNull {
            val capObj = it.jsonObject
            val numero = capObj["numero"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            SChapter.create().apply {
                name = "Capítulo $numero"
                url = "/series/$slug/$numero"
                date_upload = parseDate(capObj["createdAt"]?.jsonPrimitive?.content)
            }
        }.reversed()
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(dateStr)?.time ?: 0
        }.getOrDefault(0)
    }

    // =============================== Pages =================================
    override fun pageListParse(document: Document): List<Page> {
        val chapterUrl = document.location()
        val slug = chapterUrl.substringAfter("/series/").substringBefore("/")
        val chapterNum = chapterUrl.substringAfterLast("/")

        return buildList {
            var pageNum = 0
            while (pageNum < 100) {
                val imageUrl = "$CDN_URL/file/lycantoons/$slug/$chapterNum/page-$pageNum.jpg"
                val isSuccessful = client.newCall(
                    Request.Builder().url(imageUrl).head().build(),
                ).execute().use { it.isSuccessful }

                if (isSuccessful) {
                    add(Page(pageNum, chapterUrl, imageUrl))
                    pageNum++
                } else {
                    return@buildList
                }
            }
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================= Utilities ===============================
    private fun kotlinx.serialization.json.JsonObject.toSManga() = SManga.create().apply {
        title = get("title")?.jsonPrimitive?.content ?: ""
        url = "/series/${get("slug")?.jsonPrimitive?.content}"
        thumbnail_url = get("coverUrl")?.jsonPrimitive?.content
        description = get("description")?.jsonPrimitive?.content
        author = get("author")?.jsonPrimitive?.content
        artist = get("artist")?.jsonPrimitive?.content
        genre = get("genre")?.jsonArray?.joinToString { it.jsonPrimitive.content }
        status = when (get("status")?.jsonPrimitive?.content) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Filters ================================
    class SeriesTypeFilter : UriPartFilter(
        "Tipo",
        arrayOf(
            "Todos" to "",
            "Manga" to "MANGA",
            "Manhwa" to "MANHWA",
            "Manhua" to "MANHUA",
            "Novel" to "NOVEL",
            "Comic" to "COMIC",
            "Webtoon" to "WEBTOON",
        ),
    )

    class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            "Todos" to "",
            "Em andamento" to "ONGOING",
            "Completo" to "COMPLETED",
            "Hiato" to "HIATUS",
            "Cancelado" to "CANCELLED",
            "Dropado" to "DROPPED",
        ),
    )

    class GenreList(genres: List<Genre>) : Filter.Group<GenreCheckBox>("Gêneros", genres.map { GenreCheckBox(it.name, it.id) })
    class GenreCheckBox(name: String, val id: String) : Filter.CheckBox(name)
    class Genre(val name: String, val id: String)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }
}
