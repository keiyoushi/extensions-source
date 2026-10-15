package eu.kanade.tachiyomi.extension.es.tenkaiscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class FalcoScan : KeiSource() {
    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrlHost }
        .addInterceptor(::readerInterceptor)

    // The ranking page is permanently in maintenance, the full catalogue is used instead
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/comics").asJsoup(), "a.falco-card")

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(
        client.get(baseUrl).asJsoup(),
        "section:has(h2:containsOwn(Recientemente actualizado)) a.falco-card",
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val urlBuilder = "$baseUrl/comics".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("search", query)
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

        return parseMangaList(client.get(urlBuilder.build()).asJsoup(), "a.falco-card")
    }

    private fun parseMangaList(document: Document, selector: String): MangasPage {
        val mangas = document.select(selector).map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".info h4")!!.text()
                thumbnail_url = element.selectFirst(".cover img")?.absUrl("src")
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("NOTA: Los filtros serán ignorados si se realiza una búsqueda por texto."),
        Filter.Header("Solo se puede aplicar un filtro a la vez."),
        AlphabeticFilter(),
        GenreFilter(),
        StatusFilter(),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            title = document.selectFirst(".series-main h1")!!.text()
            description = document.selectFirst(".series-main p.desc")?.text()
            genre = document.select(".series-main .falco-tag").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".series-cover")?.attr("style")
                ?.let { COVER_REGEX.find(it)?.groupValues?.get(1) }
            author = document.infoValue("Autor")
            artist = document.infoValue("Artista")
            status = document.infoValue("Status")?.parseStatus() ?: SManga.UNKNOWN
        }

        val chapterList = document.select("a.chapter-card").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".ch-name")!!.text()
                date_upload = dateFormat.tryParseDate(element.selectFirst(".ch-date")?.text())
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    private fun Document.infoValue(label: String): String? = select(".info-panel .info-row")
        .firstOrNull { it.selectFirst(".label")?.text() == label }
        ?.selectFirst(".value")?.text()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()
        document.selectFirst("#canvas-reader")?.attr("data-token")?.let { tokens[chapterUrl] = it }

        return document.select("#canvas-reader .cap-canvas").mapIndexed { i, element ->
            val url = element.absUrl("data-src").toHttpUrl().newBuilder()
                .apply { if (element.attr("data-scrambled") == "1") addQueryParameter(SCRAMBLED_PARAM, "1") }
                .fragment(chapterUrl)
                .build()
            Page(i, imageUrl = url.toString())
        }
    }

    // Reader tokens are short-lived and bound to the session cookies, keyed by chapter URL
    private val tokens = ConcurrentHashMap<String, String>()

    private fun fetchToken(chapterUrl: String): String {
        val response = client.newCall(Request.Builder().url(chapterUrl).headers(headers).build()).execute()
        val token = Jsoup.parse(response.body.string()).selectFirst("#canvas-reader")!!.attr("data-token")
        tokens[chapterUrl] = token
        return token
    }

    private fun readerInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapterUrl = request.url.fragment
        if (chapterUrl == null || !request.url.encodedPath.startsWith("/img-")) {
            return chain.proceed(request)
        }

        val scrambled = request.url.queryParameter(SCRAMBLED_PARAM) != null
        val url = request.url.newBuilder().removeAllQueryParameters(SCRAMBLED_PARAM).fragment(null).build()
        val response = fetchWithToken(chain, url.toString(), chapterUrl)
        if (!scrambled || !response.isSuccessful) return response

        val manifest = response.body.string().parseAs<ManifestDto>()
        val bitmap = Bitmap.createBitmap(manifest.w, manifest.h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        manifest.p.forEach { piece ->
            val bytes = fetchWithToken(chain, "$baseUrl/img-fragment/${piece.id}", chapterUrl).use {
                if (!it.isSuccessful) throw Exception("HTTP ${it.code}")
                it.body.bytes()
            }
            val fragment = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val left = (piece.c * manifest.pw).toFloat()
            val top = (piece.r * manifest.ph).toFloat()
            // Fragments are stored mirrored and/or colour-inverted, the bits of `t` say which
            val matrix = Matrix().apply {
                postScale(
                    if (piece.t and 1 != 0) -1f else 1f,
                    if (piece.t and 2 != 0) -1f else 1f,
                    fragment.width / 2f,
                    fragment.height / 2f,
                )
                postTranslate(left, top)
            }
            val paint = Paint().apply {
                if (piece.t and 4 != 0) colorFilter = ColorMatrixColorFilter(INVERT_MATRIX)
            }
            canvas.drawBitmap(fragment, matrix, paint)
            fragment.recycle()
        }

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()

        return response.newBuilder()
            .body(output.toByteArray().toResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    private fun fetchWithToken(chain: Interceptor.Chain, url: String, chapterUrl: String): Response {
        fun call(token: String) = chain.proceed(
            Request.Builder()
                .url(url)
                .headers(headers)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Falco-Token", token)
                .build(),
        )

        val response = call(tokens[chapterUrl] ?: fetchToken(chapterUrl))
        if (response.code != 401) return response
        response.close()
        return call(fetchToken(chapterUrl))
    }

    private fun String.parseStatus() = when (this.lowercase()) {
        "en emisión" -> SManga.ONGOING
        "finalizado" -> SManga.COMPLETED
        "cancelado" -> SManga.CANCELLED
        "en espera" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    @Serializable
    class ManifestDto(
        val w: Int,
        val h: Int,
        val pw: Int,
        val ph: Int,
        val p: List<PieceDto>,
    )

    @Serializable
    class PieceDto(
        val id: String,
        val r: Int,
        val c: Int,
        val t: Int,
    )

    companion object {
        private const val SCRAMBLED_PARAM = "falco_scrambled"
        private val COVER_REGEX = Regex("""url\('([^']+)'\)""")
        private val INVERT_MATRIX = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d/M/yyyy", Locale("es"))
