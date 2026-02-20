package eu.kanade.tachiyomi.extension.en.hentara

import eu.kanade.tachiyomi.extension.en.hentara.dto.HentaraComicDto
import eu.kanade.tachiyomi.extension.en.hentara.dto.HentaraEpisodeDto
import eu.kanade.tachiyomi.extension.en.hentara.dto.HentaraIndexDto
import eu.kanade.tachiyomi.extension.en.hentara.dto.HentaraMangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
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
import java.util.TimeZone

class Hentara : HttpSource() {

    override val name = "Hentara"
    override val baseUrl = "https://hentara.com"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://cdn.hentara.com/data"

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ===============================
    // Popular
    // ===============================
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 1 }))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ===============================
    // Latest
    // ===============================
    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 0 }))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ===============================
    // Search
    // ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortIdx = filters.filterIsInstance<SortFilter>().firstOrNull()?.state ?: 0
        val genreIdx = filters.filterIsInstance<GenreFilter>().firstOrNull()?.state ?: 0

        val url = "$apiUrl/index.json".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("query", query)
            .addQueryParameter("sort", sortIdx.toString())
            .addQueryParameter("genre", genreIdx.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<HentaraIndexDto>()
        val url = response.request.url
        val query = url.queryParameter("query")
        val genreIdx = url.queryParameter("genre")?.toIntOrNull() ?: 0
        val sortIdx = url.queryParameter("sort")?.toIntOrNull() ?: 0
        val page = url.queryParameter("page")?.toIntOrNull() ?: 1

        val genre = GENRES.getOrNull(genreIdx) ?: "Any"

        val mangas = data.comics
            .asSequence()
            .filter { comic ->
                (query.isNullOrBlank() || comic.title.contains(query, ignoreCase = true)) &&
                    (genre == "Any" || comic.genres.any { it.name.equals(genre, ignoreCase = true) })
            }
            .let { filtered ->
                when (sortIdx) {
                    0 -> filtered.sortedByDescending { dateFormat.tryParse(it.latest_episode_date?.substringBefore(".")) }
                    1 -> filtered.sortedByDescending { it.view_count }
                    2 -> filtered.sortedBy { it.title }
                    else -> filtered
                }
            }
            .toList()

        return parseMangasPage(mangas, page)
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        GenreFilter(),
    )

    private fun parseMangasPage(mangas: List<HentaraComicDto>, page: Int): MangasPage {
        val itemsPerPage = 30
        val fromIndex = (page - 1) * itemsPerPage
        val toIndex = minOf(fromIndex + itemsPerPage, mangas.size)

        if (fromIndex >= mangas.size) {
            return MangasPage(emptyList(), false)
        }

        val pageMangas = mangas.subList(fromIndex, toIndex).map { it.toSManga() }
        val hasNextPage = toIndex < mangas.size

        return MangasPage(pageMangas, hasNextPage)
    }

    // ===============================
    // Filters
    // ===============================
    private class GenreFilter : Filter.Select<String>("Genre", GENRES)

    private class SortFilter :
        Filter.Select<String>(
            "Sort",
            arrayOf("Latest", "Popular", "Alphabetical"),
        )

    // ===============================
    // Details
    // ===============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/comics/$slug.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<HentaraMangaDto>().comic.toSManga()

    // ===============================
    // Chapters
    // ===============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<HentaraMangaDto>()
        val slug = data.comic.slug

        return data.episodes.map {
            SChapter.create().apply {
                url = "/manhwa/$slug/chapter-${it.episode_number}"
                name = "Chapter ${it.episode_number}${it.title.takeIf { t -> t.isNotBlank() }?.let { t -> " - $t" } ?: ""}"
                chapter_number = it.episode_number.toFloat()
                date_upload = dateFormat.tryParse(it.created_at.substringBefore("."))
            }
        }.sortedByDescending { it.chapter_number }
    }

    // ===============================
    // Pages
    // ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val pathSegments = chapter.url.trim('/').split("/")
        if (pathSegments.size < 3) {
            throw IllegalArgumentException("Malformed chapter URL: ${chapter.url}")
        }
        val slug = pathSegments[1]
        val ep = pathSegments[2].substringAfter("chapter-").toIntOrNull()
            ?: throw IllegalArgumentException("Malformed chapter URL: ${chapter.url}")

        return GET("$apiUrl/episodes/$slug/$ep.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<HentaraEpisodeDto>()

        return data.pages.map {
            Page(it.page_number - 1, "", it.image_url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ===============================
    // Helpers
    // ===============================
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    companion object {
        private val GENRES = arrayOf(
            "Any", "Action", "BL", "Cheating", "Detective", "Drama", "Harem",
            "In-Law", "MILF", "Married", "Office", "Romance", "Spin-Off",
            "Thriller", "University", "College", "Nerd",
        )
    }
}
