package eu.kanade.tachiyomi.extension.all.tappytoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Tappytoon(override val lang: String) : HttpSource() {
    override val name = "Tappytoon"

    override val baseUrl = "https://www.tappytoon.com/$lang"

    override val supportsLatest = true

    override val client = network.client.newBuilder().addInterceptor { chain ->
        val res = chain.proceed(chain.request())
        val mime = res.headers["Content-Type"]
        if (res.isSuccessful) {
            if (mime != "application/octet-stream") {
                return@addInterceptor res
            }
            // Fix image content type
            val type = IMG_CONTENT_TYPE.toMediaType()
            val body = res.body.bytes().toResponseBody(type)
            return@addInterceptor res.newBuilder().body(body)
                .header("Content-Type", IMG_CONTENT_TYPE).build()
        }
        // Throw JSON error if available
        if (mime == "application/json") {
            res.body.string().let(json::parseToJsonElement).run {
                throw IOException(jsonObject["message"]!!.jsonPrimitive.content)
            }
        }
        res.close()
        throw IOException("HTTP error ${res.code}")
    }.build()

    private val json by injectLazy<Json>()

    private val apiHeaders by lazy {
        val res = client.newCall(GET(baseUrl, headers)).execute()
        val data = res.asJsoup().getElementById("__NEXT_DATA__")!!
        val obj = json.parseToJsonElement(data.data())
            .jsonObject["props"]!!.jsonObject["initialState"]!!
            .jsonObject["axios"]!!.jsonObject["headers"]!!.jsonObject
        val auth = obj["Authorization"]!!.jsonPrimitive.content
        val uuid = obj["X-Device-Uuid"]!!.jsonPrimitive.content
        headers.newBuilder()
            .set("Origin", "https://www.tappytoon.com")
            .set("Accept-Language", lang)
            .set("Authorization", auth)
            .set("X-Device-Uuid", uuid)
            .build()
    }

    private var nextUrl: String? = null

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", System.getProperty("http.agent")!!)
        .set("Referer", "https://www.tappytoon.com/")

    override fun latestUpdatesRequest(page: Int) =
        apiUrl.newBuilder().run {
            addEncodedPathSegment("comics")
            addEncodedQueryParameter("day_of_week", day)
            addEncodedQueryParameter("locale", lang)
            GET(toString(), apiHeaders)
        }

    override fun popularMangaRequest(page: Int) =
        apiUrl.newBuilder().run {
            addEncodedPathSegment("comics")
            addEncodedQueryParameter("sort_by", "trending")
            // Sort is only available for completed series
            addEncodedQueryParameter("filter", "completed")
            addEncodedQueryParameter("locale", lang)
            GET(toString(), apiHeaders)
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (nextUrl != null) return GET(nextUrl!!, apiHeaders)
        val url = apiUrl.newBuilder()
            .addEncodedPathSegments("comics")
            .addEncodedQueryParameter("locale", lang)
        val genre = filters.find { it is Genre } as? Genre
        if (genre != null && genre.state != 0) {
            url.addEncodedQueryParameter("genre", genre.alias)
            url.addEncodedQueryParameter("limit", "50")
        } else if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
        }
        return GET(url.toString(), apiHeaders)
    }

    // Request the real URL for the webview
    override fun mangaDetailsRequest(manga: SManga) =
        GET("$baseUrl/comics/${manga.slug}", headers)

    override fun chapterListRequest(manga: SManga) =
        apiUrl.newBuilder().run {
            addEncodedPathSegments("comics/${manga.id}/chapters")
            addEncodedQueryParameter("locale", lang)
            GET(toString(), apiHeaders)
        }

    override fun pageListRequest(chapter: SChapter) =
        apiUrl.newBuilder().run {
            addEncodedPathSegments("content-delivery/contents")
            addEncodedQueryParameter("chapterId", chapter.url)
            addEncodedQueryParameter("variant", "high")
            addEncodedQueryParameter("locale", lang)
            GET(toString(), apiHeaders)
        }

    override fun latestUpdatesParse(response: Response) =
        response.parse<List<Comic>>().accessible.map {
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

    override fun popularMangaParse(response: Response) =
        latestUpdatesParse(response)

    override fun searchMangaParse(response: Response) =
        response.headers["Link"].let {
            nextUrl = it?.substringAfter('<')?.substringBefore('>')
            latestUpdatesParse(response).copy(hasNextPage = it != null)
        }

    override fun chapterListParse(response: Response) =
        response.parse<List<Chapter>>().accessible.asReversed().map {
            SChapter.create().apply {
                name = it.toString()
                url = it.id.toString()
                chapter_number = it.order + 1f
                date_upload = dateFormat.parse(it.createdAt)?.time ?: 0L
            }
        }

    override fun pageListParse(response: Response) =
        response.parse<Media>().mapIndexed { idx, img ->
            Page(idx, "", img.toString())
        }

    override fun fetchMangaDetails(manga: SManga) =
        rx.Observable.just(manga.apply { initialized = true })!!

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: can't be used with text search!"),
        Genre(genres.keys.toTypedArray()),
    )

    private inline fun <reified T> Response.parse() =
        json.decodeFromJsonElement<T>(json.parseToJsonElement(body.string()))

    class Genre(values: Array<String>) : Filter.Select<String>("Genre", values)

    private inline val Genre.alias: String
        get() = genres[values[state]]!!

    private inline val SManga.slug: String
        get() = url.substringBefore('|')

    private inline val SManga.id: String
        get() = url.substringAfter('|')

    override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    companion object {
        private const val IMG_CONTENT_TYPE = "image/jpeg"

        private val apiUrl = "https://api-global.tappytoon.com".toHttpUrl()

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

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-d'T'HH:mm:ss", Locale.ROOT)
        }

        private val day by lazy {
            when (Calendar.getInstance()[Calendar.DAY_OF_WEEK]) {
                Calendar.SUNDAY -> "sun"
                Calendar.MONDAY -> "mon"
                Calendar.TUESDAY -> "tue"
                Calendar.WEDNESDAY -> "wed"
                Calendar.THURSDAY -> "thu"
                Calendar.FRIDAY -> "fri"
                Calendar.SATURDAY -> "sat"
                else -> error("What day is it?")
            }
        }
    }
}
