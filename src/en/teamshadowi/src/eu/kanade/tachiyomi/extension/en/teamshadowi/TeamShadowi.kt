package eu.kanade.tachiyomi.extension.en.teamshadowi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TeamShadowi : HttpSource() {

    override val name = "Team Shadowi"

    override val baseUrl = "https://www.team-shadowi.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Reusable headers for fetching raw JSON React Server Component payloads
    private val rscHeaders by lazy { headers.newBuilder().add("Rsc", "1").build() }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 20
        return GET("$baseUrl/api/series/popular?timePeriod=all&genre=all&sortBy=rating&offset=$offset&limit=20", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<SeriesResponse>()
        val mangas = res.data.map { it.toSManga() }
        return MangasPage(mangas, res.hasMore)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * 20
        return GET("$baseUrl/api/series/popular?timePeriod=all&genre=all&sortBy=created&offset=$offset&limit=20", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
            return GET(url, headers)
        }

        val offset = (page - 1) * 20
        val url = "$baseUrl/api/series/popular".toHttpUrl().newBuilder()
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "20")
            .addQueryParameter("timePeriod", "all")

        var genre = "all"
        var sort = "rating"

        for (filter in filters) {
            when (filter) {
                is GenreFilter -> genre = filter.toUriPart()
                is SortFilter -> sort = filter.toUriPart()
                else -> {}
            }
        }

        url.addQueryParameter("genre", genre)
        url.addQueryParameter("sortBy", sort)

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()

        if (requestUrl.contains("/api/search")) {
            val res = response.parseAs<SearchResponse>()
            val mangas = res.series.map { it.toSManga() }
            return MangasPage(mangas, false) // Text search endpoint does not paginate
        }

        return popularMangaParse(response)
    }

    // =============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.extractNextJs<PublicDataSeries>()?.series
            ?: throw Exception("Failed to extract series data")

        return SManga.create().apply {
            title = data.title
            description = data.description
            thumbnail_url = data.thumbnailUrl
            status = when (data.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = (data.genres.orEmpty() + data.tags.orEmpty()).distinct().joinToString()
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<PublicDataSeries>()?.chapters
            ?: return emptyList()

        val slug = response.request.url.pathSegments.last()

        return data.map { chap ->
            val numStr = chap.number.toString().removeSuffix(".0")
            val cleanDate = chap.createdAt?.substringBefore("+")?.substringBefore("Z")
            SChapter.create().apply {
                url = "/read/$slug/$numStr"
                name = if (chap.title.isNullOrBlank()) "Chapter $numStr" else "Chapter $numStr: ${chap.title}"
                date_upload = dateFormat.tryParse(cleanDate)
                chapter_number = chap.number
            }
        }.sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<PublicDataChapter>()?.pages
            ?: return emptyList()

        return data.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = FilterList(
        SortFilter(),
        GenreFilter(),
    )
}
