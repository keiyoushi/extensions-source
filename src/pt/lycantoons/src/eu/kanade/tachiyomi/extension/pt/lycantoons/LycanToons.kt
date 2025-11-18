package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup

private val TAG = "LycanToons"

class LycanToons : HttpSource() {

    override val name = "Lycan Toons"

    override val baseUrl = "https://lycantoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val pageHeaders by lazy {
        headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
    }

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

    override fun getFilterList(): FilterList = LycanToonsFilters.get()

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
        pageHeaders,
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
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =====================Utils=====================

    private fun Response.extractSlugAndChapter(): Pair<String, String> {
        val segments = request.url.pathSegments
        val slug = segments.getOrNull(1)?.trim('/')
            ?: throw IllegalStateException("Slug da série não encontrado na URL")
        val chapterNumber = segments.getOrNull(2)?.trim('/')
            ?: throw IllegalStateException("Número do capítulo não encontrado na URL")
        return slug to chapterNumber
    }

    private fun pageCountFromHtml(html: String, chapterNumber: String): Int? = try {
        val jsonStr = getChapterDataJson(html)
        val cleanJsonStr = jsonStr.replace(Regex("\\\\\""), "\"")
        val jsonObj = json.parseToJsonElement(cleanJsonStr).jsonObject
        val numeroStr = jsonObj["numero"]?.jsonPrimitive?.content ?: ""
        val pageCount = jsonObj["pageCount"]?.jsonPrimitive?.intOrNull ?: 0
        if (numeroStr == chapterNumber) pageCount else null
    } catch (e: Exception) {
        null
    }

    private fun getChapterDataJson(html: String): String {
        val document = Jsoup.parse(html)
        val script = document.select("script").firstOrNull { it.data().contains("chapterData", ignoreCase = true) }
            ?: throw Exception("Unable to retrieve chapterData script")

        val data = script.data()
        val keyIndex = data.indexOf("chapterData", ignoreCase = true)
        if (keyIndex == -1) {
            throw Exception("chapterData key not found in script")
        }

        val start = data.indexOf('{', keyIndex)
        if (start == -1) {
            throw Exception("chapterData object start not found")
        }

        var depth = 1
        var i = start + 1
        while (i < data.length && depth > 0) {
            when (data[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }

        return data.substring(start, i)
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
