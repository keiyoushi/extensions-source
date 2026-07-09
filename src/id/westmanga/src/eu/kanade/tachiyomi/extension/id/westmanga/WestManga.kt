package eu.kanade.tachiyomi.extension.id.westmanga

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Source
abstract class WestManga : HttpSource() {
    private val apiUrl = "https://data.mantweh.online"
    override val supportsLatest = true

    private var genres: List<Pair<String, String>> = emptyList()
    private var genresFetchJob: Boolean = false

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/contents".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "20")
            addQueryParameter("type", "Comic")
            filters.filterIsInstance<UrlFilter>().forEach {
                it.addToUrl(this)
            }
        }.build()

        return apiRequest(url)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<PaginatedData<BrowseManga>>()
        val entries = data.data.map { it.toSManga() }
        return MangasPage(entries, data.paginator.hasNextPage())
    }

    override fun getFilterList(): FilterList {
        fetchGenres()

        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            StatusFilter(),
            CountryFilter(),
            ColorFilter(),
        )

        if (genres.isEmpty()) {
            filters.add(Filter.Header("Klik pada 'Atur ulang' untuk memuat ulang genre"))
        } else {
            filters.add(GenreFilter(genres))
        }

        return FilterList(filters)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        require(path.size == 3) { "Migrate from $name to $name" }
        val slug = path[1]

        return apiRequest("$apiUrl/api/comic/$slug".toHttpUrl())
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]
        return "$baseUrl/comic/$slug"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<Data<Manga>>().data
        return data.toSManga(baseUrl)
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Data<Manga>>().data
        return data.chapters.map { it.toSChapter() }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val path = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        require(path.isNotEmpty()) { "Refresh Chapter List" }
        val slug = path.first()

        return apiRequest("$apiUrl/api/v/$slug".toHttpUrl())
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.first()
        return "$baseUrl/view/$slug"
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<Data<ImageList>>().data

        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun fetchGenres() {
        if (genres.isNotEmpty() || genresFetchJob) return
        genresFetchJob = true

        val url = "$apiUrl/api/contents/genres".toHttpUrl()
        val request = apiRequest(url, true)
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                genresFetchJob = false
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use { res ->
                    runCatching {
                        if (res.isSuccessful) {
                            val apiGenres = res.parseAs<Data<List<ApiGenre>>>().data
                            genres = apiGenres.map { it.name to it.id.toString() }
                        }
                    }.onFailure {
                        genresFetchJob = false
                    }
                }
            }
        })
    }

    private var tokenCache: String? = null

    val bearerToken: String?
        get() {
            if (tokenCache != null) return tokenCache

            val handler = Handler(Looper.getMainLooper())
            val latch = CountDownLatch(1)

            handler.post {
                val webview = WebView(applicationContext)
                with(webview.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }

                webview.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("localStorage.getItem('access_token')") {
                            tokenCache = it.takeIf { it != "null" }?.trim('"') ?: "" // don't check again
                            latch.countDown()
                            webview.destroy()
                        }
                    }
                }
                webview.loadDataWithBaseURL(baseUrl, " ", "text/html", "utf-8", null)
            }
            latch.await(8, TimeUnit.SECONDS)
            return tokenCache
        }

    private fun apiRequest(url: HttpUrl, isGenre: Boolean = false): Request {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val message = "wm-api-request"
        val key = timestamp + "GET" + url.encodedPath + ACCESS_KEY + SECRET_KEY
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val hash = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val signature = hash.joinToString("") { "%02x".format(it) }

        val apiHeaders = headersBuilder().apply {
            if (!isGenre) {
                bearerToken?.takeUnless { it.isEmpty() }?.let { set("Authorization", "Bearer $it") }
            }
            set("x-wm-request-time", timestamp)
            set("x-wm-accses-key", ACCESS_KEY)
            set("x-wm-request-signature", signature)
        }.build()

        return GET(url, apiHeaders)
    }
}

private const val ACCESS_KEY = "WM_WEB_FRONT_END"
private const val SECRET_KEY = "xxxoidj"
