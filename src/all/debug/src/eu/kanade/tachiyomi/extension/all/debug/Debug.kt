package eu.kanade.tachiyomi.extension.all.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

class Debug : HttpSource() {

    override val name = "Mihon Debugger"
    override val baseUrl = "http://example.com"
    override val lang = "all"
    override val supportsLatest = false

    // ==============================
    // Interceptor for Rendering Text & Preventing 404s
    // ==============================

    override val client = network.client.newBuilder().addInterceptor { chain ->
        val request = chain.request()
        val httpUrl = request.url

        // 1. Prevent 404s on dummy routes: Intercept all example.com requests
        if (httpUrl.host == "example.com") {
            return@addInterceptor Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200) // Force a success code so Mihon doesn't abort
                .message("OK").body("Dummy Body".toResponseBody("text/plain".toMediaTypeOrNull())).build()
        }

        // 2. Intercept the passive analysis to draw the response as an image
        if (httpUrl.encodedFragment == "render_text") {
            Log.d("DebugExtension", "Intercepting render request: ${request.url}")

            val response = chain.proceed(request)

            val textResponse = response.body.string()
            Log.d("DebugExtension", "Original text response:\n$textResponse")

            val bitmap = textToBitmap(textResponse)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)

            return@addInterceptor response.newBuilder().code(200)
                .body(stream.toByteArray().toResponseBody("image/png".toMediaTypeOrNull())).build()
        } else if (httpUrl.encodedFragment == "render_gif") {
            Log.d("DebugExtension", "Intercepting render request: ${request.url}")

            val response = chain.proceed(request)

            val textResponse = response.body.string()

            val gifBase = byteArrayOf(
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
                0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0x21, 0xf9.toByte(), 0x04, 0x01,
                0x00, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00, 0x00, 0x00, 0x01,
                0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3b,
            )
            val bytes = gifBase + ("\n" + textResponse + "\n").toByteArray()

            return@addInterceptor response.newBuilder().code(200)
                .body(bytes.toResponseBody("image/gif".toMediaTypeOrNull())).build()
        }

        // Proceed normally for any other requests (like the Active Analysis image endpoints)
        chain.proceed(request)
    }.build()

    private fun textToBitmap(text: String): Bitmap {
        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
        }

        @Suppress("DEPRECATION")
        val staticLayout = StaticLayout(text, textPaint, 1000, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)

        // Add extra height to ensure the bottom text isn't cut off
        val bitmap = Bitmap.createBitmap(1040, staticLayout.height + 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.WHITE)
        canvas.translate(20f, 20f) // Add slight padding
        staticLayout.draw(canvas)

        return bitmap
    }

    // ==============================
    // Static Extension Data Routing
    // ==============================

    // Latest Updates Route
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = listOf(
            SManga.create().apply {
                title = "Passive Analysis"
                url = "/passive"
                description = "Renders TLS/Bot analysis directly into a viewable image."
            },
            SManga.create().apply {
                title = "Active Analysis"
                url = "/active"
                description = "Triggers endpoints natively. Returns broken images."
            },
        )
        return MangasPage(mangas, false)
    }

    // Manga Details Route
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val url = response.request.url.toString()
        return SManga.create().apply {
            title = if (url.contains("/passive")) "Passive Analysis" else "Active Analysis"
            initialized = true
        }
    }

    // Chapter List Route
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val baseInt = System.currentTimeMillis()
        val rUrl = response.request.url.toString()
        val chapters = mutableListOf<SChapter>()

        if (rUrl.contains("/passive")) {
            chapters.add(
                SChapter.create().apply {
                    name = "1. Tls.peet.ws (Rendered Output)"
                    url = "/passive/1/$baseInt"
                },
            )
        } else if (rUrl.contains("/active")) {
            chapters.add(
                SChapter.create().apply {
                    name = "1. Tls.peet.ws"
                    url = "/active/1/$baseInt"
                },
            )
            chapters.add(
                SChapter.create().apply {
                    name = "2. Device & Browser Info"
                    url = "/active/2/$baseInt"
                },
            )
            chapters.add(
                SChapter.create().apply {
                    name = "3. Web Browser"
                    url = "/active/3/$baseInt"
                },
            )
        }
        return chapters.reversed()
    }

    // Page List Route
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url.toString()
        return when {
            url.contains("/passive/1") -> listOf(
                Page(0, "", "https://tls.peet.ws/api/all#render_text"),
                Page(1, "", "https://tls.peet.ws/api/all#render_gif"),
            )

            url.contains("/active/1") -> listOf(Page(0, "", "https://tls.peet.ws/api/all"))
            url.contains("/active/2") -> listOf(
                Page(0, "", "https://deviceandbrowserinfo.com/are_you_a_bot"),
                Page(1, "", "https://fingerprint-scan.com/fpscanner/demo"),
            )

            url.contains("/active/3") -> listOf(Page(0, "", "https://google.com"))

            else -> emptyList()
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ==============================
    // Unused Required Overrides
    // ==============================

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = latestUpdatesRequest(page)
    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)
}
