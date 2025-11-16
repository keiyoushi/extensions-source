package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class LycanToons : HttpSource() {

    override val name = "Lycan Toons"

    override val baseUrl = "https://lycantoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private fun parseMangas(response: Response): MangasPage {
        val result = response.parseAs<PopularResponseDto>()
        return MangasPage(result.data.map { it.toSManga() }, result.pagination.hasNext)
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/metrics/popular?limit=13", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangas(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/api/metrics/recently-updated?limit=13", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangas(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val status = filters.filterIsInstance<StatusFilter>().firstOrNull()?.toApiValue()
        val seriesType = filters.filterIsInstance<SeriesTypeFilter>().firstOrNull()?.toApiValue()
        val tags = filters.filterIsInstance<TagsFilter>().flatMap { it.state.filter { it.state }.map { it.apiValue } }.takeIf { it.isNotEmpty() }
        val body = SearchBodyDto(15, page, query, status, seriesType, tags)
        return POST("$baseUrl/api/series", headers, Json.encodeToString(body).toRequestBody("application/json; charset=utf-8".toMediaType()))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponseDto>()
        return MangasPage(result.series.map { it.toSManga() }, result.pagination.hasNextPage)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Filtros de busca"),
        StatusFilter(),
        SeriesTypeFilter(),
        TagsFilter(),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val slug = response.request.url.pathSegments.last()
        val apiResponse = client.newCall(GET("$baseUrl/api/series/$slug")).execute()
        val result = apiResponse.parseAs<SeriesDataDto>()
        return result.toSManga()
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()
        val slug = response.request.url.pathSegments.last()

        val chaptersJson = extractChaptersJson(html)
        val chapters = json.decodeFromString<List<ChapterDto>>(chaptersJson)

        return chapters.map { chapter ->
            SChapter.create().apply {
                val numeroStr = chapter.numero.toString().removeSuffix(".0")
                name = "Capítulo $numeroStr"
                chapter_number = chapter.numero
                date_upload = dateFormat.tryParse(chapter.createdAt)
                url = "/series/$slug/$numeroStr"
            }
        }.sortedByDescending { it.chapter_number }
    }

    private fun extractChaptersJson(html: String): String {
        val seriesPagePattern = Regex("\"capitulos\":\\s*(\\[.*?\\])", RegexOption.DOT_MATCHES_ALL)
        val seriesMatch = seriesPagePattern.find(html)
        if (seriesMatch != null) {
            return seriesMatch.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        }

        val chapterPagePattern = Regex("\"chapterData\":\\s*(\\{.*?\\})", RegexOption.DOT_MATCHES_ALL)
        val chapterMatch = chapterPagePattern.find(html)
        if (chapterMatch != null) {
            val chapterJson = chapterMatch.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
            return "[$chapterJson]"
        }

        throw Exception("Capítulos não encontrados")
    }

    // =============================== Pages = ===============================

    private val pageListRegex = Regex("""8:\[.*?null,(?<json>\{.*\})\]""")

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val chapterNumberFromUrl = response.request.url.pathSegments.last()

        val match = pageListRegex.find(html)
            ?: throw Exception("JSON de dados não encontrado")

        val jsonString = match.groups["json"]!!.value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
        val seriesData = jsonObject["seriesData"]!!.jsonObject
        val slug = seriesData["slug"]!!.jsonPrimitive.content

        val capitulos = seriesData["capitulos"]!!.jsonArray
        val currentChapterObject = capitulos.firstOrNull {
            it.jsonObject["numero"]!!.jsonPrimitive.content.toString() == chapterNumberFromUrl
        }?.jsonObject ?: throw Exception("Capítulo não encontrado no JSON")

        val numero = currentChapterObject["numero"]!!.jsonPrimitive.content.toString().normalizeChapterNumber()
        val pageCount = currentChapterObject["pageCount"]!!.jsonPrimitive.int

        return (0 until pageCount).map { index ->
            val pageUrl = "https://cdn.lycantoons.com/file/lycantoons/$slug/$numero/page-$index.jpg"
            Page(index = index, url = pageUrl, imageUrl = pageUrl)
        }
    }

    private fun String.normalizeChapterNumber(): String {
        return replace(Regex("\\.0+$"), "").replace(Regex("\\.$"), "")
    }

    override fun imageUrlParse(response: Response): String {
        return response.request.url.toString()
    }

    // ============================== Filters ===============================

    private class StatusFilter : Filter.Select<String>(
        "Status",
        arrayOf("Todos", "Em andamento", "Completo", "Hiato", "Cancelado"),
    ) {
        fun toApiValue(): String? = when (state) {
            0 -> null
            1 -> "ONGOING"
            2 -> "COMPLETED"
            3 -> "HIATUS"
            4 -> "CANCELLED"
            else -> null
        }
    }

    private class SeriesTypeFilter : Filter.Select<String>(
        "Tipo",
        arrayOf("Todos", "Manga", "Manhwa", "Manhua", "Novel"),
    ) {
        fun toApiValue(): String? = when (state) {
            0 -> null
            1 -> "MANGA"
            2 -> "MANHWA"
            3 -> "MANHUA"
            4 -> "NOVEL"
            else -> null
        }
    }

    private class TagsFilter : Filter.Group<TagCheckBox>(
        "Gêneros",
        listOf(
            TagCheckBox("Action", "action"),
            TagCheckBox("Adventure", "adventure"),
            TagCheckBox("Comedy", "comedy"),
            TagCheckBox("Drama", "drama"),
            TagCheckBox("Fantasy", "fantasy"),
            TagCheckBox("Horror", "horror"),
            TagCheckBox("Mystery", "mystery"),
            TagCheckBox("Romance", "romance"),
            TagCheckBox("School Life", "school_life"),
            TagCheckBox("Sci-Fi", "sci_fi"),
            TagCheckBox("Slice of Life", "slice_of_life"),
            TagCheckBox("Sports", "sports"),
            TagCheckBox("Supernatural", "supernatural"),
            TagCheckBox("Tragedy", "tragedy"),
            TagCheckBox("Thriller", "thriller"),
        ),
    )

    private class TagCheckBox(name: String, val apiValue: String) : Filter.CheckBox(name)

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        private val json = Json { ignoreUnknownKeys = true }
    }
}
