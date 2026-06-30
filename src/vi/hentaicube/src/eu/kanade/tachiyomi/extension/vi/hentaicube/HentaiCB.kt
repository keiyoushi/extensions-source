package eu.kanade.tachiyomi.extension.vi.hentaicube

import android.content.SharedPreferences
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HentaiCB : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi"))

    override val client: OkHttpClient = network.client.newBuilder()
        .followRedirects(false)
        .addInterceptor { chain ->
            val maxRedirects = 5
            var request = chain.request()
            var response = chain.proceed(request)
            var redirectCount = 0

            while (response.isRedirect && redirectCount < maxRedirects) {
                val newUrl = response.header("Location") ?: break
                val newUrlHttp = newUrl.toHttpUrl()
                val redirectedDomain = newUrlHttp.run { "$scheme://$host" }
                if (redirectedDomain != baseUrl) {
                    synchronized(prefsLock) {
                        preferences.edit().putString(BASE_URL_PREF, redirectedDomain).commit()
                    }
                }
                response.close()
                request = request.newBuilder()
                    .url(newUrlHttp)
                    .build()
                response = chain.proceed(request)
                redirectCount++
            }
            if (redirectCount >= maxRedirects) {
                response.close()
                throw java.io.IOException("Too many redirects: $maxRedirects")
            }
            response
        }
        .rateLimit(3)
        .build()

    private val preferences: SharedPreferences = getPreferences()
    private val prefsLock = Any()

    override val filterNonMangaItems = false

    override val mangaSubString = "read"

    override val altNameSelector = ".post-content_item:contains(Tên khác) .summary-content"

    private val thumbnailOriginalUrlRegex = Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$")

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).apply {
        val img = element.selectFirst("img")
        thumbnail_url = imageFromElement(img!!)?.replace(thumbnailOriginalUrlRegex, "$1")
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val mangaUrl = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment(mangaSubString)
                addPathSegment(query.substringAfter(URL_SEARCH_PREFIX))
                addPathSegment("")
            }.build()
            return client.newCall(GET(mangaUrl, headers))
                .asObservableSuccess().map { response ->
                    val manga = mangaDetailsParse(response).apply {
                        setUrlWithoutDomain(mangaUrl.toString())
                        initialized = true
                    }

                    MangasPage(listOf(manga), false)
                }
        }

        // Special characters causing search to fail
        val queryFixed = query
            .replace("–", "-")
            .replace("’", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("…", "...")

        return super.fetchSearchManga(page, queryFixed, filters)
    }

    private val oldMangaUrlRegex by lazy { Regex("^$baseUrl/\\w+/") }

    // Change old entries from mangaSubString
    override fun getMangaUrl(manga: SManga): String = super.getMangaUrl(manga)
        .replace(oldMangaUrlRegex, "$baseUrl/$mangaSubString/")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty() && !chaptersWrapper.isNullOrEmpty()) {
            val mangaUrl = document.location().removeSuffix("/")
            val mangaId = chaptersWrapper.attr("data-id")

            val allChapters = Elements()
            var page = 1

            while (true) {
                val xhrRequest = xhrChaptersRequest(mangaUrl, page)
                var xhrResponse = client.newCall(xhrRequest).execute()

                // Newer Madara versions throws HTTP 400 when using the old endpoint.
                if (xhrResponse.code == 400 && page == 1) {
                    xhrResponse.close()
                    val oldRequest = oldXhrChaptersRequest(mangaId)
                    xhrResponse = client.newCall(oldRequest).execute()
                }

                val xhrDocument = xhrResponse.asJsoup()
                allChapters.addAll(xhrDocument.select(chapterListSelector()))

                val hasNextPage = xhrDocument.selectFirst("div.pagination a[data-page='${page + 1}']") != null
                xhrResponse.close()

                if (!hasNextPage) {
                    break
                }
                page++
            }
            chapterElements = allChapters
        }

        return chapterElements.map(::chapterFromElement)
    }

    private fun xhrChaptersRequest(mangaUrl: String, page: Int): Request {
        val request = xhrChaptersRequest(mangaUrl)
        if (page <= 1) return request

        val url = request.url.newBuilder()
            .addQueryParameter("t", page.toString())
            .build()

        return request.newBuilder().url(url).build()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        fetchPageListApi(chapter)
    }

    private fun fetchPageListApi(chapter: SChapter): List<Page> {
        val chapterUrl = chapter.url
        val originUrl = chapterUrl.toHttpUrl().newBuilder()
            .scheme("https")
            .host(baseUrl.toHttpUrl().host)
            .encodedPath("/")
            .build()

        // Build cookies string with cf_clearance from cookie jar
        val cookies = client.cookieJar.loadForRequest(originUrl)
            .joinToString("; ") { "${it.name}=${it.value}" }

        val referer = chapterUrl

        // Step 1: Get nonce + session from challenge endpoint
        val challengeUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("wp-json/manga-reader/v1/challenge")
            .build()

        val challengeRequest = Request.Builder()
            .url(challengeUrl)
            .header("Referer", referer)
            .header("Cookie", cookies)
            .build()

        val challengeResponse = client.newCall(challengeRequest).execute()
        val challengeJson = JSONObject(challengeResponse.body?.string().orEmpty())
        challengeResponse.close()
        val nonce = challengeJson.getString("nonce")
        val session = challengeJson.getString("session")

        // Step 2: Paginate images
        val allImages = mutableListOf<String>()
        var offset = 0
        var totalCount = Int.MAX_VALUE

        while (offset < totalCount) {
            val imagesUrl = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("wp-json/manga-reader/v1/images")
                .addQueryParameter("offset", offset.toString())
                .build()

            val imagesRequest = Request.Builder()
                .url(imagesUrl)
                .header("Referer", referer)
                .header("Cookie", cookies)
                .header("x-masr-nonce", nonce)
                .header("x-masr-session", session)
                .build()

            val imagesResponse = client.newCall(imagesRequest).execute()
            val imagesJson = JSONObject(imagesResponse.body?.string().orEmpty())
            imagesResponse.close()

            totalCount = imagesJson.optInt("count", 0)
            val imagesArray = imagesJson.optJSONArray("images") ?: break
            if (imagesArray.length() == 0) break

            for (i in 0 until imagesArray.length()) {
                allImages.add(imagesArray.getString(i))
            }

            offset = imagesJson.optInt("next", -1)
            if (offset <= 0 || offset >= totalCount) break
        }

        return allImages.mapIndexed { i, imageUrl ->
            Page(i, chapterUrl, imageUrl)
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
    }
}
