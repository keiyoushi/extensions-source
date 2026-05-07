@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.pt.mangadash

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Suppress("unused")
class MangaDash :
    HttpSource(),
    ConfigurableSource {

    override val name = "MangaDash"
    override val baseUrl = "https://mangadash.net"
    override val lang = "pt-BR"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    // Retrieve the app's dedicated cache directory safely
    private val cacheDir: File by lazy {
        File(Injekt.get<Application>().cacheDir, "mangadash_pdf_cache").apply {
            mkdirs()
        }
    }

    private val loginInterceptor = Interceptor { chain ->
        val request = chain.request()

        // Skip login requests to avoid infinite loops
        if (request.url.encodedPath.contains("auth/login")) {
            return@Interceptor chain.proceed(request)
        }

        val username = preferences.getString(PREF_USERNAME, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""

        if (username.isNotBlank() && password.isNotBlank()) {
            val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            val hasSession = cookies.any { it.name == "session" }

            if (!hasSession) {
                synchronized(this) {
                    val currentCookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                    if (!currentCookies.any { it.name == "session" }) {
                        try {
                            performLogin(username, password)
                        } catch (_: Exception) {
                            // Ignore login errors to let the original request proceed and fail naturally
                        }
                    }
                }
            }
        }

        chain.proceed(request)
    }

    private val pdfInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        if (url.pathSegments.firstOrNull() == "pdf-page") {
            val fileName = url.pathSegments[1]
            val pageIndex = url.pathSegments[2].toInt()

            val file = File(cacheDir, fileName)
            if (!file.exists()) {
                throw IOException("PDF file not found in cache")
            }

            return@Interceptor ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(pageIndex).use { page ->
                        // Scale up the PDF page resolution (PDF coordinates are 72 DPI).
                        // Max width bounded to ~1500px to ensure Memory Safety on large files.
                        val scale = minOf(2.5f, 1500f / page.width)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt(),
                            (page.height * scale).toInt(),
                            Bitmap.Config.ARGB_8888,
                        )

                        // PDF pages are transparent by default, fill the background with white
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // Memory-efficient stream-based image processing using Okio
                        val buffer = Buffer()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())
                        bitmap.recycle() // Important: free native memory immediately

                        val body = buffer.asResponseBody("image/jpeg".toMediaType())

                        Response.Builder()
                            .code(200)
                            .protocol(Protocol.HTTP_1_1)
                            .request(request)
                            .message("OK")
                            .body(body)
                            .build()
                    }
                }
            }
        }
        chain.proceed(request)
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(loginInterceptor)
        .addInterceptor(pdfInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", "application/json, text/plain, */*")
        .add("Referer", "$baseUrl/")

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val usernamePref = EditTextPreference(screen.context).apply {
            key = PREF_USERNAME
            title = "Usuário / Email"
            summary = "Preencha para fazer login automaticamente"
            setDefaultValue("")
        }

        val passwordPref = EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Senha"
            summary = "Preencha para fazer login automaticamente"
            setDefaultValue("")
        }

        val infoPref = EditTextPreference(screen.context).apply {
            key = "pref_info_18plus"
            title = "Atenção: Conteúdo +18"
            summary = "Para acessar o conteúdo +18, você precisa de uma conta e habilitar a opção no site.\n\n" +
                "Passo a passo:\n" +
                "1. Abra a extensão no WebView (ícone de bússola).\n" +
                "2. Faça login na sua conta.\n" +
                "3. Acesse seu Perfil -> Configurações -> Preferências.\n" +
                "4. Marque a opção 'Habilitar Conteúdo Adulto (+18)'.\n" +
                "5. Clique em 'Salvar Preferências'."
            setOnPreferenceClickListener { true }
        }

        screen.addPreference(usernamePref)
        screen.addPreference(passwordPref)
        screen.addPreference(infoPref)
    }

    private fun performLogin(username: String, password: String) {
        val loginUrl = "$baseUrl/auth/login"
        val baseClient = network.cloudflareClient

        // 1. Get CSRF Token. Using Use block for safety.
        val getRequest = GET(loginUrl, headers)
        val document = baseClient.newCall(getRequest).execute().asJsoup()
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content")
            ?: document.selectFirst("input[name=csrf_token]")?.attr("value")

        if (csrfToken.isNullOrEmpty()) {
            return
        }

        // 2. Perform Login POST
        val formBody = FormBody.Builder()
            .add("csrf_token", csrfToken)
            .add("login", username)
            .add("password", password)
            .build()

        val postRequest = POST(loginUrl, headers, formBody)
        baseClient.newCall(postRequest).execute().close()
    }

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/mangas/list?page=$page&q=&sort=populares&categoria=&status=&ano=&plus18=", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MangaListDto>()
        return MangasPage(dto.mangas, dto.hasNext)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/mangas/list?page=$page&q=&sort=recentes&categoria=&status=&ano=&plus18=", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "recentes"
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart() ?: ""
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: ""
        val year = filters.firstInstanceOrNull<YearFilter>()?.toUriPart() ?: ""
        val plus18 = if (filters.firstInstanceOrNull<Plus18Filter>()?.state == true) "true" else ""

        val url = "$baseUrl/api/mangas/list".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("q", query)
            addQueryParameter("sort", sort)
            addQueryParameter("categoria", category)
            addQueryParameter("status", status)
            addQueryParameter("ano", year)
            addQueryParameter("plus18", plus18)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".neon-title")?.text()!!
            author = document.selectFirst(".tag-author")?.text()
            genre = document.select(".manga-tags a.tag:not(.tag-author)").joinToString { it.text() }
            description = document.selectFirst(".manga-description")?.text()

            val statsText = document.select(".stats-row .stat-item").text()
            status = when {
                statsText.contains("Lançamento", ignoreCase = true) -> SManga.ONGOING
                statsText.contains("Concluído", ignoreCase = true) -> SManga.COMPLETED
                statsText.contains("Hiato", ignoreCase = true) -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(".chapters-scroll-container .chapter-row").map { element ->
            SChapter.create().apply {
                url = element.attr("href")
                name = element.selectFirst(".chapter-title-group h4")?.text()!!
                val dateStr = element.selectFirst(".chapter-meta-info:has(.fa-calendar)")?.text()
                date_upload = dateStr?.let { dateFormat.tryParse(it) } ?: 0L

                // Extract chapter number for proper sorting fixing the website's alphabetical issue
                chapter_number = name.substringAfter("Capítulo").trim().toFloatOrNull() ?: -1f
            }
        }

        return chapters.sortedWith(
            compareByDescending<SChapter> { it.chapter_number }
                .thenByDescending { it.name },
        )
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val dataElement = document.selectFirst("script#chapterViewerData")
            ?: throw Exception("Dados do capítulo não encontrados")

        val dto = dataElement.data().parseAs<ChapterDataDto>()

        val images = dto.resolvedImages
        if (!images.isNullOrEmpty()) {
            return images.mapIndexed { i, img -> Page(i, imageUrl = img) }
        }

        var pdfUrl = dto.resolvedPdfUrl
        if (!pdfUrl.isNullOrBlank()) {
            pdfUrl = pdfUrl.replace(" ", "%20")
            if (pdfUrl.startsWith("/")) {
                pdfUrl = baseUrl + pdfUrl
            }

            // Create a specific client with a longer timeout to prevent OkHttp crashing on 30MB+ PDF downloads
            val pdfClient = client.newBuilder()
                .readTimeout(3, TimeUnit.MINUTES)
                .connectTimeout(1, TimeUnit.MINUTES)
                .build()

            val pdfHeaders = headersBuilder()
                .add("Origin", baseUrl)
                .build()

            val pdfRequest = GET(pdfUrl, pdfHeaders)

            // Using use { ... } around manual network calls block for reliable memory release
            pdfClient.newCall(pdfRequest).execute().use { pdfResponse ->
                if (!pdfResponse.isSuccessful) {
                    throw Exception("HTTP error ${pdfResponse.code} ao baixar o arquivo PDF do capítulo")
                }

                // Cleanup older PDF files to free up disk space (removes files older than 1 hr)
                cacheDir.listFiles()?.forEach {
                    if (System.currentTimeMillis() - it.lastModified() > 3600000) {
                        it.delete()
                    }
                }

                val tempFile = File(cacheDir, "chapter_${System.currentTimeMillis()}.pdf")

                // Use stream saving to prevent buffering heavy PDFs entirely into memory
                pdfResponse.body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val pageCount = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.pageCount
                    }
                }

                return (0 until pageCount).map { i ->
                    Page(i, imageUrl = "$baseUrl/pdf-page/${tempFile.name}/$i")
                }
            }
        }

        throw Exception("Nenhuma imagem encontrada para este capítulo.")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        SortFilter(),
        CategoryFilter(),
        StatusFilter(),
        YearFilter(),
        Plus18Filter(),
    )

    // ============================= Utilities =============================
    companion object {
        private const val PREF_USERNAME = "pref_username"
        private const val PREF_PASSWORD = "pref_password"

        private val dateFormat by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        }
    }
}
