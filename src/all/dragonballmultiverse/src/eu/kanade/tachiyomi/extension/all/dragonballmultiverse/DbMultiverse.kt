package eu.kanade.tachiyomi.extension.all.dragonballmultiverse

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import rx.Observable

abstract class DbMultiverse(override val lang: String, private val internalLang: String) : HttpSource() {

    override val name =
        if (internalLang.endsWith("_PA")) {
            "Dragon Ball Multiverse Parody"
        } else {
            "Dragon Ball Multiverse"
        }

    override val baseUrl = "https://www.dragonball-multiverse.com"

    override val supportsLatest = false

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(::drawBalloonsOnImage)
        .build()

    private fun createManga(type: String) = SManga.create().apply {
        title = when (type) {
            "comic" -> "DB Multiverse"
            "namekseijin" -> "Namekseijin Densetsu"
            "strip" -> "Minicomic"
            else -> name
        }
        status = SManga.ONGOING
        url = "/$internalLang/chapters.html?comic=$type"
        description = "Dragon Ball Multiverse (DBM) is a free online comic, made by a whole team of fans. It's our personal sequel to DBZ."
        thumbnail_url = "$baseUrl/imgs/read/$type.jpg"
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        // site hosts three titles that can be read by the app
        return listOf("page", "strip", "namekseijin")
            .map { createManga(it) }
            .let { Observable.just(MangasPage(it, hasNextPage = false)) }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = manga.apply {
        initialized = true
    }.let { Observable.just(it) }

    protected open val chapterListSelector: String = ".cadrelect.chapter"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector).map {
            SChapter.create().apply {
                val href = it.selectFirst("a")!!.attr("href")
                setUrlWithoutDomain("/$internalLang/$href")
                name = it.selectFirst("h4")!!.text()
            }
        }.reversed()
    }

    @Serializable
    data class PageLayout(
        val scale: Float,
        val balloons: List<BalloonBox>,
    )

    @Serializable
    data class BalloonBox(
        val text: String,
        val left: Float,
        val top: Float,
        val width: Float,
    )

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".pageslist a").mapIndexed { index, a ->
            val href = a.attr("href")
            Page(index, url = baseUrl + "/$internalLang/$href")
        }
    }

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()
        val element = document.selectFirst("#balloonsimg")!!

        val rawImageUrl = when {
            element.hasAttr("src") -> element.attr("abs:src")
            element.selectFirst("img") != null -> element.selectFirst("img")!!.attr("abs:src")
            else -> {
                val styleUrl = element.attr("style").substringAfter("url(").substringBefore(")")
                val cleanUrl = styleUrl.removeSurrounding("\"").removeSurrounding("'")
                if (cleanUrl.startsWith("http")) cleanUrl else baseUrl + cleanUrl
            }
        }

        val balloons = element.select(".balloon").map { b ->
            val style = b.attr("style")

            BalloonBox(
                text = b.text(),
                left = style.extractCssProp("left"),
                top = style.extractCssProp("top"),
                width = style.extractCssProp("width"),
            )
        }

        val pageData = PageLayout(
            scale = element.attr("style").substringAfter("scale(", "")
                .substringBefore(")", "")
                .toFloatOrNull() ?: 1f,
            balloons = balloons,
        )

        return if (balloons.isNotEmpty()) {
            "$rawImageUrl#${pageData.toJsonString()}"
        } else {
            rawImageUrl
        }
    }

    fun String.extractCssProp(prop: String, default: String = "0"): Float = substringAfter("$prop:", default)
        .substringBefore(";")
        .filter { it.isDigit() || it == '.' }
        .toFloatOrNull() ?: 0f

    private fun drawBalloonsOnImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.fragment.isNullOrEmpty()) {
            return response
        }

        val page = request.url.fragment!!.parseAs<PageLayout>()

        val bitmap = response.body.byteStream().use { stream ->
            BitmapFactory.decodeStream(stream)
        }

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.SANS_SERIF
            isAntiAlias = true
        }

        page.balloons.forEach { b ->
            val x = b.left * page.scale
            val y = b.top * page.scale
            val w = (b.width * page.scale).toInt().coerceAtLeast(1)

            val layout = StaticLayout.Builder.obtain(b.text, 0, b.text.length, textPaint, w)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
                .build()

            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
        }

        val buffer = Buffer().apply {
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream())
        }

        mutableBitmap.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/jpeg".toMediaType(), buffer.size))
            .build()
    }

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
}
