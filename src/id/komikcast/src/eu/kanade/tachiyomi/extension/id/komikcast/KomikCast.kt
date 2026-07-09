package eu.kanade.tachiyomi.extension.id.komikcast

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Source
abstract class KomikCast : HttpSource() {

    private val apiUrl = "https://be.komikcast.cc"
    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
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

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.getSlug(baseUrl)}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/series/${manga.getSlug(baseUrl)}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SeriesDetailResponse>()
        return result.data.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/series/${manga.getSlug(baseUrl)}/chapters", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterListResponse>()
        val slug = response.request.url.pathSegments[1]
        return result.data.map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, chapterIndex) = chapter.getSlugAndIndex(baseUrl)
        return "$baseUrl/series/$slug/chapter/$chapterIndex"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val (slug, chapterIndex) = chapter.getSlugAndIndex(baseUrl)
        return GET("$apiUrl/series/$slug/chapters/$chapterIndex", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterDetailResponse>()
        return result.data.toPageList()
    }

    private fun parseSeriesListResponse(response: Response): MangasPage {
        val result = response.parseAs<SeriesListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.meta?.let { it.page ?: 0 < (it.lastPage ?: 0) } ?: false
        return MangasPage(mangas, hasNextPage)
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
