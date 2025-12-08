package eu.kanade.tachiyomi.extension.pt.lycantoons

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element

class LycanToons : HttpSource() {

    override val name = "Lycan Toons"

    override val baseUrl = "https://lycantoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = seriesRequest(manga.slug())

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SeriesDto>()
        return result.toSManga()
    }

    // =====================Chapters=====================

    override fun chapterListRequest(manga: SManga): Request = seriesRequest(manga.slug())

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<SeriesDto>().let { series ->
            series.capitulos!!
                .map { it.toSChapter(series.slug) }
                .sortedByDescending { it.chapter_number }
        }

    // =====================Pages========================

    override fun pageListRequest(chapter: SChapter): Request = GET(
        "$baseUrl${chapter.url}",
        pageHeaders,
    )

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.select("script:containsData(self.__next_f)")
            .joinToString("\n", transform = Element::data)

        val content = QuickJs.create().use {
            it.evaluate(
                """
                globalThis.self = globalThis;
                $script
                self.__next_f.map(it => it[it.length - 1]).join('')
                """.trimIndent(),
            ) as String
        }

        val images = PAGES_REGEX.find(content)!!.groupValues.last().parseAs<List<String>>()

        return images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =====================Utils=====================

    private fun metricsRequest(path: String, page: Int): Request =
        GET("$baseUrl/api/metrics/$path?limit=$PAGE_LIMIT&page=$page", headers)

    private fun List<SeriesDto>.toMangasPage(): MangasPage =
        MangasPage(map { it.toSManga() }, false)

    private fun seriesRequest(slug: String): Request = GET("$baseUrl/api/series/$slug", headers)

    private fun SManga.slug(): String = url.substringAfterLast("/")

    private val json by lazy { jsonInstance }

    companion object {
        private const val PAGE_LIMIT = 13
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val PAGES_REGEX = """"imageUrls":([^]]+])""".toRegex()
    }
}
