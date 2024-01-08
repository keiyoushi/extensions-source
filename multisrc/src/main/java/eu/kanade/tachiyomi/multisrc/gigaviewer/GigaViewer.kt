package eu.kanade.tachiyomi.multisrc.gigaviewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor

abstract class GigaViewer(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val cdnUrl: String = "",
) : ParsedHttpSource() {

    override val supportsLatest = true

    protected val dayOfWeek: String by lazy {
        Calendar.getInstance()
            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US)!!
            .lowercase(Locale.US)
    }

    protected open val publisher: String = ""

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series", headers)

    override fun popularMangaSelector(): String = "ul.series-list li a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2.series-list-title")!!.text()
        thumbnail_url = element.selectFirst("div.series-list-thumb img")!!
            .attr("data-src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector(): String = "h2.series-list-date-week.$dayOfWeek + ul.series-list li a"

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // The search returns 404 when there's no results.
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableIgnoreCode(404)
            .map(::searchMangaParse)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("q", query)

            return GET(url.toString(), headers)
        }

        val collectionSelected = (filters[0] as CollectionFilter).selected
        val collectionPath = if (collectionSelected.path.isBlank()) "" else "/" + collectionSelected.path
        return GET("$baseUrl/series$collectionPath", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().contains("search")) {
            return super.searchMangaParse(response)
        }

        return popularMangaParse(response)
    }

    override fun searchMangaSelector() = "ul.search-series-list li, ul.series-list li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.title-box p.series-title")!!.text()
        thumbnail_url = element.selectFirst("div.thmb-container a img")!!.attr("src")
        setUrlWithoutDomain(element.selectFirst("div.thmb-container a")!!.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    protected open fun mangaDetailsInfoSelector(): String = "section.series-information div.series-header"

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.selectFirst(mangaDetailsInfoSelector())!!

        title = infoElement.selectFirst("h1.series-header-title")!!.text()
        author = infoElement.selectFirst("h2.series-header-author")!!.text()
        artist = author
        description = infoElement.selectFirst("p.series-header-description")!!.text()
        thumbnail_url = infoElement.selectFirst("div.series-header-image-wrapper img")!!
            .attr("data-src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val readableProductList = document.selectFirst("div.js-readable-product-list")!!
        val firstListEndpoint = readableProductList.attr("data-first-list-endpoint")
            .toHttpUrlOrNull()!!
        val latestListEndpoint = readableProductList.attr("data-latest-list-endpoint")
            .toHttpUrlOrNull() ?: firstListEndpoint
        val numberSince = latestListEndpoint.queryParameter("number_since")!!.toFloat()
            .coerceAtLeast(firstListEndpoint.queryParameter("number_since")!!.toFloat())

        val newHeaders = headers.newBuilder()
            .set("Referer", response.request.url.toString())
            .build()
        var readMoreEndpoint = firstListEndpoint.newBuilder()
            .setQueryParameter("number_since", numberSince.toString())
            .toString()

        val chapters = mutableListOf<SChapter>()

        var request = GET(readMoreEndpoint, newHeaders)
        var result = client.newCall(request).execute()

        while (result.code != 404) {
            val jsonResult = json.parseToJsonElement(result.body.string()).jsonObject
            readMoreEndpoint = jsonResult["nextUrl"]!!.jsonPrimitive.content
            val tempDocument = Jsoup.parse(
                jsonResult["html"]!!.jsonPrimitive.content,
                response.request.url.toString(),
            )

            tempDocument
                .select("ul.series-episode-list " + chapterListSelector())
                .mapTo(chapters) { element -> chapterFromElement(element) }

            request = GET(readMoreEndpoint, newHeaders)
            result = client.newCall(request).execute()
        }

        result.close()

        return chapters
    }

    override fun chapterListSelector() = "li.episode"

    protected open val chapterListMode = CHAPTER_LIST_PAID

    override fun chapterFromElement(element: Element): SChapter {
        val info = element.selectFirst("a.series-episode-list-container") ?: element
        val mangaUrl = element.ownerDocument()!!.location()

        return SChapter.create().apply {
            name = info.selectFirst("h4.series-episode-list-title")!!.text()
            if (chapterListMode == CHAPTER_LIST_PAID && element.selectFirst("span.series-episode-list-is-free") == null) {
                name = YEN_BANKNOTE + name
            } else if (chapterListMode == CHAPTER_LIST_LOCKED && element.hasClass("private")) {
                name = LOCK + name
            }
            date_upload = info.selectFirst("span.series-episode-list-date")
                ?.text().orEmpty()
                .toDate()
            scanlator = publisher
            setUrlWithoutDomain(if (info.tagName() == "a") info.attr("href") else mangaUrl)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val episode = document.selectFirst("script#episode-json")!!
            .attr("data-value")
            .let {
                try {
                    json.decodeFromString<GigaViewerEpisodeDto>(it)
                } catch (e: SerializationException) {
                    throw Exception("このチャプターは非公開です\nChapter is not available!")
                }
            }

        return episode.readableProduct.pageStructure.pages
            .filter { it.type == "main" }
            .mapIndexed { i, page ->
                val imageUrl = page.src.toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("width", page.width.toString())
                    .addQueryParameter("height", page.height.toString())
                    .toString()
                Page(i, document.location(), imageUrl)
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    protected data class Collection(val name: String, val path: String) {
        override fun toString(): String = name
    }

    private class CollectionFilter(val collections: List<Collection>) : Filter.Select<Collection>(
        "コレクション",
        collections.toTypedArray(),
    ) {
        val selected: Collection
            get() = collections[state]
    }

    override fun getFilterList(): FilterList = FilterList(CollectionFilter(getCollections()))

    protected open fun getCollections(): List<Collection> = emptyList()

    protected open fun imageIntercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        if (!request.url.toString().startsWith(cdnUrl)) {
            return chain.proceed(request)
        }

        val width = request.url.queryParameter("width")!!.toInt()
        val height = request.url.queryParameter("height")!!.toInt()

        val newUrl = request.url.newBuilder()
            .removeAllQueryParameters("width")
            .removeAllQueryParameters("height")
            .build()
        request = request.newBuilder().url(newUrl).build()

        val response = chain.proceed(request)
        val image = decodeImage(response.body.byteStream(), width, height)
        val body = image.toResponseBody(jpegMediaType)

        response.close()

        return response.newBuilder().body(body).build()
    }

    protected open fun decodeImage(image: InputStream, width: Int, height: Int): ByteArray {
        val input = BitmapFactory.decodeStream(image)
        val cWidth = (floor(width.toDouble() / (DIVIDE_NUM * MULTIPLE)) * MULTIPLE).toInt()
        val cHeight = (floor(height.toDouble() / (DIVIDE_NUM * MULTIPLE)) * MULTIPLE).toInt()

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val imageRect = Rect(0, 0, width, height)
        canvas.drawBitmap(input, imageRect, imageRect, null)

        for (e in 0 until DIVIDE_NUM * DIVIDE_NUM) {
            val x = e % DIVIDE_NUM * cWidth
            val y = (floor(e.toFloat() / DIVIDE_NUM) * cHeight).toInt()
            val cellSrc = Rect(x, y, x + cWidth, y + cHeight)

            val row = floor(e.toFloat() / DIVIDE_NUM).toInt()
            val dstE = e % DIVIDE_NUM * DIVIDE_NUM + row
            val dstX = dstE % DIVIDE_NUM * cWidth
            val dstY = (floor(dstE.toFloat() / DIVIDE_NUM) * cHeight).toInt()
            val cellDst = Rect(dstX, dstY, dstX + cWidth, dstY + cHeight)
            canvas.drawBitmap(input, cellSrc, cellDst, null)
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output)
        return output.toByteArray()
    }

    private fun Call.asObservableIgnoreCode(code: Int): Observable<Response> {
        return asObservable().doOnNext { response ->
            if (!response.isSuccessful && response.code != code) {
                response.close()
                throw Exception("HTTP error ${response.code}")
            }
        }
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_PARSER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_PARSER by lazy { SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH) }

        private const val DIVIDE_NUM = 4
        private const val MULTIPLE = 8
        private val jpegMediaType = "image/jpeg".toMediaType()

        const val CHAPTER_LIST_PAID = 0
        const val CHAPTER_LIST_LOCKED = 1

        private const val YEN_BANKNOTE = "💴 "
        private const val LOCK = "🔒 "
    }
}
