package eu.kanade.tachiyomi.extension.en.mangak

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaK : HttpSource() {

    override val name = "MangaK"
    override val baseUrl = "https://mangak.io"
    override val lang = "en"
    override val supportsLatest = true

    // ---------------------------------------------------------------------------
    // HTTP client
    // ---------------------------------------------------------------------------

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Mobile Safari/537.36",
        )

    // ---------------------------------------------------------------------------
    // Helper: extract __NEXT_DATA__ JSON from any page
    // ---------------------------------------------------------------------------

    private fun Response.nextData(): JSONObject {
        val doc = asJsoup()
        val raw = doc.selectFirst("script#__NEXT_DATA__")?.html()
            ?: throw Exception("__NEXT_DATA__ not found")
        return JSONObject(raw)
            .getJSONObject("props")
            .getJSONObject("pageProps")
    }

    // ---------------------------------------------------------------------------
    // Helper: parse a manga item JSON object into SManga
    // ---------------------------------------------------------------------------

    private fun JSONObject.toSManga(): SManga = SManga.create().apply {
        val slug = optString("slug").ifEmpty { optString("url").trimStart('/') }
        url = "/$slug"
        title = optString("name")
        thumbnail_url = optString("cover")
        status = when (optString("status").lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ---------------------------------------------------------------------------
    // Popular  →  /search?sort=popular&page=N
    // ---------------------------------------------------------------------------

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/search?sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseSearchPage(response)

    // ---------------------------------------------------------------------------
    // Latest  →  /search?sort=latest&page=N
    // ---------------------------------------------------------------------------

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/search?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseSearchPage(response)

    // ---------------------------------------------------------------------------
    // Search  →  /search?q=QUERY&genre=X&status=Y&type=Z&sort=S&page=N
    // ---------------------------------------------------------------------------

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query)
            addQueryParameter("page", page.toString())
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> if (filter.state != 0) {
                        addQueryParameter("genre", GENRES[filter.state].second)
                    }
                    is StatusFilter -> if (filter.state != 0) {
                        addQueryParameter("status", STATUSES[filter.state].second)
                    }
                    is TypeFilter -> if (filter.state != 0) {
                        addQueryParameter("type", TYPES[filter.state].second)
                    }
                    is SortFilter ->
                        addQueryParameter("sort", SORTS[filter.state].second)
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseSearchPage(response)

    // Shared parser for popular / latest / search — all use /search page
    private fun parseSearchPage(response: Response): MangasPage {
        val pageProps = response.nextData()
        val items = pageProps.optJSONArray("ssrItems") ?: JSONArray()
        val pagination = pageProps.optJSONObject("ssrPagination")
        val hasNext = pagination?.optBoolean("has_next", false) ?: false

        val mangas = (0 until items.length()).map { i ->
            items.getJSONObject(i).toSManga()
        }
        return MangasPage(mangas, hasNext)
    }

    // ---------------------------------------------------------------------------
    // Manga details  →  /{slug}
    // ---------------------------------------------------------------------------

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val pageProps = response.nextData()
        val m = pageProps.getJSONObject("initialManga")
        return SManga.create().apply {
            url = m.optString("url").ifEmpty { "/${m.optString("slug")}" }
            title = m.optString("name")
            thumbnail_url = m.optString("cover")
            description = m.optString("summary")
            status = when (m.optString("status").lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "cancelled", "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            // Authors
            val authors = m.optJSONArray("authors")
            if (authors != null && authors.length() > 0) {
                author = (0 until authors.length())
                    .map { authors.getJSONObject(it).optString("name") }
                    .joinToString(", ")
            }
            // Genres
            val genresArr = m.optJSONArray("genres")
            if (genresArr != null) {
                genre = (0 until genresArr.length())
                    .map { genresArr.getJSONObject(it).optString("name") }
                    .joinToString(", ")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Chapter list  →  same /{slug} page, initialManga.chapters array
    // ---------------------------------------------------------------------------

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val pageProps = response.nextData()
        val m = pageProps.getJSONObject("initialManga")
        val mangaSlug = m.optString("slug")
        val chapters = m.optJSONArray("chapters") ?: return emptyList()

        return (0 until chapters.length()).map { i ->
            val c = chapters.getJSONObject(i)
            SChapter.create().apply {
                // URL pattern: /{manga-slug}/{chapter-slug}
                val chapterSlug = c.optString("id") // e.g. "chapter-186"
                url = "/$mangaSlug/$chapterSlug"
                name = c.optString("name")
                date_upload = parseDate(c.optString("updatedAt"))
                chapter_number = c.optDouble("chapterNumber", -1.0).toFloat()
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Page list  →  /{manga-slug}/{chapter-slug}
    //   __NEXT_DATA__ → initialChapter.images  (direct URL array)
    // ---------------------------------------------------------------------------

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val pageProps = response.nextData()
        val chapter = pageProps.getJSONObject("initialChapter")
        val images = chapter.getJSONArray("images")
        return (0 until images.length()).map { i ->
            Page(i, imageUrl = images.getString(i))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // ---------------------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------------------

    override fun getFilterList() = FilterList(
        Filter.Header("Filters ignored when searching by title"),
        Filter.Separator(),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        SortFilter(),
    )

    class GenreFilter : Filter.Select<String>("Genre", GENRES.map { it.first }.toTypedArray())
    class StatusFilter : Filter.Select<String>("Status", STATUSES.map { it.first }.toTypedArray())
    class TypeFilter : Filter.Select<String>("Type", TYPES.map { it.first }.toTypedArray())
    class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray())

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return runCatching {
            DATE_FORMAT.parse(dateStr)?.time ?: 0L
        }.getOrDefault(0L)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

        val GENRES = listOf(
            "Any" to "",
            "Action" to "action",
            "Adventure" to "adventure",
            "Comedy" to "comedy",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Harem" to "harem",
            "Historical" to "historical",
            "Horror" to "horror",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Martial Arts" to "martial-arts",
            "Mecha" to "mecha",
            "Mystery" to "mystery",
            "Psychological" to "psychological",
            "Romance" to "romance",
            "School" to "school",
            "School Life" to "school-life",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Sports" to "sports",
            "Supernatural" to "supernatural",
            "Thriller" to "thriller",
            "Tragedy" to "tragedy",
            "Webtoons" to "webtoons",
            "Yaoi" to "yaoi",
            "Yuri" to "yuri",
        )

        val STATUSES = listOf(
            "Any" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Cancelled" to "cancelled",
        )

        val TYPES = listOf(
            "Any" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        )

        val SORTS = listOf(
            "Latest Updated" to "latest",
            "Most Popular" to "popular",
            "Recently Added" to "newest",
            "Highest Rating" to "rating",
            "Most Viewed" to "views",
            "A–Z" to "alphabetical",
        )
    }
}
