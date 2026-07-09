package eu.kanade.tachiyomi.extension.id.mangakuri

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val DOMAIN = "mangakuri.online"

@Source
abstract class Mangakuri : HttpSource() {

    override val supportsLatest = true

    private val apiUrl = "https://api.$DOMAIN/api"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("sort", "views")
            .addQueryParameter("order", "desc")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()
        val mangas = dto.data.map { it.toSManga() }

        val hasNextPage = response.request.url.queryParameter("page")?.toIntOrNull()?.let { it < dto.totalPages } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("sort", "new")
            .addQueryParameter("order", "desc")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.selectedValue())
                is OrderFilter -> url.addQueryParameter("order", filter.selectedValue())
                is StatusFilter -> {
                    val status = filter.selectedValue()
                    if (status.isNotEmpty()) url.addQueryParameter("status", status)
                }
                is GenreFilter -> {
                    val genre = filter.selectedValue()
                    if (genre.isNotEmpty()) url.addQueryParameter("genre", genre)
                }
                is TypeFilter -> {
                    val type = filter.selectedValue()
                    if (type.isNotEmpty()) url.addQueryParameter("comic_type", type)
                }
                is ColorFilter -> {
                    val color = filter.selectedValue()
                    if (color.isNotEmpty()) url.addQueryParameter("color_format", color)
                }
                is ReadingFilter -> {
                    val reading = filter.selectedValue()
                    if (reading.isNotEmpty()) url.addQueryParameter("reading_format", reading)
                }
                is TextFilter -> {
                    if (filter.state.isNotEmpty()) url.addQueryParameter(filter.queryKey, filter.state)
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/series${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SeriesDetailDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<SeriesDetailDto>()
        val comicSlug = dto.slug
        return dto.units.map { it.toSChapter(comicSlug, dateFormat) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/series${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterDetailDto>()
        return dto.chapter.pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        OrderFilter(),
        StatusFilter(),
        GenreFilter(),
        TypeFilter(),
        ColorFilter(),
        ReadingFilter(),
        TextFilter("Author", "author"),
        TextFilter("Artist", "artist"),
        TextFilter("Publisher", "publisher"),
    )

    // ============================= Utilities =============================

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
