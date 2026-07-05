package eu.kanade.tachiyomi.extension.en.vizshonenjump

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Viz : HttpSource() {

    private val servicePath: String
        get() = if (name.contains("Shonen Jump")) "shonenjump" else "vizmanga"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::headersIntercept)
        .addInterceptor(::authCheckIntercept)
        .addInterceptor(::authChapterCheckIntercept)
        .addInterceptor(VizImageInterceptor())
        .rateLimit(1, 1.seconds)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private var loggedIn: Boolean? = null

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()

        return GET(
            url = "$baseUrl/read/$servicePath/section/free-chapters",
            headers = newHeaders,
            cache = CacheControl.FORCE_NETWORK,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (!response.request.url.toString().contains("section/free-chapters")) {
            throw Exception(COUNTRY_NOT_SUPPORTED)
        }

        val document = response.asJsoup()

        val mangas = document.select("section.section_chapters div.o_sort_container div.o_sortable > a.o_chapters-link").map { element ->
            SManga.create().apply {
                title = element.selectFirst("div.pad-x-rg")!!.text()
                thumbnail_url = element.selectFirst("div.pos-r img.disp-bl")?.attr("data-original")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }

        return MangasPage(mangas.sortedBy { it.title }, false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull() ?: throw Exception("Unsupported url")
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported url")
            }
            val pathSegments = url.pathSegments
            if (pathSegments.size < 3) {
                throw Exception("Unsupported url")
            }
            val seriesSlug = if (pathSegments[2] == "chapter" && pathSegments[1].contains("-chapter-")) {
                pathSegments[1].substringBeforeLast("-chapter-")
            } else {
                pathSegments[2]
            }
            return fetchSearchManga(page, "$PREFIX_URL_SEARCH/${pathSegments[0]}/chapters/$seriesSlug", filters)
        }
        if (query.startsWith(PREFIX_URL_SEARCH)) {
            val url = query.substringAfter(PREFIX_URL_SEARCH)
            val service = url.split("/")[1]
            if (service != servicePath) return Observable.just(MangasPage(emptyList(), false))
            return fetchMangaDetails(
                SManga.create().apply {
                    this.url = "/$url"
                    this.title = ""
                    this.initialized = false
                },
            ).map { MangasPage(listOf(it), false) }
        }
        return super.fetchSearchManga(page, query, filters)
            .map {
                val filteredMangas = it.mangas.filter { m -> m.title.contains(query, true) }
                MangasPage(filteredMangas, it.hasNextPage)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val seriesIntro = document.selectFirst("section#series-intro")
            ?: throw Exception("Series intro not found")

        val mangaUrl = document.location().substringAfter(baseUrl)

        return SManga.create().apply {
            author = seriesIntro.selectFirst("div.type-rg span")?.text()
                ?.replace("Created by ", "")
            artist = author
            status = SManga.ONGOING
            description = seriesIntro.selectFirst("div.line-solid")?.text()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("section.section_chapters td a > img")?.attr("data-original")
                ?: ""
            url = mangaUrl
            title = seriesIntro.selectFirst("h2.type-lg")?.text() ?: ""
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select("section.section_chapters div.o_sortable > a.o_chapter-container, section.section_chapters div.o_sortable div.o_chapter-vol-container tr.o_chapter a.o_chapter-container")

        val allChapters = elements.mapNotNull { element ->
            val urlStr = element.attr("data-target-url")
            if (urlStr.isBlank()) return@mapNotNull null

            SChapter.create().apply {
                val isVolume = element.selectFirst("div:nth-child(1) table") == null

                if (isVolume) {
                    name = element.text()
                } else {
                    val leftSide = element.selectFirst("div:nth-child(1) table")!!
                    val rightSide = element.selectFirst("div:nth-child(2) table")!!

                    name = rightSide.selectFirst("td")!!.text()
                    val dateStr = leftSide.selectFirst("td[align=right]")!!.text()
                    date_upload = DATE_FORMATTER.tryParse(dateStr)
                }

                chapter_number = name.substringAfter("Ch. ").toFloatOrNull() ?: -1F
                scanlator = "VIZ Media"
                url = if (urlStr.startsWith("javascript")) {
                    urlStr.substringAfter("'").substringBeforeLast("'") + "&locked=true"
                } else {
                    urlStr
                }
            }
        }

        return allChapters.sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaUrl = chapter.url
            .substringBefore("-chapter")
            .replace("$servicePath/", "$servicePath/chapters/")

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + mangaUrl)
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pageCount = document.selectFirst("script:containsData(var pages)")?.data()
            ?.substringAfter("= ")
            ?.substringBefore(";")
            ?.toInt() ?: 0
        val mangaId = document.location()
            .substringAfterLast("/")
            .substringBefore("?")

        return IntRange(0, pageCount).map {
            val imageUrl = "$baseUrl/manga/get_manga_url".toHttpUrl().newBuilder()
                .addQueryParameter("device_id", "3")
                .addQueryParameter("manga_id", mangaId)
                .addQueryParameter("pages", it.toString())
                .toString()

            Page(it, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .add("X-Client-Login", (loggedIn ?: false).toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    // ============================= Utilities =============================

    private fun checkIfIsLoggedIn() {
        val refreshHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val loginCheckRequest = GET("$baseUrl/$REFRESH_LOGIN_LINKS_URL", refreshHeaders)
        try {
            network.client.newCall(loginCheckRequest).execute().use { response ->
                val document = response.asJsoup()
                loggedIn = document.selectFirst("div#o_account-links-content")
                    ?.attr("logged_in")?.toBoolean() ?: false
            }
        } catch (_: Exception) {
            loggedIn = false
        }
    }

    private fun headersIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = request.headers.newBuilder()
            .removeAll("Accept-Encoding")
            .build()
        return chain.proceed(request.newBuilder().headers(headers).build())
    }

    private fun authCheckIntercept(chain: Interceptor.Chain): Response {
        if (loggedIn == null) {
            checkIfIsLoggedIn()
        }

        return chain.proceed(chain.request())
    }

    private fun authChapterCheckIntercept(chain: Interceptor.Chain): Response {
        val requestUrl = chain.request().url.toString()

        if (!requestUrl.contains("/chapter/") || !requestUrl.contains("&locked=true")) {
            return chain.proceed(chain.request())
        }

        val mangaId = requestUrl.substringAfterLast("/").substringBefore("?")

        val authCheckHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .add("X-Client-Login", (loggedIn ?: false).toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val authCheckUrl = "$baseUrl/$MANGA_AUTH_CHECK_URL".toHttpUrl().newBuilder()
            .addQueryParameter("device_id", "3")
            .addQueryParameter("manga_id", mangaId)
            .toString()
        val authCheckRequest = GET(authCheckUrl, authCheckHeaders)

        val authCheckResponse = chain.proceed(authCheckRequest).parseAs<MangaAuthDto>()

        if (authCheckResponse.ok == 1 && authCheckResponse.archiveInfo?.ok == 1) {
            val newChapterUrl = chain.request().url.newBuilder()
                .removeAllQueryParameters("locked")
                .build()
            val newChapterRequest = chain.request().newBuilder()
                .url(newChapterUrl)
                .build()

            return chain.proceed(newChapterRequest)
        }

        if (authCheckResponse.archiveInfo?.error?.code == 4) {
            throw IOException(SESSION_EXPIRED)
        }

        throw IOException(authCheckResponse.archiveInfo?.error?.message ?: AUTH_CHECK_FAILED)
    }

    companion object {
        private const val ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01"

        private val DATE_FORMATTER = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

        private const val COUNTRY_NOT_SUPPORTED = "Your country is not supported by the service."
        private const val SESSION_EXPIRED = "Your session has expired, please log in through WebView again."
        private const val AUTH_CHECK_FAILED = "Something went wrong in the auth check."

        private const val REFRESH_LOGIN_LINKS_URL = "account/refresh_login_links"
        private const val MANGA_AUTH_CHECK_URL = "manga/auth"

        const val PREFIX_URL_SEARCH = "url:"
    }
}
