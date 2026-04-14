package eu.kanade.tachiyomi.extension.en.teamshadowi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    override fun mangaDetailsRequest(manga: SManga): Request {
        // Appending 'Rsc: 1' fetches the raw JSON React Server Component payload
        return GET(baseUrl + manga.url, headers.newBuilder().add("Rsc", "1").build())
    }

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
            genre = (data.genres.orEmpty() + data.tags.orEmpty()).distinct().joinToString(", ")
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<PublicDataSeries>()?.chapters
            ?: throw Exception("Failed to extract chapter data")

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
    override fun pageListRequest(chapter: SChapter): Request {
        // Using Rsc header to grab embedded JSON pages for the chapter instead of HTML
        return GET(baseUrl + chapter.url, headers.newBuilder().add("Rsc", "1").build())
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<PublicDataChapter>()?.pages
            ?: throw Exception("Failed to extract pages data")

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

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    class SortFilter :
        UriPartFilter(
            "Sort By",
            arrayOf(
                Pair("Rating", "rating"),
                Pair("Latest", "created"),
                Pair("Views", "views"),
                Pair("Title", "title"),
            ),
        )

    class GenreFilter :
        UriPartFilter(
            "Genre",
            arrayOf(
                Pair("All", "all"),
                Pair("Action", "action"),
                Pair("Adventure", "adventure"),
                Pair("Comedy", "comedy"),
                Pair("Drama", "drama"),
                Pair("Ecchi", "ecchi"),
                Pair("Fantasy", "fantasy"),
                Pair("Isekai", "isekai"),
                Pair("Romance", "romance"),
            ),
        )

    // ============================== Models ================================
    @Serializable
    data class SeriesResponse(
        val data: List<Series>,
        val hasMore: Boolean = false,
    )

    @Serializable
    data class SearchResponse(
        val series: List<Series> = emptyList(),
    )

    @Serializable
    data class Series(
        val title: String,
        val slug: String,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
        val status: String? = null,
        val description: String? = null,
        val genres: List<String>? = emptyList(),
    ) {
        fun toSManga() = SManga.create().apply {
            title = this@Series.title
            url = "/series/${this@Series.slug}"
            thumbnail_url = this@Series.thumbnailUrl
            status = when (this@Series.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = this@Series.description
            genre = this@Series.genres?.joinToString(", ")
        }
    }

    @Serializable
    data class PublicDataSeries(
        val series: SeriesDetails,
        val chapters: List<ChapterData> = emptyList(),
    )

    @Serializable
    data class SeriesDetails(
        val title: String,
        val description: String? = null,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
        val status: String? = null,
        val genres: List<String>? = emptyList(),
        val tags: List<String>? = emptyList(),
    )

    @Serializable
    data class ChapterData(
        val id: String,
        val number: Float,
        val title: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
    )

    @Serializable
    data class PublicDataChapter(
        val pages: List<String> = emptyList(),
    )
}
