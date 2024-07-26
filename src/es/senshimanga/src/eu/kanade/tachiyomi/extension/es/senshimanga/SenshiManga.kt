package eu.kanade.tachiyomi.extension.es.senshimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class SenshiManga : HttpSource() {

    override val name = "Senshi Manga"

    override val baseUrl = "https://visorsenshi.com"

    private val apiUrl = "https://api.visorsenshi.com"

    override val lang = "es"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3)
        .rateLimitHost(apiUrl.toHttpUrl(), 3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val apiHeaders: Headers = headersBuilder()
        .add("Organization-Domain", "visorsenshi.com")
        .build()

    override fun popularMangaRequest(page: Int): Request =
        GET("$apiUrl/api/manga-custom?page=$page&limit=$PAGE_LIMIT&order=popular", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiUrl/api/manga-custom?page=$page&limit=$PAGE_LIMIT&order=latest", apiHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/manga-custom".toHttpUrl().newBuilder()

        url.setQueryParameter("page", page.toString())
        url.setQueryParameter("limit", PAGE_LIMIT.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortByFilter -> url.setQueryParameter("order", filter.toUriPart())
                else -> {}
            }
        }

        if (query.isNotBlank()) url.setQueryParameter("title", query)

        return GET(url.build(), apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")!!.toInt()
        val result = json.decodeFromString<Data<SeriesListDataDto>>(response.body.string())

        val mangas = result.data.series.map { it.toSManga() }
        val hasNextPage = page < result.data.maxPage

        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        SortByFilter("Ordenar por", getSortList()),
    )

    private fun getSortList() = arrayOf(
        Pair("Popularidad", "popular"),
        Pair("Recientes", "latest"),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$apiUrl/api/manga-custom/${manga.url}", apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = json.decodeFromString<Data<SeriesDto>>(response.body.string())
        return result.data.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val seriesSlug = chapter.url.substringBefore("/")
        val chapterSlug = chapter.url.substringAfter("/")

        return "$baseUrl/manga/$seriesSlug/chapters/$chapterSlug"
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<Data<SeriesDto>>(response.body.string())
        val seriesSlug = result.data.slug
        return result.data.chapters?.map { it.toSChapter(seriesSlug) } ?: emptyList()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val seriesSlug = chapter.url.substringBefore("/")
        val chapterSlug = chapter.url.substringAfter("/")

        return GET("$apiUrl/api/manga-custom/$seriesSlug/chapter/$chapterSlug/pages", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<Data<List<PageDto>>>(response.body.string())
        return result.data.mapIndexed { i, page ->
            Page(i, imageUrl = page.imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    class SortByFilter(title: String, list: Array<Pair<String, String>>) : UriPartFilter(title, list)

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        private const val PAGE_LIMIT = 36
    }
}
