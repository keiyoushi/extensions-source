package eu.kanade.tachiyomi.extension.ja.rawbaka

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.readIntLittleEndian
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.xorInPlace
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RawBaka : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::descrambleImage)

    // The site ranks popularity only in a five-title homepage slider, so Popular lists the whole catalogue
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("manga/")
            if (page > 1) addPathSegments("page/$page/")
        }.build()
        return getMangaList(url)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latest_page", page.toString())
            .build()
        return getMangaList(url)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("s", query.trim())
            addQueryParameter("post_type", "manga")
        }.build()
        return getMangaList(url)
    }

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.manga-grid article.manga-card, article.search-result-card").map { card ->
            val link = card.selectFirst("h2 a, h3 a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("a.cover img")?.coverUrl()
            }
        }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    // Stored urls keep the decoded "/manga/<slug>/" path so existing library entries resolve
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseMangaDetails(document, manga), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = manga.apply {
        val hero = document.selectFirst("section.manga-hero")!!
        title = hero.selectFirst("h1.manga-title")!!.text()
        thumbnail_url = hero.selectFirst(".cover img")?.coverUrl()
        status = when (hero.selectFirst(".status-pill")?.text()?.lowercase()) {
            "ongoing", "on-going" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = hero.select(".manga-summary p").map { paragraph ->
            paragraph.select("br").forEach { it.replaceWith(TextNode("\n")) }
            paragraph.wholeText().lines().map(String::trim).filter(String::isNotEmpty).joinToString("\n")
        }.filter(String::isNotEmpty).joinToString("\n\n").ifEmpty { null }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("div.chapter-list div.chapter-row").map { row ->
        val link = row.selectFirst("a")!!
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = DATE_FORMAT.tryParseDate(row.selectFirst("time")?.text())
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val state = document.selectFirst("script.mr-state")
            ?: return document.select("div.reader-images img").mapIndexed { index, img ->
                Page(index, imageUrl = img.absUrl("src"))
            }
        val imageUrls = decodeReaderState(state.data().trim())
        return document.select("div.reader-images figure.mr-page").mapIndexed { index, figure ->
            Page(index, imageUrl = imageUrls.getValue(figure.attr("data-r")))
        }
    }

    // Base64 of a version byte, a 32-byte XOR key and the reversed payload: count, then id, url and 3x3 tile order per page
    private fun decodeReaderState(state: String): Map<String, String> {
        val bytes = Base64.decode(state, Base64.DEFAULT)
        val payload = bytes.copyOfRange(33, bytes.size).xorInPlace(bytes.copyOfRange(1, 33)).reversedArray()
        var offset = 4
        return List(payload.readIntLittleEndian(0)) {
            val id = payload.toHexString(offset, offset + 8)
            val length = payload.readIntLittleEndian(offset + 8)
            offset += 12
            val url = payload.decodeToString(offset, offset + length)
            offset += length
            val order = List(GRID * GRID) { i ->
                val byte = payload[offset + i / 2].toInt()
                if (i % 2 == 0) byte shr 4 and 0xF else byte and 0xF
            }
            offset += 5
            id to url.toHttpUrl().newBuilder().fragment(order.joinToString(",")).build().toString()
        }.toMap()
    }

    // The reader shuffles each image as a 3x3 grid, tile i of the served image belongs at tile order[i]
    private fun descrambleImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val order = request.url.fragment?.split(',')?.map(String::toInt) ?: return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val image = response.use { BitmapFactory.decodeStream(it.body.byteStream()) }
            ?: throw IOException("Failed to decode image")
        val result = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val tileWidth = image.width / GRID
        val tileHeight = image.height / GRID
        val canvas = Canvas(result)
        order.forEachIndexed { tile, original ->
            val left = tile % GRID * tileWidth
            val top = tile / GRID * tileHeight
            val destLeft = original % GRID * tileWidth
            val destTop = original / GRID * tileHeight
            canvas.drawBitmap(
                image,
                Rect(left, top, left + tileWidth, top + tileHeight),
                Rect(destLeft, destTop, destLeft + tileWidth, destTop + tileHeight),
                null,
            )
        }
        image.recycle()

        val output = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 90, output.outputStream())
        result.recycle()
        return response.newBuilder()
            .removeHeader("Content-Length")
            .removeHeader("Content-Type")
            .body(output.asResponseBody(JPEG, output.size))
            .build()
    }

    // Widest srcset entry; search cards use a cropped thumbnail as src
    private fun Element.coverUrl(): String? = attrOrNull("srcset")
        ?.split(", ")
        ?.maxByOrNull { it.substringAfterLast(' ').removeSuffix("w").toIntOrNull() ?: 0 }
        ?.substringBeforeLast(' ')
        ?: attrOrNull("abs:src")

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
        private val JPEG = "image/jpeg".toMediaType()
        private const val GRID = 3
    }
}
