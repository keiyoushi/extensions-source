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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class FlameComics : HttpSource() {
    override val name = "Flame Comics"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId: Int = 2
    override val baseUrl = "https://flamecomics.xyz"
    private val cdn = "https://cdn.flamecomics.xyz"

    override val client = super.client.newBuilder()
        .rateLimit(2, 7)
        .addInterceptor(::composedImageIntercept)
        .build()

    private val removeSpecialCharsregex = Regex("[^A-Za-z0-9 ]")

    override fun latestUpdatesParse(response: Response): MangasPage =
        mangaParse(response) { list -> list.sortedByDescending { it.last_edit } }

    override fun popularMangaParse(response: Response): MangasPage =
        mangaParse(response) { list -> list.sortedByDescending { it.views } }

    override fun searchMangaParse(response: Response): MangasPage =
        mangaParse(response) { seriesList ->
            val query = removeSpecialCharsregex.replace(
                response.request.url.queryParameter("search").toString().lowercase(),
                "",
            )
            seriesList.filter { series ->
                val titles = json.decodeFromString<List<String>>(series.altTitles) + series.title
                titles.any { title ->
                    query in removeSpecialCharsregex.replace(
                        title.lowercase(),
                        "",
                    )
                }
            }
        }

    private fun mangaParse(
        response: Response,
        transform: (List<Series>) -> List<Series>,
    ): MangasPage {
        val searchedSeriesData =
            getJsonData<SearchPageData>(response.asJsoup())?.props?.pageProps?.series
                ?: return MangasPage(listOf(), false)

        var page = 1
        if (response.request.url.queryParameter("page") != null) {
            page = Integer.parseInt(response.request.url.queryParameter("page") + "")
        }

        val manga = transform(searchedSeriesData).map { seriesData ->
            SManga.create().apply {
                title = seriesData.title
                setUrlWithoutDomain("$baseUrl/series/${seriesData.series_id}")
                thumbnail_url = "$cdn/series/${seriesData.series_id}/${seriesData.cover}"
            }
        }
        page--

        var lastPage = page * 20 + 20
        if (lastPage > manga.size) {
            lastPage = manga.size
        }
        if (lastPage < 0) lastPage = 0
        return MangasPage(manga.subList(page * 20, lastPage), lastPage < manga.size)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("browse")
                addQueryParameter("search", query)
                addQueryParameter("page", page.toString())
            }.build(),
            headers,
        )

    override fun popularMangaRequest(page: Int): Request =
        GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("browse")
                addQueryParameter("page", page.toString())
            }.build(),
            headers,
        )

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val seriesData = getJsonData<MangaPageData>(response.asJsoup())?.props?.pageProps?.series
            ?: return SManga.create()

        title = seriesData.title
        thumbnail_url = cdn.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            addPathSegment(seriesData.series_id.toString())
            addPathSegment(seriesData.cover)
        }.build().toString()
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
        val mangaPageData = getJsonData<MangaPageData>(response.asJsoup()) ?: return listOf()
        return mangaPageData.props.pageProps.chapters.map { chapter ->
            SChapter.create().apply {
                setUrlWithoutDomain(
                    response.request.url.newBuilder().apply {
                        addQueryParameter("chapterNum", chapter.chapter.toString())
                    }.build().toString(),
                )
                chapter_number = chapter.chapter.toFloat()
                date_upload = chapter.release_date * 1000
                name = buildString {
                    append("Chapter ${chapter.chapter.toInt()}")
                    append(chapter.title ?: "")
                }
            }
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun pageListParse(response: Response): List<Page> {
        val mangaPageData =
            getJsonData<MangaPageData>(response.asJsoup())?.props?.pageProps ?: return listOf()
        val chapterNum = response.request.url.queryParameter("chapterNum")?.toDouble() ?: return listOf()
        val chapter = mangaPageData.chapters.find { c -> c.chapter == chapterNum } ?: return listOf()
        return chapter.images.mapIndexed { idx, page ->
            Page(
                idx,
                imageUrl = cdn.toHttpUrl().newBuilder().apply {
                    addPathSegment("series")
                    addPathSegment(mangaPageData.series.series_id.toString())
                    addPathSegment(chapter.token)
                    addPathSegment(page.name)
                }.build().toString(),
            )
        }
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
