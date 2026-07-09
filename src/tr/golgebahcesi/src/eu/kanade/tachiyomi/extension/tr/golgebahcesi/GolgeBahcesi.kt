package eu.kanade.tachiyomi.extension.tr.golgebahcesi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class GolgeBahcesi : HttpSource() {

    private val apiBaseUrl = "https://api.golgebahcesi.com/api"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET("$apiBaseUrl/series?page=$page&limit=24&sort=popular", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.pagination?.let { it.currentPage < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiBaseUrl/series?page=$page&limit=24&sort=updatedAt", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "default"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: ""
        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: ""
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: ""
        val minChapters = filters.firstInstanceOrNull<MinChaptersFilter>()?.state?.toString()?.takeIf { it.isNotBlank() } ?: ""

        val url = "$apiBaseUrl/series?page=$page&limit=24&sort=$sort".toHttpUrl().newBuilder()
        if (query.isNotBlank()) url.addQueryParameter("search", query)
        if (status.isNotBlank()) url.addQueryParameter("status", status)
        if (type.isNotBlank()) url.addQueryParameter("type", type)
        if (genre.isNotBlank()) url.addQueryParameter("genre", genre)
        if (minChapters.isNotBlank()) url.addQueryParameter("minChapters", minChapters)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.pagination?.let { it.currentPage < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiBaseUrl/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SeriesDto>().toSManga()

    override fun chapterListRequest(manga: SManga): Request = GET("$apiBaseUrl/series/${manga.url}/chapters", headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<List<ChapterDto>>().map { chapter ->
        SChapter.create().apply {
            url = "/${chapter.id}/${chapter.seriesSlug}/${chapter.slug}"
            name = chapter.title
            chapter_number = chapter.number
            date_upload = dateFormat.tryParse(chapter.releaseDate ?: chapter.createdAt)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val segments = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        return "$baseUrl/manga/${segments[1]}/bolum/${segments[2]}"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments[0]
        return GET("$apiBaseUrl/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<ChapterDto>().pages?.map { page ->
        Page(page.index, imageUrl = page.url)
    } ?: emptyList()

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
        MinChaptersFilter(),
    )

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException()
}
