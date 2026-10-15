package eu.kanade.tachiyomi.extension.all.dragonballmultiverse

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer

@Source
abstract class DbMultiverse : KeiSource() {

    private val internalLang: String
        get() = when (lang) {
            "en" -> "en"
            "fr" -> if (name.endsWith("Parody")) "fr_PA" else "fr"
            "ja" -> "jp"
            "zh" -> "cn"
            "es" -> "es"
            "it" -> "it"
            "pt" -> "pt"
            "de" -> "de"
            "pl" -> "pl"
            "nl" -> "nl"
            "tr" -> "tr_TR"
            "pt-BR" -> "pt_BR"
            "hu" -> "hu_HU"
            "ga" -> "ga_ES"
            "ca" -> "ct_CT"
            "no" -> "no_NO"
            "ru" -> "ru_RU"
            "ro" -> "ro_RO"
            "eu" -> "eu_EH"
            "lt" -> "lt_LT"
            "hr" -> "hr_HR"
            "ko" -> "kr_KR"
            "fi" -> "fi_FI"
            "he" -> "he_HE"
            "bg" -> "bg_BG"
            "sv" -> "sv_SE"
            "el" -> "gr_GR"
            "es-419" -> "es_CO"
            "ar" -> "ar_JO"
            "fil" -> "tl_PI"
            "la" -> "la_LA"
            "da" -> "da_DK"
            "co" -> "co_FR"
            "br" -> "br_FR"
            "vec" -> "xx_VE"
            "lmo" -> "xx_LMO"
            else -> lang
        }

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addNetworkInterceptor(::drawBalloonsOnImage)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/$internalLang/read.html").asJsoup()
        val mangas = document.select("#dbm-reads .dbm-read").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                description = element.selectFirst("> div")?.text()
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    protected open val chapterListSelector: String = ".cadrelect.chapter"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val document = client.get(getMangaUrl(manga)).asJsoup()
        val newChapters = document.select(chapterListSelector).map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a[href]")!!.attr("abs:href"))
                name = it.selectFirst("h4")!!.text()
            }
        }.reversed()

        return SMangaUpdate(manga, newChapters)
    }

    @Serializable
    class PageLayout(
        val scale: Float,
        val balloons: List<BalloonBox>,
    )

    @Serializable
    class BalloonBox(
        val text: String,
        val left: Float,
        val top: Float,
        val width: Float,
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".pageslist a[href]").mapIndexed { index, a ->
            Page(index, url = a.attr("abs:href"))
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()
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
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
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
}
