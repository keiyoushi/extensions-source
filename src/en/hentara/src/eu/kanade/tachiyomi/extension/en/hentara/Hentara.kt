package eu.kanade.tachiyomi.extension.en.hentara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
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

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    private object Api {
        const val BASE = "https://hentara.com/r2-data"

        fun index() = "$BASE/index.json"
        fun comic(slug: String) = "$BASE/comics/$slug.json"
        fun episode(slug: String, ep: Int) = "$BASE/episodes/$slug/$ep.json"
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList().apply { firstInstanceOrNull<SortFilter>()?.state = 1 })

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList().apply { firstInstanceOrNull<SortFilter>()?.state = 0 })

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortIdx = filters.firstInstanceOrNull<SortFilter>()?.state ?: 0
        val genreIdx = filters.firstInstanceOrNull<GenreFilter>()?.state ?: 0

        val url = Api.index().toHttpUrl().newBuilder()
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

        val genre = GENRES.getOrNull(genreIdx) ?: "Any"

        val mangas = data.comics
            .asSequence()
            .filter { comic ->
                (query.isNullOrBlank() || comic.title.contains(query, ignoreCase = true)) &&
                    (genre == "Any" || comic.genres.any { it.name.equals(genre, ignoreCase = true) })
            }
            .let { filtered ->
                when (sortIdx) {
                    0 -> filtered.sortedByDescending { dateFormat.tryParse(it.latestEpisodeDate) }
                    1 -> filtered.sortedByDescending { it.viewCount }
                    2 -> filtered.sortedBy { it.title }
                    else -> filtered
                }
            }
            .map { it.toSManga() }
            .toList()

        return MangasPage(mangas, false)
    }

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET(Api.comic(slug), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<HentaraMangaDto>().comic.toSManga()

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<HentaraMangaDto>()
        val slug = data.comic.slug

        return data.episodes.map { ep ->
            SChapter.create().apply {
                url = "/manhwa/$slug/chapter-${ep.episodeNumber}"
                name = ep.chapterName()
                chapter_number = ep.episodeNumber.toFloat()
                date_upload = dateFormat.tryParse(ep.createdAt)
            }
        }.sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val pathSegments = chapter.url.trim('/').split("/")
        if (pathSegments.size < 3) {
            throw IllegalArgumentException("Malformed chapter URL: ${chapter.url}")
        }
        val slug = pathSegments[1]
        val ep = pathSegments[2].substringAfter("chapter-").toIntOrNull()
            ?: throw IllegalArgumentException("Malformed chapter URL: ${chapter.url}")

        return GET(Api.episode(slug, ep), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<HentaraEpisodeDto>()

        return data.pages.map {
            Page(it.pageNumber - 1, imageUrl = it.imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        GenreFilter(),
    )

    private class GenreFilter : Filter.Select<String>("Genre", GENRES)

    private class SortFilter : Filter.Select<String>("Sort", arrayOf("Latest", "Popular", "Alphabetical"))

    // ============================= Utilities =============================

    companion object {
        private val GENRES = arrayOf(
            "Any", "Action", "BL", "Cheating", "Detective", "Drama", "Harem",
            "In-Law", "MILF", "Married", "Office", "Romance", "Spin-Off",
            "Thriller", "University", "College", "Nerd",
        )

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
