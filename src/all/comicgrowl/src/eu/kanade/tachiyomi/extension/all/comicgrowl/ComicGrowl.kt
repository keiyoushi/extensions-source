package eu.kanade.tachiyomi.extension.all.comicgrowl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class ComicGrowl(
    override val lang: String = "all",
    override val baseUrl: String = "https://comic-growl.com",
    override val name: String = "コミックグロウル",
    override val supportsLatest: Boolean = false,
) : ParsedHttpSource() {

    companion object {
        private const val PUBLISHER = "BUSHIROAD WORKS"

        private val imageUrlRegex by lazy { Regex("^.*?webp") }

        private val DATE_PARSER by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT) }

        private val json: Json by injectLazy()
    }

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(::imageDescrambler)
        .build()

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().set("Referer", "$baseUrl/")
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking/manga", headers)

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaSelector() = ".ranking-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            title = element.selectFirst(".title-text")!!.text()
            author = element.selectFirst(".author-link")?.text()
            setImageUrlFromElement(element)
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst(".series-h-info")!!
        val updateDateElement = infoElement.selectFirst(".series-h-tag-label")
        return SManga.create().apply {
            title = infoElement.select("h1 > span")[1]!!.text()
            author = infoElement.selectFirst(".series-h-credit-user-item > .article-text")?.text()
            description = infoElement.selectFirst(".series-h-credit-info-text-text p")?.wholeText()?.trim()
            setImageUrlFromElement(document.getElementsByClass("series-h-img").first())
            status = if (updateDateElement != null) SManga.ONGOING else SManga.COMPLETED // TODO: need validate
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "/list", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).mapIndexed { index, element ->
            chapterFromElement(element).apply {
                chapter_number = index.toFloat()
            }
        }.filter { it.url.isNotEmpty() }
    }

    override fun chapterListSelector() = ".article-ep-list-item-img-link"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("data-href"))
            name = element.selectFirst(".series-ep-list-item-h-text")!!.text()
            setUploadDate(element.selectFirst(".series-ep-list-date-time"))
            scanlator = PUBLISHER
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageList = mutableListOf<Page>()

        val viewer = document.selectFirst("#comici-viewer")!!
        val comiciViewerId = viewer.attr("comici-viewer-id")
        val memberJwt = viewer.attr("data-member-jwt")
        val requestUrl = "$baseUrl/book/contentsInfo".toHttpUrl().newBuilder()
            .addQueryParameter("comici-viewer-id", comiciViewerId)
            .addQueryParameter("user-id", memberJwt)
            .addQueryParameter("page-from", "0")

        // Initial request to get total pages
        val initialRequest = GET(requestUrl.addQueryParameter("page-to", "1").build(), headers)
        client.newCall(initialRequest).execute().use { initialResponse ->
            if (!initialResponse.isSuccessful) {
                throw Exception("Failed to get page list")
            }
            // FIXME: use util in core and DTO
            val totalPages =
                json.parseToJsonElement(initialResponse.body.string()).jsonObject["totalPages"]!!.jsonPrimitive.content
            // Get all pages
            val getAllPagesRequest = GET(requestUrl.setQueryParameter("page-to", totalPages).build(), headers)
            client.newCall(getAllPagesRequest).execute().use {
                if (!it.isSuccessful) {
                    throw Exception("Failed to get page list")
                }
                val result = json.parseToJsonElement(it.body.string())
                val resultJson = result.jsonObject["result"]!!.jsonArray
                resultJson.forEach { resultJsonElement ->
                    val jsonObject = resultJsonElement.jsonObject
                    // Add fragment to let interceptor to descramble the image
                    val scramble = jsonObject["scramble"]!!.jsonPrimitive.content.drop(1).dropLast(1).replace(", ", "-")
                    val imageUrl =
                        jsonObject["imageUrl"]!!.jsonPrimitive.content.toHttpUrl().newBuilder().fragment(scramble)
                            .build()
                    pageList.add(
                        Page(
                            index = jsonObject["sort"]!!.jsonPrimitive.int,
                            imageUrl = imageUrl.toString(),
                        ),
                    )
                }
            }
        }
        return pageList
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesSelector(): String {
        TODO("Not yet implemented")
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaSelector(): String {
        TODO("Not yet implemented")
    }

    // ========================================= Helper Functions =====================================

    /**
     * Set cover image url from [element] for [SManga]
     */
    private fun SManga.setImageUrlFromElement(element: Element?) {
        if (element == null) {
            return
        }
        val match = imageUrlRegex.find(element.selectFirst("source")!!.attr("data-srcset"))
        // Add missing protocol
        if (match != null) {
            this.thumbnail_url = "https:${match.value}"
        }
    }

    /**
     * Set date_upload to [SChapter], parsing from string like "3月31日" to UNIX Epoch time.
     */
    private fun SChapter.setUploadDate(element: Element?) {
        if (element == null) {
            return
        }
        this.date_upload = DATE_PARSER.tryParse(element.attr("datetime"))
    }

    /**
     * Interceptor to descramble the image.
     */
    private fun imageDescrambler(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val scramble = request.url.fragment ?: return response // return if no scramble fragment
        val tiles = buildList {
            scramble.split("-").forEachIndexed { index, s ->
                val scrambleInt = s.toInt()
                add(index, TilePos(scrambleInt / 4, scrambleInt.mod(4)))
            }
        }

        val scrambledImg = BitmapFactory.decodeStream(response.body.byteStream())
        val descrambledImg = drawDescrambledImage(scrambledImg, scrambledImg.width, scrambledImg.height, tiles)

        val output = ByteArrayOutputStream()
        descrambledImg.compress(Bitmap.CompressFormat.JPEG, 90, output)

        val image = output.toByteArray()
        val body = image.toResponseBody("image/jpeg".toMediaType())

        return response.newBuilder()
            .body(body)
            .build()
    }

    private fun drawDescrambledImage(rawImage: Bitmap, width: Int, height: Int, tiles: List<TilePos>): Bitmap {
        // Prepare canvas
        val descrambledImg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(descrambledImg)

        // Tile width and height(4x4)
        val tileWidth = width / 4
        val tileHeight = height / 4

        // Draw rect
        var count = 0
        for (x in 0..3) {
            for (y in 0..3) {
                val desRect = Rect(x * tileWidth, y * tileHeight, (x + 1) * tileWidth, (y + 1) * tileHeight)
                val srcRect = Rect(
                    tiles[count].x * tileWidth,
                    tiles[count].y * tileHeight,
                    (tiles[count].x + 1) * tileWidth,
                    (tiles[count].y + 1) * tileHeight,
                )
                canvas.drawBitmap(rawImage, srcRect, desRect, null)
                count++
            }
        }
        return descrambledImg
    }

    // Left-top corner position
    private class TilePos(val x: Int, val y: Int)
}
