package eu.kanade.tachiyomi.extension.en.vizshonenjump

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

open class Viz(
    final override val name: String,
    private val servicePath: String,
) : ParsedHttpSource() {

    override val baseUrl = "https://www.viz.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::headersIntercept)
        .addInterceptor(::authCheckIntercept)
        .addInterceptor(::authChapterCheckIntercept)
        .addInterceptor(VizImageInterceptor())
        .rateLimit(1, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/$servicePath")

    private val json: Json by injectLazy()

    private var mangaList: List<SManga>? = null

    private var loggedIn: Boolean? = null

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl)
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

        val mangasPage = super.popularMangaParse(response)

        mangaList = mangasPage.mangas.sortedBy { it.title }

        return mangasPage
    }

    override fun popularMangaSelector(): String =
        "section.section_chapters div.o_sort_container div.o_sortable > a.o_chapters-link"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.pad-x-rg")!!.text()
        thumbnail_url = element.selectFirst("div.pos-r img.disp-bl")
            ?.attr("data-original")
        url = element.attr("href")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (!response.request.url.toString().contains("section/free-chapters")) {
            throw Exception(COUNTRY_NOT_SUPPORTED)
        }

        val mangasPage = super.latestUpdatesParse(response)

        mangaList = mangasPage.mangas.sortedBy { it.title }

        return mangasPage
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_URL_SEARCH)) {
            val url = query.substringAfter(PREFIX_URL_SEARCH)
            val service = url.split("/")[1]
            if (service != servicePath) return Observable.just(MangasPage(emptyList(), false))
            return fetchMangaDetails(
                SManga.create().apply {
                    this.url = url
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.request.url.toString().contains("section/free-chapters")) {
            throw Exception(COUNTRY_NOT_SUPPORTED)
        }

        return super.searchMangaParse(response)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val seriesIntro = document.select("section#series-intro").first()!!

        // Get the thumbnail url from the manga list (if available),
        // or fetch it for the first time (in backup restore, for example).
        if (mangaList == null) {
            val request = popularMangaRequest(1)
            val response = client.newCall(request).execute()
            // Call popularMangaParse to fill the manga list.
            popularMangaParse(response)
        }

        val mangaUrl = document.location().substringAfter(baseUrl)
        val mangaFromList = mangaList!!.firstOrNull { it.url == mangaUrl }

        return SManga.create().apply {
            author = seriesIntro.select("div.type-rg span").firstOrNull()?.text()
                ?.replace("Created by ", "")
            artist = author
            status = SManga.ONGOING
            description = seriesIntro.select("div.line-solid").firstOrNull()?.text()
            thumbnail_url = if (!mangaFromList?.thumbnail_url.isNullOrEmpty()) {
                mangaFromList!!.thumbnail_url // Can't be null in this branch
            } else {
                document.selectFirst("section.section_chapters td a > img")?.attr("data-original") ?: ""
            }
            url = mangaUrl
            title = if (!mangaFromList?.title.isNullOrEmpty()) {
                mangaFromList!!.title // Can't be null in this branch
            } else {
                seriesIntro.selectFirst("h2.type-lg")?.text() ?: ""
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = super.chapterListParse(response)

        checkIfIsLoggedIn()

        if (loggedIn == true) {
            return allChapters.map { oldChapter ->
                oldChapter.apply {
                    url = url.substringAfter("'").substringBeforeLast("'") + "&locked=true"
                }
            }
        }

        return allChapters.filter { !it.url.startsWith("javascript") }
            .sortedByDescending { it.chapter_number }
    }

    override fun chapterListSelector() =
        "section.section_chapters div.o_sortable > a.o_chapter-container, " +
            "section.section_chapters div.o_sortable div.o_chapter-vol-container tr.o_chapter a.o_chapter-container"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val isVolume = element.select("div:nth-child(1) table").first() == null

        if (isVolume) {
            name = element.text()
        } else {
            val leftSide = element.select("div:nth-child(1) table").first()!!
            val rightSide = element.select("div:nth-child(2) table").first()!!

            name = rightSide.select("td").first()!!.text()
            date_upload = leftSide.select("td[align=right]").first()!!.text().toDate()
        }

        chapter_number = name.substringAfter("Ch. ").toFloatOrNull() ?: -1F
        scanlator = "VIZ Media"
        url = element.attr("data-target-url")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaUrl = chapter.url
            .substringBefore("-chapter")
            .replace("$servicePath/", "$servicePath/chapters/")

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + mangaUrl)
            .build()

        return GET(baseUrl + chapter.url, newHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageCount = document.select("script:containsData(var pages)").first()!!.data()
            .substringAfter("= ")
            .substringBefore(";")
            .toInt()
        val mangaId = document.location()
            .substringAfterLast("/")
            .substringBefore("?")

        return IntRange(0, pageCount).map {
            val imageUrl = "$baseUrl/manga/get_manga_url".toHttpUrl().newBuilder()
                .addQueryParameter("device_id", "3")
                .addQueryParameter("manga_id", mangaId)
                .addQueryParameter("pages", it.toString())
                .toString()

            // The image URL is actually fetched in the interceptor to avoid the short
            // time expiration it have. Using the interceptor will guarantee the requests
            // always follow the expected order, even when downloading:
            // imageUrlRequest -> imageRequest -> decryption
            // By using the url field of page, while downloading through the app it will
            // do a batch call to get all imageUrl's first and then starts downloading it,
            // but this takes time and the imageUrl's will be already expired. The reader
            // doesn't face this issue as it follows the expected request order.
            Page(it, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", ACCEPT_JSON)
            .add("X-Client-Login", (loggedIn ?: false).toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun checkIfIsLoggedIn(chain: Interceptor.Chain? = null) {
        val refreshHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val loginCheckRequest = GET("$baseUrl/$REFRESH_LOGIN_LINKS_URL", refreshHeaders)
        val loginCheckResponse = chain?.proceed(loginCheckRequest)
            ?: client.newCall(loginCheckRequest).execute()
        val document = loginCheckResponse.asJsoup()

        loggedIn = document.select("div#o_account-links-content").firstOrNull()
            ?.attr("logged_in")?.toBoolean() ?: false

        loginCheckResponse.close()
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
            checkIfIsLoggedIn(chain)
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

        val authCheckUrl = "$baseUrl/$MANGA_AUTH_CHECK_URL".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("device_id", "3")
            .addQueryParameter("manga_id", mangaId)
            .toString()
        val authCheckRequest = GET(authCheckUrl, authCheckHeaders)
        val authCheckResponse = chain.proceed(authCheckRequest).parseAs<VizMangaAuthDto>()

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

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private const val ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
        }

        private const val COUNTRY_NOT_SUPPORTED = "Your country is not supported by the service."
        private const val SESSION_EXPIRED = "Your session has expired, please log in through WebView again."
        private const val AUTH_CHECK_FAILED = "Something went wrong in the auth check."

        private const val REFRESH_LOGIN_LINKS_URL = "account/refresh_login_links"
        private const val MANGA_AUTH_CHECK_URL = "manga/auth"

        const val PREFIX_URL_SEARCH = "url:"
    }
}
