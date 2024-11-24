package eu.kanade.tachiyomi.extension.en.flamecomics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.net.URLDecoder

class FlameComics : ParsedHttpSource() {

    override val client = super.client.newBuilder()
        .rateLimit(2, 7)
        .addInterceptor(::composedImageIntercept)
        .build()

    // Flame Scans -> Flame Comics
    override val id = 6350607071566689772

    override val name = "Flame Comics"

    private val removeSpecialCharsregex = Regex("[^A-Za-z0-9 ]")

    private val host = "flamecomics.xyz"
    override val baseUrl = "https://$host"
    private val cdn = "https:cdn.$host"

    override val lang = "en"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private fun getRealUrl(string: String?): String {
        if (string == null) return ""
        var url = URLDecoder.decode(string.substringAfter("url="), "utf8")
        url = url.substringBefore("?")
        url = url.substringBefore("&")
        return url
    }

    override fun chapterFromElement(element: Element): SChapter =
        throw UnsupportedOperationException()

    override fun chapterListSelector(): String = throw UnsupportedOperationException()
    override fun imageUrlParse(document: Document): String = ""

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.queryParameter("search")

        var page = 1
        if (response.request.url.queryParameter("page") != null) {
            page = Integer.parseInt(response.request.url.queryParameter("page"))
        }

        val doc = response.asJsoup()
        val jsonData = doc.getElementById("__NEXT_DATA__")?.data() ?: return MangasPage(listOf(), false)
        val searchData = json.decodeFromString<SearchPageData>(jsonData)

        val manga = searchData.props.pageProps.series.filter { it -> removeSpecialCharsregex.replace(query.toString().lowercase(), "")  in  removeSpecialCharsregex.replace(it.title.lowercase(), "")
        }.map { it ->
            SManga.create().apply {
                title = it.title
                setUrlWithoutDomain("$baseUrl/series/${it.series_id}")
                thumbnail_url = "$cdn/series/${it.series_id}/${it.cover}"
            }
        }
        Log.i("flamecomics", "" + manga.size)
        page--

        var lastPage = page * 20 + 20
        if (lastPage > manga.size) {
            lastPage = manga.size
        }
        if (lastPage < 0)lastPage = 0
        Log.i("flamecomics", "$page $lastPage")
        return MangasPage(manga.subList(page * 20, lastPage), lastPage < manga.size)
    }

    override fun searchMangaNextPageSelector(): String? = null
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/browse?search=$query&page=$page")
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()
    override fun popularMangaSelector(): String = "div[class^=SeriesCard_imageContainer] img"
    override fun latestUpdatesSelector(): String =
        "div[class^=SeriesCard_chapterImageContainer] img"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("abs:alt").substringAfter("$baseUrl/").replaceFirst("/", "")
        val url = element.attr("abs:src")
        thumbnail_url = getRealUrl(url).substringBefore("&")
        setUrlWithoutDomain(getRealUrl(url).substringBefore("/thumb"))
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = getRealUrl(document.selectFirst("img")?.attr("abs:src"))

        val descElement = document.selectFirst("h3:contains(Description)")

        description =
            descElement?.parent()?.selectFirst("p")?.text()

        status = if (document.selectFirst("span:contains(Ongoing)") != null) {
            SManga.ONGOING
        } else if (document.selectFirst("span:contains(Dropped)") != null) {
            SManga.CANCELLED
        } else {
            SManga.UNKNOWN
        }

        author = document.selectFirst("p:contains(Author)")?.nextElementSibling()?.text()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val jsonData = doc.getElementById("__NEXT_DATA__")?.data() ?: return listOf<SChapter>()
        val pageData = json.decodeFromString<PageData>(jsonData)
        return pageData.props.pageProps.chapters.mapIndexed { idx, chapter ->
            SChapter.create().apply {
                setUrlWithoutDomain("/series/${pageData.props.pageProps.series.series_id}/${chapter.token}")
                date_upload = chapter.release_date * 1000
                var n = "Chapter ${chapter.chapter.toInt()} - "
                if (chapter.title != null) n += chapter.title
                name = n
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img").mapIndexed { idx, img ->
            var url = img.attr("abs:src")
            url = URLDecoder.decode(url.substringAfter("url="), "utf8")
            Page(idx, imageUrl = url.substringBefore("?"))
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
