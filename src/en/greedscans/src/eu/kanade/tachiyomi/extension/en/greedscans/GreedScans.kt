package eu.kanade.tachiyomi.extension.en.greedscans

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
import java.text.SimpleDateFormat
import java.util.Locale

class GreedScans : HttpSource() {

    override val name = "Greed Scans"
    override val baseUrl = "https://gojoscans.com"
    private val apiUrl = "https://api.gojoscans.com/api"
    override val lang = "en"
    override val supportsLatest = true

    // ==================== POPULAR ====================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ==================== LATEST ====================

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ==================== SEARCH ====================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "24")
            addQueryParameter("sort_order", "desc")
            if (query.isNotEmpty()) addQueryParameter("search", query)
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SeriesListResponse>().data

        val mangas = data.data.map { series ->
            SManga.create().apply {
                url = "/series/${series.slug}"
                title = series.title
                thumbnail_url = series.coverImage
                status = series.status.toStatus()
            }
        }

        return MangasPage(mangas, data.hasNextPage())
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        MinChaptersFilter(),
        GenreFilter(),
    )

    // ==================== MANGA DETAILS ====================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val compatibleUrl = manga.url.replace("/manga/", "/series/")
        return GET("$apiUrl$compatibleUrl", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<SeriesDetailResponse>().data

        return SManga.create().apply {
            url = "/series/${data.slug}"
            title = data.title
            author = data.author
            artist = data.studio
            thumbnail_url = data.coverImage
            status = data.status.toStatus()
            genre = data.genres.joinToString(", ")
            description = buildString {
                data.synopsis?.let { append(it) }
                if (data.alternativeTitles.isNotEmpty()) {
                    append("\n\nAlternative Titles:\n")
                    append(data.alternativeTitles.joinToString("\n"))
                }
            }
        }
    }

    // ==================== CHAPTER LIST ====================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<SeriesDetailResponse>().data

        return data.chapters.sortedByDescending { it.chapterNumber }.map { chapter ->
            SChapter.create().apply {
                url = "/series/${data.slug}/chapters/${chapter.slug}"
                name = chapter.title
                date_upload = (chapter.publishedAt ?: chapter.createdAt)?.let {
                    DATE_FORMAT.tryParse(it)
                } ?: 0L
            }
        }
    }

    companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)
    }

    // ==================== PAGE LIST ====================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val images = response.parseAs<ChapterDetailResponse>().data.chapter.images

        return images.mapIndexed { i, img -> Page(i, imageUrl = img.imageUrl) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // ==================== HELPERS ====================

    private fun String?.toStatus() = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}
