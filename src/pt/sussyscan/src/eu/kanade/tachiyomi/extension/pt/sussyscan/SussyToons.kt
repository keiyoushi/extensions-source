package eu.kanade.tachiyomi.extension.pt.sussyscan

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.HTTP_BAD_GATEWAY
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SussyToons : HttpSource(), ConfigurableSource {

    override val name = "Sussy Toons"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val id = 6963507464339951166

    // Moved from Madara
    override val versionId = 2

    private val json: Json by injectLazy()

    private val isCi = System.getenv("CI") == "true"

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    private var _apiUrlCache: String? = null

    private var apiUrl: String
        get() = _apiUrlCache ?: preferences.prefApiUrl.also { _apiUrlCache = it }
        set(value) { _apiUrlCache = value }

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    private val SharedPreferences.prefBaseUrl: String get() = getString(BASE_URL_PREF, defaultBaseUrl)!!
    private val SharedPreferences.prefApiUrl: String get() = getString(API_BASE_URL_PREF, defaultApiUrl)!!
    private fun SharedPreferences.prefApiUrlUpSet(url: String): String {
        edit().putString(API_BASE_URL_PREF, url)
            .apply()
        return url
    }

    private val defaultBaseUrl: String = "https://www.sussytoons.site"
    private val defaultApiUrl: String = "https://api-dev.sussytoons.site"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addInterceptor(::findApiUrl)
        .addInterceptor(::findChapterUrl)
        .addInterceptor(::chapterPages)
        .addInterceptor(::imageLocation)
        .build()

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { domain ->
            if (domain != defaultBaseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
        preferences.getString(API_DEFAULT_BASE_URL_PREF, null).let { domain ->
            if (domain != defaultApiUrl) {
                preferences.edit()
                    .putString(API_BASE_URL_PREF, defaultApiUrl)
                    .putString(API_DEFAULT_BASE_URL_PREF, defaultApiUrl)
                    .apply()
            }
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("scan-id", "1") // Required header for requests

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/obras/top5", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<WrapperDto<List<MangaDto>>>()
        val mangas = dto.results.filterNot { it.slug.isNullOrBlank() }.map { it.toSManga() }
        return MangasPage(mangas, false) // There's a pagination bug
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/novos-capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<WrapperDto<List<MangaDto>>>()
        val mangas = dto.results.filterNot { it.slug.isNullOrBlank() }.map { it.toSManga() }
        return MangasPage(mangas, dto.hasNextPage())
    }

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "8")
            .addQueryParameter("obr_nome", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addPathSegment(manga.id)
            .fragment("$mangaPagePrefix${getMangaUrl(manga)}")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response) =
        response.parseAs<WrapperDto<MangaDto>>().results.toSManga()

    private val SManga.id: String get() {
        val mangaUrl = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments(url)
            .build()
        return mangaUrl.pathSegments[2]
    }

    // ============================= Chapters =================================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<WrapperDto<WrapperChapterDto>>().results.chapters.map {
            SChapter.create().apply {
                name = it.name
                it.chapterNumber?.let {
                    chapter_number = it
                }
                val chapterApiUrl = apiUrl.toHttpUrl().newBuilder()
                    .addEncodedPathSegments(chapterUrl!!)
                    .addPathSegment(it.id.toString())
                    .build()
                setUrlWithoutDomain(chapterApiUrl.toString())
                date_upload = it.updateAt.toDate()
            }
        }.sortedBy(SChapter::chapter_number).reversed()
    }

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter): Request {
        return super.pageListRequest(chapter).let { request ->
            val url = request.url.newBuilder()
                .fragment("$pageImagePrefix${chapter.url}")
                .build()

            request.newBuilder()
                .url(url)
                .build()
        }
    }

    private var pageUrl: String? = null

    override fun pageListParse(response: Response): List<Page> {
        pageUrl = pageUrl ?: findPageUrl(response)
        val chapterPageId = response.request.url.pathSegments.last()

        val chapterUrl = response.request.url.fragment
            ?.substringAfter(pageImagePrefix)
            ?: throw Exception("Não foi possivel carregar as páginas")

        val url = apiUrl.toHttpUrl().newBuilder()
            .addEncodedPathSegments(pageUrl!!)
            .addPathSegment(chapterPageId)
            .fragment(
                "$chapterPagePrefix${"$baseUrl$chapterUrl"}",
            )
            .build()

        val res = client.newCall(GET(url, headers)).execute()

        val dto = res.parseAs<WrapperDto<ChapterPageDto>>().results
        return dto.pages.mapIndexed { index, image ->
            val imageUrl = CDN_URL.toHttpUrl().newBuilder()
                .addPathSegments("wp-content/uploads/WP-manga/data")
                .addPathSegments(image.src.toPathSegment())
                .build().toString()
            Page(index, imageUrl = imageUrl)
        }
    }

    /**
     * Get the “dynamic” path segment of the chapter page
     */
    private fun findPageUrl(response: Response): String {
        val document = response.asJsoup()
        val scriptUrl = document.select("script[src]")
            .map { it.absUrl("src") }
            .firstOrNull { it.contains("app/capitulo", ignoreCase = true) }
            ?: throw IOException("Não foi possivel encontrar a URL da página")

        return client.newCall(GET(scriptUrl, headers)).execute().use {
            pageUrlRegex.find(it.body.string())?.groups?.get(1)?.value?.toPathSegment()
        } ?: throw IOException("Não foi possivel extrair a URL da página")
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================= Interceptors =================================

    private var chapterPageHeaders: Headers? = null

    private var chapterUrl: String? = null

    private fun findApiUrl(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response: Response = try {
            chain.proceed(request)
        } catch (ex: SocketTimeoutException) {
            chain.createTimeoutResponse(request)
        }

        if (request.url.toString().contains(apiUrl).not()) {
            return response
        }

        if (response.isSuccessful) {
            return response
        }

        response.close()

        fetchApiUrl(chain).forEach { urlCandidate ->
            val url = request.url.toString()
                .replace(apiUrl, urlCandidate)
                .toHttpUrl()

            val newRequest = request.newBuilder()
                .url(url)
                .build()

            return chain.proceed(newRequest).takeIf(Response::isSuccessful).also {
                apiUrl = preferences.prefApiUrlUpSet(urlCandidate)
            } ?: return@forEach
        }

        throw IOException(
            buildString {
                append("Não foi possível encontrar a URL da API.")
                append("Troque manualmente nas configurações da extensão")
            },
        )
    }
    private fun Interceptor.Chain.createTimeoutResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message("")
            .code(HTTP_BAD_GATEWAY)
            .build()
    }

    private fun fetchApiUrl(chain: Interceptor.Chain): List<String> {
        val scripts = chain.proceed(GET(baseUrl, headers)).asJsoup()
            .select("script[src*=next]:not([nomodule]):not([src*=app])")

        val script = getScriptBodyWithUrls(scripts, chain)
            ?: throw Exception("Não foi possivel localizar a URL da API")

        return apiUrlRegex.findAll(script)
            .flatMap { stringsRegex.findAll(it.value).map { match -> match.groupValues[1] } }
            .filter(urlRegex::containsMatchIn)
            .toList()
    }

    private fun getScriptBodyWithUrls(scripts: Elements, chain: Interceptor.Chain): String? {
        val elements = scripts.toList().reversed()
        for (element in elements) {
            val scriptUrl = element.absUrl("src")
            val script = chain.proceed(GET(scriptUrl, headers)).body.string()
            if (apiUrlRegex.containsMatchIn(script)) {
                return script
            }
        }
        return null
    }

    /**
     * Get the “dynamic” path segment of the chapter list
     */
    private fun findChapterUrl(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val mangaUrl = request.url.fragment
            ?.takeIf {
                it.contains(mangaPagePrefix, ignoreCase = true) && chapterUrl.isNullOrBlank()
            }?.substringAfter(mangaPagePrefix)
            ?: return chain.proceed(request)

        val document = chain.proceed(GET(mangaUrl, headers)).asJsoup()

        val scriptUrl = document.select("script[src]")
            .map { it.absUrl("src") }
            .firstOrNull { it.contains("app/obra", ignoreCase = true) }
            ?: throw IOException("Não foi possivel encontrar a URL do capitulo")

        chapterUrl = chain.proceed(GET(scriptUrl, headers)).use { response ->
            response.body.string().let {
                chapterUrlRegex.find(it)?.groups?.get(1)?.value?.toPathSegment()
            } ?: throw IOException("Não foi possivel extrair a URL do capitulo")
        }

        return chain.proceed(request)
    }

    private fun imageLocation(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.isSuccessful) {
            return response
        }

        val url = request.url.toString()
        if (url.contains(CDN_URL, ignoreCase = true)) {
            response.close()

            val newRequest = request.newBuilder()
                .url(url.replace(CDN_URL, OLDI_URL, ignoreCase = true))
                .build()

            return chain.proceed(newRequest)
        }
        return response
    }

    /**
     * Resolve the “dynamic” headers of the chapter page
     */
    private fun chapterPages(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapterUrl = request.url.fragment
            ?.takeIf { it.contains(chapterPagePrefix) }
            ?.substringAfter(chapterPagePrefix)?.toHttpUrl()?.newBuilder()?.fragment(null)
            ?.build()
            ?: return chain.proceed(request)

        val originUrl = request.url.newBuilder()
            .fragment(null)
            .build()

        val newRequest = request.newBuilder()
            .url(originUrl)

        chapterPageHeaders?.let { headers ->
            newRequest.headers(headers)
            val response = chain.proceed(newRequest.build())
            if (response.isSuccessful) {
                return response
            }
            response.close()
        }

        val chapterPageRequest = request.newBuilder()
            .url(chapterUrl)
            .build()

        return chain.proceed(fetchChapterPagesHeaders(chapterPageRequest, newRequest.build()))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchChapterPagesHeaders(baseRequest: Request, originRequest: Request): Request {
        val latch = CountDownLatch(1)
        val headers = originRequest.headers.newBuilder()
        var webView: WebView? = null
        val looper = Handler(Looper.getMainLooper())
        looper.post {
            webView = WebView(Injekt.get<Application>())
            webView?.let {
                with(it.settings) {
                    javaScriptEnabled = true
                    blockNetworkImage = true
                }
            }
            webView?.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val ignore = listOf(".css", "google", "fonts", "ads")
                    val url = request.url.toString()
                    if (ignore.any { url.contains(it, ignoreCase = true) }) {
                        return emptyResource()
                    }
                    if (request.isOriginRequest() && request.method.equals("GET", true)) {
                        headers.fill(request.requestHeaders)
                        latch.countDown()
                    }
                    return super.shouldInterceptRequest(view, request)
                }
                private fun WebResourceRequest.isOriginRequest() =
                    originRequest.url.toString().equals(this.url.toString(), ignoreCase = true)

                private fun emptyResource() = WebResourceResponse(null, null, null)
            }
            webView?.loadUrl(baseRequest.url.toString(), headers.build().toMap())
        }

        latch.await(client.readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)

        looper.post {
            webView?.run {
                stopLoading()
                destroy()
            }
        }

        chapterPageHeaders = headers.build()

        return originRequest.newBuilder()
            .headers(chapterPageHeaders!!)
            .build()
    }

    private fun Headers.Builder.fill(from: Map<String, String>): Headers.Builder {
        return from.entries.fold(this) { builder, entry ->
            builder.set(entry.key, entry.value)
        }
    }

    // ============================= Settings ====================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val fields = listOf(
            EditTextPreference(screen.context).apply {
                key = BASE_URL_PREF
                title = BASE_URL_PREF_TITLE
                summary = URL_PREF_SUMMARY

                dialogTitle = BASE_URL_PREF_TITLE
                dialogMessage = "URL padrão:\n$defaultBaseUrl"

                setDefaultValue(defaultBaseUrl)
                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                    true
                }
            },
            EditTextPreference(screen.context).apply {
                key = API_BASE_URL_PREF
                title = API_BASE_URL_PREF_TITLE
                summary = buildString {
                    append("Se não souber como verificar a URL da API, ")
                    append("busque suporte no Discord do repositório de extensões.")
                    appendLine(URL_PREF_SUMMARY)
                    append("\n⚠ A fonte não oferece suporte para essa extensão.")
                }

                dialogTitle = BASE_URL_PREF_TITLE
                dialogMessage = "URL da API padrão:\n$defaultApiUrl"

                setDefaultValue(defaultApiUrl)
                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                    true
                }
            },
        )

        fields.forEach(screen::addPreference)
    }

    // ============================= Utilities ====================================

    private fun MangaDto.toSManga(): SManga {
        val sManga = SManga.create().apply {
            title = name
            thumbnail_url = thumbnail
            initialized = true
            val mangaUrl = "$baseUrl/obra".toHttpUrl().newBuilder()
                .addPathSegment(this@toSManga.id.toString())
                .addPathSegment(this@toSManga.slug!!)
                .build()
            setUrlWithoutDomain(mangaUrl.toString())
        }

        Jsoup.parseBodyFragment(description).let { sManga.description = it.text() }
        sManga.status = status.toStatus()

        return sManga
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromStream(body.byteStream())
    }

    private fun String.toDate() =
        try { dateFormat.parse(this)!!.time } catch (_: Exception) { 0L }

    /**
     * Normalizes path segments:
     * Ex: [ "/a/b/", "/a/b", "a/b/", "a/b" ]
     * Result: "a/b"
     */
    private fun String.toPathSegment() = this.trim().split("/")
        .filter(String::isNotEmpty)
        .joinToString("/")

    companion object {
        const val CDN_URL = "https://cdn.sussytoons.site"
        const val OLDI_URL = "https://oldi.sussytoons.site"
        const val mangaPagePrefix = "mangaPage:"
        const val chapterPagePrefix = "chapterPage:"
        const val pageImagePrefix = "pageImage:"

        private const val URL_PREF_SUMMARY = "Para uso temporário, se a extensão for atualizada, a alteração será perdida."

        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Editar URL da fonte"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Reinicie o aplicativo para aplicar as alterações"

        private const val API_BASE_URL_PREF = "overrideApiUrl"
        private const val API_BASE_URL_PREF_TITLE = "Editar URL da API da fonte"
        private const val API_DEFAULT_BASE_URL_PREF = "defaultApiUrl"

        val chapterUrlRegex = """push\("([^"]*capitulo[^"]*)/?"\.concat""".toRegex()
        val pageUrlRegex = """\.get\("([^"]*capitulo[^(/?")]*)/?"\.concat""".toRegex()

        val apiUrlRegex = """(?<=production",)(.*?)(?=;function)""".toRegex()
        val urlRegex = """https?://[\w\-]+(\.[\w\-]+)+[/#?]?.*$""".toRegex()
        val stringsRegex = """"(.*?)"""".toRegex()

        @SuppressLint("SimpleDateFormat")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
