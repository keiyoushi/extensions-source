package eu.kanade.tachiyomi.extension.id.komikcast

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class KomikCast : HttpSource() {

    override val id = 972717448578983812
    override val name = "Komik Cast"
    override val baseUrl = "https://v1.komikcast.fit"
    private val apiUrl = "https://be.komikcast.fit"
    override val lang = "id"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "application/json")
        .add("Accept-language", "en-US,en;q=0.9,id;q=0.8")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("includeMeta", "true")
            .addQueryParameter("sort", "popularity")
            .addQueryParameter("sortOrder", "desc")
            .addQueryParameter("take", "12")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseSeriesListResponse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("includeMeta", "true")
            .addQueryParameter("sort", "latest")
            .addQueryParameter("sortOrder", "desc")
            .addQueryParameter("take", "12")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseSeriesListResponse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("includeMeta", "true")
            .addQueryParameter("take", "12")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("filter", "title=like=\"$query\",nativeTitle=like=\"$query\"")
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseSeriesListResponse(response)

    override fun getMangaUrl(manga: SManga): String {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        val slug = path[1]
        return "$baseUrl/series/$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        val slug = path[1]
        return GET("$apiUrl/series/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SeriesDetailResponse>()
        return result.data.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        val slug = path[1]
        return GET("$apiUrl/series/$slug/chapters", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterListResponse>()
        val slug = response.request.url.pathSegments[1]
        return result.data.map { it.toSChapter(slug) }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("/chapter/")) {
            val slug = chapter.url.substringAfter("/chapter/").substringBefore("-chapter-")
            val chapterIndex = chapter.url.substringAfter("-chapter-").substringBefore("-bahasa-")
            return GET("$apiUrl/series/$slug/chapters/$chapterIndex", headers)
        }

        val path = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        val slug = path[1]
        val chapterIndex = path[3]
        return GET("$apiUrl/series/$slug/chapters/$chapterIndex", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterDetailResponse>()
        val images = result.data.data.images ?: emptyList()
        return images.mapIndexed { index, imageUrl ->
            Page(index, "", imageUrl)
        }
    }

    /**
     * Handle both responses:
     * - Popular/Latest/Search: {"data": [...], "meta": {...}}  (array)
     * - Related/Suggestions:   {"data": {"id":..., "data":{...}}} (single object)
     */
    private fun parseSeriesListResponse(response: Response): MangasPage {
        val body = response.body.string()

        return try {
            // Normal: data is array
            val result = json.decodeFromString(SeriesListResponse.serializer(), body)
            val mangas = result.data.map { it.toSManga() }
            val hasNextPage = result.meta?.let { (it.page ?: 0) < (it.lastPage ?: 0) } ?: false
            MangasPage(mangas, hasNextPage)
        } catch (_: Exception) {
            // Fallback: data is single object (suggestions/related)
            try {
                val result = json.decodeFromString(SeriesDetailResponse.serializer(), body)
                val manga = result.data.toSManga()
                MangasPage(listOf(manga), false)
            } catch (_: Exception) {
                MangasPage(emptyList(), false)
            }
        }
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        SortOrderFilter(),
        StatusFilter(),
        FormatFilter(),
        TypeFilter(),
        GenreFilter(getGenres()),
    )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
