package eu.kanade.tachiyomi.extension.es.tenkaiscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class FalcoScan : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es"))

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(FalcoImageInterceptor(baseUrl))
        .rateLimit(3) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        .add("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
        .add("Sec-Fetch-Dest", "document")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Site", "same-origin")
        .add("Sec-Fetch-User", "?1")
        .add("Upgrade-Insecure-Requests", "1")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/comics", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.list-grid a.falco-card, a.falco-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst("h4")?.text()?.trim() ?: ""
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.card-grid a.falco-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst("h4")?.text()?.trim() ?: ""
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/comics".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("search", query.trim())
        } else {
            filters.firstInstanceOrNull<AlphabeticFilter>()?.let {
                if (it.state != 0) urlBuilder.addQueryParameter("filter", it.toUriPart())
            }
            filters.firstInstanceOrNull<GenreFilter>()?.let {
                if (it.state != 0) urlBuilder.addQueryParameter("gen", it.toUriPart())
            }
            filters.firstInstanceOrNull<StatusFilter>()?.let {
                if (it.state != 0) urlBuilder.addQueryParameter("status", it.toUriPart())
            }
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTA: Los filtros serán ignorados si se realiza una búsqueda por texto."),
        Filter.Header("Solo se puede aplicar un filtro a la vez."),
        AlphabeticFilter(),
        GenreFilter(),
        StatusFilter(),
    )

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("div.series-main h1")?.text()?.trim() ?: ""
        description = document.selectFirst("div.series-main p.desc")?.text()?.trim()

        val coverStyle = document.selectFirst("div.series-cover")?.attr("style") ?: ""
        thumbnail_url = if (coverStyle.contains("url('")) {
            coverStyle.substringAfter("url('").substringBefore("')")
        } else {
            document.selectFirst("meta[property=og:image]")?.attr("content")
        }

        genre = document.select("div.series-main div.falco-tags span.falco-tag").joinToString { it.text() }

        document.selectFirst("aside.info-panel")?.let { panel ->
            author = panel.selectFirst("div.info-row:has(span.label:contains(Autor)) span.value")?.text()?.trim()
            artist = panel.selectFirst("div.info-row:has(span.label:contains(Artista)) span.value")?.text()?.trim()
            status = panel.selectFirst("div.info-row:has(span.label:contains(Status)) span.value")?.text()?.parseStatus() ?: SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapters-grid a.chapter-card, a.chapter-card").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.selectFirst("div.ch-name")?.text()?.trim() ?: element.text()
                date_upload = dateFormat.tryParse(element.selectFirst("div.ch-date")?.text()?.trim())
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        val canvases = document.select("div.reader-pages div.cap-canvas, div.cap-canvas")

        canvases.forEachIndexed { i, element ->
            val isScrambled = element.attr("data-scrambled") == "1"
            val dataSrc = element.attr("data-src")
            val manifestSrc = element.attr("data-manifest-src")
            val fragmentBase = element.attr("data-fragment-base")
            val fragmentDir = element.attr("data-fragment-dir")

            if (isScrambled && manifestSrc.isNotBlank()) {
                val encodedBase = URLEncoder.encode(fragmentBase, "UTF-8")
                val encodedDir = URLEncoder.encode(fragmentDir, "UTF-8")
                val url = "$manifestSrc#scramble=1&base=$encodedBase&dir=$encodedDir"
                pages.add(Page(i, imageUrl = url))
            } else if (dataSrc.isNotBlank()) {
                val url = if (dataSrc.startsWith("http")) dataSrc else "$baseUrl$dataSrc"
                pages.add(Page(i, imageUrl = url))
            }
        }

        if (pages.isEmpty()) {
            document.select("div.reader-pages img, div.page-content img").forEachIndexed { i, element ->
                val src = element.imgAttr()
                if (src.isNotBlank()) {
                    pages.add(Page(i, imageUrl = src))
                }
            }
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Referer", "$baseUrl/")
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    private fun Element.imgAttr(): String {
        val src = when {
            this.hasAttr("data-src") && this.attr("data-src").isNotBlank() -> this.attr("data-src")
            else -> this.attr("src")
        }
        return if (src.startsWith("http")) src else "$baseUrl$src"
    }

    private fun String.parseStatus() = when (this.lowercase().trim()) {
        "en emisión", "en emision" -> SManga.ONGOING
        "finalizado", "completed" -> SManga.COMPLETED
        "cancelado", "canceled" -> SManga.CANCELLED
        "en espera", "pausado" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private class FalcoImageInterceptor(private val baseUrl: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            val fragment = url.fragment

            if (fragment == null || !fragment.startsWith("scramble=1")) {
                return chain.proceed(request)
            }

            val manifestUrl = url.newBuilder().fragment(null).build()
            val manifestRequest = request.newBuilder()
                .url(manifestUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$baseUrl/")
                .build()

            val manifestResponse = chain.proceed(manifestRequest)
            if (!manifestResponse.isSuccessful) {
                return manifestResponse
            }

            val manifestJsonStr = manifestResponse.body.string()
            val manifest = JSONObject(manifestJsonStr)
            val width = manifest.getInt("width")
            val height = manifest.getInt("height")
            val pieceWidth = manifest.getInt("pieceWidth")
            val pieceHeight = manifest.getInt("pieceHeight")
            val pieces = manifest.getJSONArray("pieces")

            val params = fragment.split("&").associate {
                val parts = it.split("=", limit = 2)
                parts[0] to if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            }
            val fragmentBase = params["base"] ?: ""
            val fragmentDir = params["dir"] ?: ""

            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val canvas = Canvas(resultBitmap)

            for (idx in 0 until pieces.length()) {
                val pieceObj = pieces.getJSONObject(idx)
                val fileName = pieceObj.getString("file")
                val row = pieceObj.getInt("row")
                val col = pieceObj.getInt("col")

                val piecePath = "projects/$fragmentDir$fileName"
                val encodedPiecePath = Base64.encodeToString(piecePath.toByteArray(), Base64.NO_WRAP)
                val fragmentUrl = fragmentBase.replace("PLACEHOLDER", encodedPiecePath)

                val pieceRequest = request.newBuilder()
                    .url(fragmentUrl)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "$baseUrl/")
                    .build()

                val pieceResponse = chain.proceed(pieceRequest)
                if (pieceResponse.isSuccessful) {
                    val pieceBitmap = BitmapFactory.decodeStream(pieceResponse.body.byteStream())
                    if (pieceBitmap != null) {
                        canvas.drawBitmap(
                            pieceBitmap,
                            (col * pieceWidth).toFloat(),
                            (row * pieceHeight).toFloat(),
                            null,
                        )
                        pieceBitmap.recycle()
                    }
                }
            }

            val output = Buffer()
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output.outputStream())
            resultBitmap.recycle()

            val responseBody = output.asResponseBody("image/jpeg".toMediaType(), output.size)
            return manifestResponse.newBuilder()
                .code(200)
                .message("OK")
                .body(responseBody)
                .build()
        }
    }
}
