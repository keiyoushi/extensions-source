package eu.kanade.tachiyomi.extension.all.tappytoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import java.io.IOException
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class Tappytoon : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val res = chain.proceed(chain.request())
        val mime = res.headers["Content-Type"]
        if (res.isSuccessful) {
            if (mime != "application/octet-stream") {
                return@addInterceptor res
            }
            // Fix image content type
            val type = IMG_CONTENT_TYPE.toMediaType()
            val body = res.body.source().asResponseBody(type)
            return@addInterceptor res.newBuilder().body(body).build()
        }
        // Throw JSON error if available
        if (mime == "application/json") {
            throw IOException(res.parseAs<ErrorResponse>().message)
        }
        res.close()
        throw IOException("HTTP error ${res.code}")
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("User-Agent", System.getProperty("http.agent")!!)
        .set("Referer", "https://www.tappytoon.com/")
        .set("Origin", "https://www.tappytoon.com")

    private var apiHeaders: Headers? = null

    private suspend fun apiHeaders(): Headers = apiHeaders ?: run {
        val data = client.get(baseUrl).asJsoup().getElementById("__NEXT_DATA__")!!
        val axiosHeaders = data.data().parseAs<NextData>().props.initialState.axios.headers
        headers.newBuilder()
            .set("Accept-Language", lang)
            .set("Authorization", axiosHeaders.authorization)
            .set("X-Device-Uuid", axiosHeaders.deviceUuid)
            .build()
            .also { apiHeaders = it }
    }

    private var nextUrl: String? = null

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrl.newBuilder()
            .addEncodedPathSegment("comics")
            .addEncodedQueryParameter("day_of_week", day)
            .addEncodedQueryParameter("locale", lang)
            .build()

        return parseComics(client.get(url, apiHeaders()))
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiUrl.newBuilder()
            .addEncodedPathSegment("comics")
            .addEncodedQueryParameter("sort_by", "trending")
            // Sort is only available for completed series
            .addEncodedQueryParameter("filter", "completed")
            .addEncodedQueryParameter("locale", lang)
            .build()

        return parseComics(client.get(url, apiHeaders()))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = nextUrl?.takeIf { page > 1 }?.toHttpUrl() ?: apiUrl.newBuilder().run {
            addEncodedPathSegments("comics")
            addEncodedQueryParameter("locale", lang)
            val genre = filters.firstInstanceOrNull<Genre>()
            if (genre != null && genre.state != 0) {
                addEncodedQueryParameter("genre", genre.alias)
                addEncodedQueryParameter("limit", "50")
            } else if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            }
            build()
        }

        val response = client.get(url, apiHeaders())
        val link = response.headers["Link"]
        nextUrl = link?.substringAfter('<')?.substringBefore('>')
        return parseComics(response).copy(hasNextPage = link != null)
    }

    private fun parseComics(response: Response) = response.parseAs<List<Comic>>().accessible.map {
        SManga.create().apply {
            url = it.toString()
            title = it.title
            description = it.longDescription
            thumbnail_url = it.posterThumbnailUrl
            author = it.authors.joinToString()
            artist = author
            genre = buildString {
                it.genres.joinToString(this, postfix = ", ")
                append("Rating: ").append(it.ageRating)
            }
            status = when {
                it.isCompleted -> SManga.COMPLETED
                !it.isHiatus -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }.run { MangasPage(this, false) }

    // The real URL for the webview
    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${manga.slug}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val url = apiUrl.newBuilder()
            .addEncodedPathSegments("comics/${manga.id}/chapters")
            .addEncodedQueryParameter("locale", lang)
            .build()

        val chapterList = client.get(url, apiHeaders()).parseAs<List<Chapter>>().accessible.asReversed().map {
            SChapter.create().apply {
                name = it.toString()
                this.url = it.id.toString()
                chapter_number = it.order + 1f
                date_upload = Instant.tryParse(it.willAccessibleAt)
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = apiUrl.newBuilder()
            .addEncodedPathSegments("content-delivery/contents")
            .addEncodedQueryParameter("chapterId", chapter.url)
            .addEncodedQueryParameter("variant", "high")
            .addEncodedQueryParameter("locale", lang)
            .build()

        return client.get(url, apiHeaders()).parseAs<Media>().mapIndexed { idx, img ->
            Page(idx, "", img.toString())
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("NOTE: can't be used with text search!"),
        Genre(genres.keys.toTypedArray()),
    )

    class Genre(values: Array<String>) : Filter.Select<String>("Genre", values)

    private inline val Genre.alias: String
        get() = genres[values[state]]!!

    private inline val SManga.slug: String
        get() = url.substringBefore('|')

    private inline val SManga.id: String
        get() = url.substringAfter('|')

    private val day: String
        get() = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(Locale.ENGLISH)

    companion object {
        private const val IMG_CONTENT_TYPE = "image/jpeg"

        private val apiUrl: HttpUrl = "https://api-global.tappytoon.com".toHttpUrl()

        private val genres = mapOf(
            "<select>" to "",
            "Action" to "action",
            "Romance" to "romance",
            "Fantasy" to "fantasy",
            "School" to "school",
            "Slice of Life" to "slice",
            "BL" to "bl",
            "Comedy" to "comedy",
            "GL" to "gl",
        )
    }
}
