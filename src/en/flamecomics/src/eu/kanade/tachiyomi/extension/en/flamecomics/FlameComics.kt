package eu.kanade.tachiyomi.extension.en.flamecomics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream

class FlameComics : HttpSource() {
    override val name = "Flame Comics"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId: Int = 2
    override val baseUrl = "https://flamecomics.xyz"
    private val cdn = "https://cdn.flamecomics.xyz"

    private val json: Json by injectLazy()

    override val client = super.client.newBuilder()
        .rateLimit(2, 7)
        .addInterceptor(::buildIdOutdatedInterceptor)
        .addInterceptor(::composedImageIntercept)
        .build()

    private val removeSpecialCharsregex = Regex("[^A-Za-z0-9 ]")

    private fun dataApiReqBuilder() = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("_next")
        addPathSegment("data")
        addPathSegment(buildId)
    }

    private fun imageApiUrlBuilder(dataUrl: String) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("_next")
        addPathSegment("image")
    }.build().toString() + "?url=$dataUrl"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            dataApiReqBuilder().apply {
                addPathSegment("browse.json")
                fragment("$page&${removeSpecialCharsregex.replace(query.lowercase(), "")}")
            }.build(),
            headers,
        )

    override fun popularMangaRequest(page: Int): Request =
        GET(
            dataApiReqBuilder().apply {
                addPathSegment("browse.json")
                fragment("$page")
            }.build(),
            headers,
        )

    override fun latestUpdatesRequest(page: Int): Request = GET(
        dataApiReqBuilder().apply {
            addPathSegment("index.json")
        }.build(),
        headers,
    )

    override fun searchMangaParse(response: Response): MangasPage =
        mangaParse(response) { seriesList ->
            val query = response.request.url.fragment!!.split("&")[1]
            seriesList.filter { series ->
                val titles = mutableListOf(series.title)
                if (series.altTitles != null) {
                    titles += json.decodeFromString<List<String>>(series.altTitles)
                }
                titles.any { title ->
                    removeSpecialCharsregex.replace(
                        query.lowercase(),
                        "",
                    ) in removeSpecialCharsregex.replace(
                        title.lowercase(),
                        "",
                    )
                }
            }
        }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val latestData = json.decodeFromString<LatestPageData>(response.body.string())
        return MangasPage(
            latestData.pageProps.latestEntries.blocks[0].series.map { seriesData ->
                SManga.create().apply {
                    title = seriesData.title
                    setUrlWithoutDomain(
                        dataApiReqBuilder().apply {
                            val seriesID =
                                seriesData.series_id
                            addPathSegment("series")
                            addPathSegment("$seriesID.json")
                            addQueryParameter("id", seriesData.series_id.toString())
                        }.build().toString(),
                    )
                    thumbnail_url = imageApiUrlBuilder(
                        cdn.toHttpUrl().newBuilder().apply {
                            addPathSegment("series")
                            addPathSegment(seriesData.series_id.toString())
                            addPathSegment(seriesData.cover)
                        }.build()
                            .toString() + "&w=640&q=75", // for some reason they don`t include the ?
                    )
                }
            },
            false,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage =
        mangaParse(response) { list -> list.sortedByDescending { it.views } }

    private fun mangaParse(
        response: Response,
        transform: (List<Series>) -> List<Series>,
    ): MangasPage {
        val searchedSeriesData =
            json.decodeFromString<SearchPageData>(response.body.string()).pageProps.series

        val page = if (!response.request.url.fragment?.contains("&")!!) {
            response.request.url.fragment!!.toInt()
        } else {
            response.request.url.fragment!!.split("&")[0].toInt()
        }

        val manga = transform(searchedSeriesData).map { seriesData ->
            SManga.create().apply {
                title = seriesData.title
                setUrlWithoutDomain(
                    baseUrl.toHttpUrl().newBuilder().apply {
                        addPathSegment("series")
                        addPathSegment(seriesData.series_id.toString())
                    }.build().toString(),
                )
                thumbnail_url = imageApiUrlBuilder(
                    cdn.toHttpUrl().newBuilder().apply {
                        addPathSegment("series")
                        addPathSegment(seriesData.series_id.toString())
                        addPathSegment(seriesData.cover)
                    }.build()
                        .toString() + "&w=640&q=75", // for some reason they don`t include the ?
                )
            }
        }
        var lastPage = page * 20
        if (lastPage > manga.size) {
            lastPage = manga.size
        }
        if (lastPage < 0) lastPage = 0
        return MangasPage(manga.subList((page - 1) * 20, lastPage), lastPage < manga.size)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(
        dataApiReqBuilder().apply {
            val seriesID =
                ("$baseUrl/${manga.url}").toHttpUrl().pathSegments.last()
            addPathSegment("series")
            addPathSegment("$seriesID.json")
            addQueryParameter("id", seriesID)
        }.build(),
        headers,
    )

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val seriesData =
            json.decodeFromString<MangaPageData>(response.body.string()).pageProps.series
        title = seriesData.title
        thumbnail_url = imageApiUrlBuilder(
            cdn.toHttpUrl().newBuilder().apply {
                addPathSegment("series")
                addPathSegment(seriesData.series_id.toString())
                addPathSegment(seriesData.cover)
            }.build().toString() + "&w=640&q=75",
        )
        description = seriesData.description
        author = seriesData.author
        status = when (seriesData.status.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "dropped" -> SManga.CANCELLED
            "hiatus" -> SManga.ON_HIATUS
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaPageData = json.decodeFromString<MangaPageData>(response.body.string())
        return mangaPageData.pageProps.chapters.map { chapter ->
            SChapter.create().apply {
                setUrlWithoutDomain(
                    baseUrl.toHttpUrl().newBuilder().apply {
                        addPathSegment("series")
                        addPathSegment(chapter.series_id.toString())
                        addPathSegment(chapter.token)
                    }.build().toString(),
                )
                chapter_number = chapter.chapter.toFloat()
                date_upload = chapter.release_date * 1000
                name = buildString {
                    append("Chapter ${chapter.chapter.toInt()} ")
                    append(chapter.title ?: "")
                }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(
        dataApiReqBuilder().apply {
            val seriesID = ("$baseUrl/${chapter.url}").toHttpUrl().pathSegments[2]
            val token = ("$baseUrl/${chapter.url}").toHttpUrl().pathSegments[3]
            addPathSegment("series")
            addPathSegment(seriesID)
            addPathSegment("$token.json")
            addQueryParameter("id", seriesID)
            addQueryParameter("token", token)
        }.build(),
        headers,
    )

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    override fun pageListParse(response: Response): List<Page> {
        val chapter =
            json.decodeFromString<ChapterPageData>(response.body.string()).pageProps.chapter
        return chapter.images.mapIndexed { idx, page ->
            Page(
                idx,
                imageUrl = imageApiUrlBuilder(
                    cdn.toHttpUrl().newBuilder().apply {
                        addPathSegment("series")
                        addPathSegment(chapter.series_id.toString())
                        addPathSegment(chapter.token)
                        addPathSegment(page.name)
                        addQueryParameter(
                            chapter.release_date.toString(),
                            value = null,
                        )
                        addQueryParameter("w", "1920")
                        addQueryParameter("q", "100")
                    }.build().toString(),
                ),
            )
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    private fun fetchBuildId(document: Document? = null): String {
        val realDocument = document
            ?: client.newCall(GET(baseUrl, headers)).execute().use { it.asJsoup() }

        val nextData = realDocument.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw Exception("Failed to find __NEXT_DATA__")

        val dto = json.decodeFromString<NewBuildID>(nextData)
        return dto.buildId
    }

    private var buildId = ""
        get() {
            if (field == "") {
                field = fetchBuildId()
            }
            return field
        }

    private fun buildIdOutdatedInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (
            response.code == 404 &&
            request.url.run {
                host == baseUrl.removePrefix("https://") &&
                    pathSegments.getOrNull(0) == "_next" &&
                    pathSegments.getOrNull(1) == "data" &&
                    fragment != "DO_NOT_RETRY"
            } &&
            response.header("Content-Type")?.contains("text/html") != false
        ) {
            // The 404 page should have the current buildId
            val document = response.asJsoup()
            buildId = fetchBuildId(document)

            // Redo request with new buildId
            val url = request.url.newBuilder()
                .setPathSegment(2, buildId)
                .fragment("DO_NOT_RETRY")
                .build()
            val newRequest = request.newBuilder()
                .url(url)
                .build()

            return chain.proceed(newRequest)
        }

        return response
    }

    private fun composedImageIntercept(chain: Interceptor.Chain): Response {
        if (!chain.request().url.toString().endsWith(COMPOSED_SUFFIX)) {
            return chain.proceed(chain.request())
        }

        val imageUrls = chain.request().url.toString()
            .removeSuffix(COMPOSED_SUFFIX)
            .split("%7C")

        var width = 0
        var height = 0

        val imageBitmaps = imageUrls.map { imageUrl ->
            val request = chain.request().newBuilder().url(imageUrl).build()
            val response = chain.proceed(request)

            val bitmap = BitmapFactory.decodeStream(response.body.byteStream())

            width += bitmap.width
            height = bitmap.height

            bitmap
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        var left = 0

        imageBitmaps.forEach { bitmap ->
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = Rect(left, 0, left + bitmap.width, bitmap.height)

            canvas.drawBitmap(bitmap, srcRect, dstRect, null)

            left += bitmap.width
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)

        val responseBody = output.toByteArray().toResponseBody(MEDIA_TYPE)

        return Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_1_1)
            .request(chain.request())
            .message("OK")
            .body(responseBody)
            .build()
    }
    // Split Image Fixer End

    companion object {
        private const val COMPOSED_SUFFIX = "?comp"
        private val MEDIA_TYPE = "image/png".toMediaType()
    }
}
