package eu.kanade.tachiyomi.extension.id.mangakuri

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Mangakuri : HttpSource() {

    override val name = "Mangakuri"

    override val baseUrl = "https://lc1.mangakuri.online/"

    override val lang = "id"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override val versionId = 2

    private val apiUrl = "https://api.mangakuri.online/api"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ================= Popular =================
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
        val mangas = dto.data.map { manga ->
            SManga.create().apply {
                url = "/comic/${manga.slug}"
                title = manga.title
                thumbnail_url = manga.posterImageUrl
            }
        }
        return MangasPage(mangas, response.request.url.queryParameter("page")!!.toInt() < dto.totalPages)
    }

    // ================= Latest =================
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

    // ================= Search =================
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

    // ================= Details =================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/series${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<SeriesDetailDto>()
        return SManga.create().apply {
            url = "/comic/${dto.slug}"
            title = dto.title
            thumbnail_url = dto.posterImageUrl
            author = dto.authorName
            artist = dto.artistName
            description = dto.synopsis?.let { Jsoup.parse(it).text() }
            genre = dto.genres.joinToString { it.name }
            status = when (dto.comicStatus?.uppercase()) {
                "ONGOING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "HIATUS" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ================= Chapters =================
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<SeriesDetailDto>()
        val comicSlug = dto.slug
        return dto.units.map { chapter ->
            SChapter.create().apply {
                url = "/comic/$comicSlug/chapter/${chapter.slug}"
                name = "Chapter ${formatChapterNumber(chapter.number)}"
                chapter_number = chapter.number.toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParse(chapter.createdAt)
            }
        }
    }

    private fun formatChapterNumber(number: String): String = number.removeSuffix(".00")

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ================= Pages =================
    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/series${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterDetailDto>()
        return dto.chapter.pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ================= Filters =================
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

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
