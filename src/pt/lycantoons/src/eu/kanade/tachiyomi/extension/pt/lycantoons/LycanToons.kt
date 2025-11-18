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
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class LycanToons : HttpSource() {

    override val name = "Lycan Toons"

    override val baseUrl = "https://lycantoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    // =====================Popular=====================

    override fun popularMangaRequest(page: Int): Request = metricsRequest("popular", page)

    override fun popularMangaParse(response: Response): MangasPage =
        response.parseAs<PopularResponse>().data.toMangasPage()

    // =====================Latest=====================

    override fun latestUpdatesRequest(page: Int): Request = metricsRequest("recently-updated", page)

    override fun latestUpdatesParse(response: Response): MangasPage =
        response.parseAs<PopularResponse>().data.toMangasPage()

    // =====================Search=====================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = SearchRequestBody(
            limit = PAGE_LIMIT,
            page = page,
            search = query,
            seriesType = filters.valueOrEmpty<SeriesTypeFilter>(),
            status = filters.valueOrEmpty<StatusFilter>(),
            tags = filters.selectedTags(),
        )

        val body = json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)
        return POST("$baseUrl/api/series", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        response.parseAs<SearchResponse>().series.toMangasPage()

    override fun getFilterList(): FilterList = FilterList(
        SeriesTypeFilter(),
        StatusFilter(),
        TagsFilter(),
    )

    // =====================Details=====================

    override fun mangaDetailsRequest(manga: SManga): Request = seriesRequest(manga.slug())

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SeriesDto>()
        return result.toSManga().apply { initialized = true }
    }

    // =====================Chapters=====================

    override fun chapterListRequest(manga: SManga): Request = seriesRequest(manga.slug())

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<SeriesDto>().let { series ->
            series.capitulos.orEmpty()
                .map { it.toSChapter(series.slug) }
                .sortedByDescending { it.chapter_number }
        }

    // =====================Pages========================

    override fun pageListRequest(chapter: SChapter): Request = GET(
        "$baseUrl${chapter.url}",
        headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build(),
    )

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val (slug, chapterNumber) = response.extractSlugAndChapter()

        val pageCount = pageCountFromHtml(html, chapterNumber)
            ?.takeIf { it > 0 }
            ?: error("Quantidade de páginas não encontrada no HTML para capítulo '$chapterNumber'")

        val chapterPath = "$cdnUrl/$slug/$chapterNumber"
        return List(pageCount) { index ->
            val imageUrl = "$chapterPath/page-$index.jpg"
            Page(index, imageUrl, imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =====================Filters=====================

    private class SeriesTypeFilter : ChoiceFilter(
        "Tipo",
        arrayOf(
            "" to "Todos",
            "MANGA" to "Mangá",
            "MANHWA" to "Manhwa",
            "MANHUA" to "Manhua",
            "COMIC" to "Comic",
            "WEBTOON" to "Webtoon",
        ),
    )

    private class StatusFilter : ChoiceFilter(
        "Status",
        arrayOf(
            "" to "Todos",
            "ONGOING" to "Em andamento",
            "COMPLETED" to "Completo",
            "HIATUS" to "Hiato",
            "CANCELLED" to "Cancelado",
        ),
    )

    private open class ChoiceFilter(
        name: String,
        private val entries: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        name,
        entries.map { it.second }.toTypedArray(),
    ) {
        fun getValue(): String = entries[state].first
    }

    private class TagsFilter : Filter.Group<TagCheckBox>(
        "Tags",
        listOf(
            TagCheckBox("Ação", "action"),
            TagCheckBox("Aventura", "adventure"),
            TagCheckBox("Comédia", "comedy"),
            TagCheckBox("Drama", "drama"),
            TagCheckBox("Fantasia", "fantasy"),
            TagCheckBox("Terror", "horror"),
            TagCheckBox("Mistério", "mystery"),
            TagCheckBox("Romance", "romance"),
            TagCheckBox("Vida escolar", "school_life"),
            TagCheckBox("Sci-fi", "sci_fi"),
            TagCheckBox("Slice of life", "slice_of_life"),
            TagCheckBox("Esportes", "sports"),
            TagCheckBox("Sobrenatural", "supernatural"),
            TagCheckBox("Thriller", "thriller"),
            TagCheckBox("Tragédia", "tragedy"),
        ),
    )

    private class TagCheckBox(
        name: String,
        val value: String,
    ) : Filter.CheckBox(name)

    private inline fun <reified T : Filter<*>> FilterList.find(): T? =
        this.filterIsInstance<T>().firstOrNull()

    private inline fun <reified T : ChoiceFilter> FilterList.valueOrEmpty(): String =
        find<T>()?.getValue().orEmpty()

    private fun FilterList.selectedTags(): List<String> =
        find<TagsFilter>()?.state
            ?.filter { it.state }
            ?.map { it.value }
            .orEmpty()

    // =====================Utils=====================

    private fun Response.extractSlugAndChapter(): Pair<String, String> {
        val segments = request.url.pathSegments
        val slug = segments.getOrNull(1)?.trim('/')
            ?: throw IllegalStateException("Slug da série não encontrado na URL")
        val chapterNumber = segments.getOrNull(2)?.trim('/')
            ?: throw IllegalStateException("Número do capítulo não encontrado na URL")
        return slug to chapterNumber
    }

    private fun pageCountFromHtml(html: String, chapterNumber: String): Int? {
        fun keyPattern(key: String) = """(?:\\?["'])?$key(?:\\?["'])?"""
        val numeroPattern = """(?:\\?["'])?\Q$chapterNumber\E(?:\\?["'])?"""
        val regex = Regex(
            """${keyPattern("chapterData")}\s*:\s*\{.*?${keyPattern("numero")}\s*:\s*$numeroPattern\s*,.*?${keyPattern("pageCount")}\s*:\s*(\d+)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        return regex.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun metricsRequest(path: String, page: Int): Request =
        GET("$baseUrl/api/metrics/$path?limit=$PAGE_LIMIT&page=$page", headers)

    private fun List<SeriesDto>.toMangasPage(): MangasPage =
        MangasPage(map { it.toSManga() }, false)

    private fun seriesRequest(slug: String): Request = GET("$baseUrl/api/series/$slug", headers)

    private fun SManga.slug(): String = url.substringAfterLast("/")

    private val json by lazy { jsonInstance }

    companion object {
        private const val PAGE_LIMIT = 13
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val cdnUrl = "https://cdn.lycantoons.com/file/lycantoons"
    }
}
