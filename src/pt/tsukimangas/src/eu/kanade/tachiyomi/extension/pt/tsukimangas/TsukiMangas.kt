package eu.kanade.tachiyomi.extension.pt.tsukimangas

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.extension.pt.tsukimangas.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.pt.tsukimangas.dto.CompleteMangaDto
import eu.kanade.tachiyomi.extension.pt.tsukimangas.dto.MangaListDto
import eu.kanade.tachiyomi.extension.pt.tsukimangas.dto.PageListDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TsukiMangas : HttpSource() {

    override val name = "Tsuki Mangás"

    override val baseUrl = "https://tsuki-mangas.com"

    private val apiUrl = baseUrl + API_PATH

    override val lang = "pt-BR"

    override val supportsLatest = true

    @delegate:SuppressLint("SetJavaScriptEnabled")
    private val token: String by lazy {
        val latch = CountDownLatch(1)
        var token = ""
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(Injekt.get<Application>())
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val script = "javascript:localStorage['token']"
                    view.evaluateJavascript(script) {
                        view.apply {
                            stopLoading()
                            destroy()
                        }
                        if (it.isBlank() || it in listOf("null", "undefined")) {
                            return@evaluateJavascript
                        }
                        token = it.replace("[\"]+".toRegex(), "")
                        latch.countDown()
                    }
                }
            }
            webView.loadUrl(baseUrl)
        }
        latch.await(10, TimeUnit.SECONDS)

        token
    }

    override val client by lazy {
        network.client.newBuilder()
            .addInterceptor(::apiHeadersInterceptor)
            .addInterceptor(::imageCdnSwapper)
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .rateLimitHost(MAIN_CDN.toHttpUrl(), 1)
            .rateLimitHost(SECONDARY_CDN.toHttpUrl(), 1)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$apiUrl/mangas?page=$page&filter=0", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val item = response.parseAs<MangaListDto>()
        val mangas = item.data.map {
            SManga.create().apply {
                url = "/obra" + it.entryPath
                thumbnail_url = baseUrl + it.imagePath
                title = it.title
            }
        }
        val hasNextPage = item.page < item.lastPage
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    // Yes, "lastests". High IQ move.
    // Also yeah, there's a "?format=0" glued to the page number. Without this,
    // the request will blow up with a HTTP 500.
    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/home/lastests?page=$page%3Fformat%3D0", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$apiUrl/mangas/$id", headers))
                .asObservableSuccess()
                .map(::searchMangaByIdParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun getFilterList() = TsukiMangasFilters.FILTER_LIST

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val params = TsukiMangasFilters.getSearchParameters(filters)
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("title", query.trim())
            .addIfNotBlank("filter", params.filter)
            .addIfNotBlank("format", params.format)
            .addIfNotBlank("status", params.status)
            .addIfNotBlank("adult_content", params.adult)
            .apply {
                params.genres.forEach { addQueryParameter("genres[]", it) }
                params.tags.forEach { addQueryParameter("tags[]", it) }
            }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.getMangaId()
        return GET("$apiUrl/mangas/$id", headers)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val mangaDto = response.parseAs<CompleteMangaDto>()
        url = "/obra" + mangaDto.entryPath
        thumbnail_url = baseUrl + mangaDto.imagePath
        title = mangaDto.title
        artist = mangaDto.staff
        genre = mangaDto.genres.joinToString { it.genre }
        status = parseStatus(mangaDto.status.orEmpty())
        description = buildString {
            mangaDto.synopsis?.also { append("$it\n\n") }
            if (mangaDto.titles.isNotEmpty()) {
                append("Títulos alternativos: ${mangaDto.titles.joinToString { it.title }}")
            }
        }
    }

    private fun parseStatus(status: String) = when (status) {
        "Ativo" -> SManga.ONGOING
        "Completo" -> SManga.COMPLETED
        "Hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val split = manga.url.split("/").reversed()
        val slug = split[0]
        val id = split[1]

        return GET("$apiUrl/chapters/$id/all#$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val parsed = response.parseAs<ChapterListDto>()
        val mangaSlug = response.request.url.fragment!!
        val mangaId = response.request.url.pathSegments.reversed()[1]

        return parsed.chapters.reversed().map {
            SChapter.create().apply {
                name = "Capítulo ${it.number}"
                // Sometimes the "number" attribute have letters or other characters,
                // which could ruin the automatic chapter number recognition system.
                chapter_number = it.number.trim { char -> !char.isDigit() }.toFloatOrNull() ?: 1F

                url = "/leitor/$mangaId/${it.versionId}/$mangaSlug/${it.number}"

                date_upload = it.created_at.orEmpty().toDate()
            }
        }
    }

    // =============================== Pages ================================
    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val versionId = chapter.url.split("/")[3]

        return GET("$apiUrl/chapter/versions/$versionId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListDto>()
        val sortedPages = data.pages.sortedBy { it.url.extractPageNumber() }
        val host = getImageHost(sortedPages.first().url)

        return sortedPages.mapIndexed { index, item ->
            Page(index, imageUrl = host + item.url)
        }
    }

    /**
     * The source normally uses only one CDN per chapter, so we'll try to get
     * the correct CDN before loading all pages, leaving the [imageCdnSwapper]
     * as the last choice.
     */
    private fun getImageHost(path: String): String {
        val pageCheck = super.client.newCall(GET(MAIN_CDN + path, headers)).execute()
        pageCheck.close()
        return when {
            !pageCheck.isSuccessful -> SECONDARY_CDN
            else -> MAIN_CDN
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================
    private inline fun <reified T> Response.parseAs(): T = use {
        try {
            json.decodeFromStream(it.body.byteStream())
        } catch (_: Exception) {
            throw Exception(
                """
                    Contéudo protegido ou foi removido.
                    Faça o login na WebView e tente novamente
                """.trimIndent(),
            )
        }
    }

    private fun HttpUrl.Builder.addIfNotBlank(query: String, value: String): HttpUrl.Builder {
        if (value.isNotBlank()) addQueryParameter(query, value)
        return this
    }

    private fun String.getMangaId() = substringAfter("/obra/").substringBefore("/")

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?.time }
            .getOrNull() ?: 0L
    }

    private val pageNumberRegex = Regex("""(\d+)\.(png|jpg|jpeg|gif|webp)$""")

    private fun String.extractPageNumber() = pageNumberRegex
        .find(substringBefore("?"))
        ?.groupValues
        ?.get(1)
        ?.toInt() ?: 0

    /**
     * This may sound stupid (because it is), but a similar approach exists
     * in the source itself, because they somehow don't know to which server
     * each page belongs to. I thought the `server` attribute returned by page
     * objects would be enough, but it turns out that it isn't. Day ruined.
     */
    private fun imageCdnSwapper(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        return if (response.code != 404) {
            response
        } else {
            response.close()
            val url = request.url.toString()
            val newUrl = when {
                url.startsWith(MAIN_CDN) -> url.replace("$MAIN_CDN/tsuki", SECONDARY_CDN)
                url.startsWith(SECONDARY_CDN) -> url.replace(SECONDARY_CDN, "$MAIN_CDN/tsuki")
                else -> url
            }

            val newRequest = GET(newUrl, request.headers)
            chain.proceed(newRequest)
        }
    }

    private val apiHeadersRegex = Regex("""headers\.common(?:\.(?<key>[0-9A-Za-z_]+)|\[['"](?<key2>[0-9A-Za-z-_]+)['"]])\s*=\s*['"](?<value>[a-zA-Z0-9_ :;.,\\/?!(){}\[\]@<>=\-+*#$&`|~^%]+)['"]""")

    private val apiHeaders by lazy {
        val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        val scriptUrl = document.selectFirst("script[src*=index-]")!!.absUrl("src")
        val script = client.newCall(GET(scriptUrl, headers)).execute().body.string()
        val matches = apiHeadersRegex.findAll(script)

        matches.associate {
            (it.groups["key"] ?: it.groups["key2"]!!).value to it.groups["value"]!!.value
        }
    }

    private fun apiHeadersInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.url.encodedPath.startsWith(API_PATH)) {
            return chain.proceed(request)
        }

        val newRequest = request.newBuilder().apply {
            apiHeaders.entries.forEach { addHeader(it.key, it.value) }
            if (token.isNotBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()

        return chain.proceed(newRequest)
    }

    companion object {
        const val PREFIX_SEARCH = "id:"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        }

        private const val MAIN_CDN = "https://cdn.tsuki-mangas.com/tsuki"
        private const val SECONDARY_CDN = "https://cdn2.tsuki-mangas.com"
        private const val API_PATH = "/api/v3"
    }
}
