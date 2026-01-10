package eu.kanade.tachiyomi.extension.ja.unext

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UNext : HttpSource(), ConfigurableSource {
    override val name = "U-NEXT"
    private val domain = "unext.jp"
    override val baseUrl = "https://video.$domain"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "https://cc.$domain"
    private val preferences: SharedPreferences by getPreferencesLazy()

    // Paid chapters redirect to the app on mobile UA, but are readable with desktop UA
    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor { chain ->
            var request = chain.request()
            var response = chain.proceed(request)
            var retryCount = 0

            // API request for details sometimes gives "PersistedQueryNotFound" but works after 1-2 retries
            while (retryCount < 5 && request.url.host == "cc.unext.jp" && response.isSuccessful) {
                val contentType = response.body.contentType()
                if (contentType?.subtype == "json") {
                    val bodyString = response.peekBody(Long.MAX_VALUE).string()
                    if (bodyString.contains("PersistedQueryNotFound")) {
                        response.close()
                        retryCount++
                        request = request.newBuilder().build()
                        response = chain.proceed(request)
                        continue
                    }
                }
                break
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val payload = Payload(
            "cosmo_getBookRanking",
            PopularVariables("D_C_COMIC", page, 20),
            Payload.Extensions(
                Payload.Extensions.PersistedQuery(1, POPULAR_QUERY_HASH),
            ),
        )
        return apiRequest(payload)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<PopularResponse>().data.bookRanking
        val mangas = result.books.map { it.bookSakuhin.toSManga() }
        val hasNextPage = result.pageInfo.let { it.page * it.pageSize < it.results }
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val payload = Payload(
            "cosmo_getNewBooks",
            LatestVariables("TAG0000014500", page, 20),
            Payload.Extensions(
                Payload.Extensions.PersistedQuery(1, LATEST_QUERY_HASH),
            ),
        )
        return apiRequest(payload)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestResponse>().data.newBooks
        val mangas = result.books.map { it.toSManga() }
        val hasNextPage = result.pageInfo.let { it.page * it.pageSize < it.results }
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = Payload(
            "cosmo_bookFreewordSearch",
            SearchVariables(query, page, 20, null, "RECOMMEND"),
            Payload.Extensions(
                Payload.Extensions.PersistedQuery(1, SEARCH_QUERY_HASH),
            ),
        )
        return apiRequest(payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>().data.search
        val mangas = result.books.map { it.toSManga() }
        val hasNextPage = result.pageInfo.let { it.page * it.pageSize < it.results }
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val bookSakuhinCode = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        val payload = Payload(
            "cosmo_bookTitleDetail",
            DetailsVariables(bookSakuhinCode, "TOTAL", 2, 5),
            Payload.Extensions(
                Payload.Extensions.PersistedQuery(1, DETAILS_QUERY_HASH),
            ),
        )
        return apiRequest(payload)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<DetailsResponse>().data.bookTitle.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val bookSakuhinCode = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        val payload = Payload(
            "cosmo_bookTitleBooks",
            ChapterListVariables(bookSakuhinCode, 1, 9999),
            Payload.Extensions(
                Payload.Extensions.PersistedQuery(1, CHAPTER_LIST_QUERY_HASH),
            ),
        )
        return apiRequest(payload)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val variables = response.request.url.queryParameter("variables")!!
        val sakuhinCode = variables.parseAs<ChapterListVariables>().bookSakuhinCode
        val data = response.parseAs<ChapterListResponse>().data.bookTitleBooks.books
        val hidePaid = preferences.getBoolean(HIDE_PAID_PREF, false)
        return data
            .filterNot { hidePaid && it.isFree != true && it.isPurchased != true && it.rightsExpirationDatetime == null }
            .map { it.toSChapter(sakuhinCode) }
            .reversed()
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.fromCallable {
            val bookFileCode = (baseUrl + chapter.url).toHttpUrl().fragment

            val payload = Payload(
                "cosmo_getBookPlaylistUrl",
                PageListVariables(bookFileCode!!),
                Payload.Extensions(
                    Payload.Extensions.PersistedQuery(1, PLAYLIST_QUERY_HASH),
                ),
            )

            val apiRequest = apiRequest(payload)
            val playlistData = client.newCall(apiRequest).execute()
            val playlistParse = playlistData.parseAs<PlaylistResponse>().data?.playlistUrl
                ?: throw Exception("This chapter is locked. Log in via WebView and rent or purchase this chapter to read.")

            var keys: Map<String, String>? = null

            // Try multiple times to load all signed cookies
            for (i in 1..4) {
                try {
                    val viewerUrl = getChapterUrl(chapter)
                    keys = fetchKeys(viewerUrl)

                    if (keys != null) break
                } catch (e: Exception) {
                    if (i == 4) throw e
                    Thread.sleep(2000)
                }
            }

            if (keys == null) {
                throw Exception("Failed to fetch DRM keys. Try again.")
            }

            val base = playlistParse.playlistBaseUrl
            val ubookPath = playlistParse.playlistUrl.ubooks.first().content
            val zipUrl = "$base/$ubookPath".toHttpUrl().newBuilder().build().toString()

            val headRequest = Request.Builder().url(zipUrl).headers(headers).head().build()
            val contentLength = client.newCall(headRequest).execute().use {
                it.header("Content-Length")?.toBigInteger()
            } ?: throw Exception("Could not get Content-Length")

            val zipHandler = ZipHandler(zipUrl, client, headers, "zip", contentLength)
            val zip = zipHandler.populate()

            val indexJson = zip.fetch("index.json", client).toString(Charsets.UTF_8).parseAs<UBookIndex>()
            val drmJson = zip.fetch("drm.json", client).toString(Charsets.UTF_8).parseAs<UBookDrm>()

            indexJson.spine.mapIndexed { i, spine ->
                val pageInfo = indexJson.pages[spine.pageId]
                    ?: throw Exception("Page definition not found for ${spine.pageId}")

                val entryName = pageInfo.image.src
                val drmData = drmJson.encryptedFileList[entryName]
                    ?: throw Exception("DRM metadata not found for $entryName")

                val key = keys[drmData.keyId] ?: throw Exception("Decryption key not found for ${drmData.keyId}")
                val zipEntry = zip.getEntry(entryName) ?: throw Exception("Entry not found in CD: $entryName")

                val requestData = ImageRequestData(
                    zipUrl,
                    zip.zipStartOffset,
                    zipEntry.localFileHeaderRelativeOffset.toLong(),
                    zipEntry.compressedSize,
                    key,
                    drmData.iv,
                    drmData.originalFileSize,
                )

                val fragment = requestData.toJsonString()
                Page(i, imageUrl = "http://127.0.0.1/#$fragment")
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchKeys(url: String): Map<String, String>? {
        // Request viewer URL to populate signed cookies
        var html: String? = null
        try {
            val request = GET(url, headers, CacheControl.FORCE_NETWORK)
            client.newCall(request).execute().use { response ->
                html = response.body.string()
            }
        } catch (_: Exception) {
        }

        val latch = CountDownLatch(1)
        var result: String? = null

        Handler(Looper.getMainLooper()).post {
            val webView = WebView(Injekt.get<Application>())

            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
                userAgentString = headers["User-Agent"]
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        val script = UNext::class.java
                            .getResourceAsStream("/assets/key-extractor.js")!!
                            .bufferedReader()
                            .use { it.readText() }

                        view.evaluateJavascript(script, null)
                    }, 3000,)
                }
            }

            webView.addJavascriptInterface(
                object : Any() {
                    @JavascriptInterface
                    @Suppress("unused")
                    fun passKeys(json: String) {
                        result = json
                        latch.countDown()
                        Handler(Looper.getMainLooper()).post {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }
                },
                "android",
            )

            webView.loadDataWithBaseURL(url, html!!, "text/html", "UTF-8", null)
        }

        latch.await(60, TimeUnit.SECONDS)

        return result?.parseAs<Map<String, String>>()
    }

    override fun imageUrlParse(response: Response): String {
        return response.request.url.toString()
    }

    private inline fun <reified T> apiRequest(payload: Payload<T>): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", payload.operationName)
            .addQueryParameter("variables", payload.variables.toJsonString())
            .addQueryParameter("extensions", payload.extensions.toJsonString())
            .build()

        val newHeaders = super.headersBuilder()
            .set("Content-Type", "application/json")
            .build()

        return GET(url.toString(), newHeaders, CacheControl.FORCE_NETWORK)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PAID_PREF
            title = "Hide paid chapters"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_PAID_PREF = "HIDE_PAID"

        // Monitor network requests to get hashes.
        // https://video.unext.jp/book/categoryranking/D_C_COMIC?genre=freecomic
        // https://cc.unext.jp/?operationName=cosmo_getBookRanking&variables={"targetCode":"D_C_COMIC","page":1,"pageSize":20}&extensions={"persistedQuery":{"version":1,"sha256Hash":"1e1e84fd9b5718c37ef030ea8230bbf9ddd1e5b86f5b8ce2c224b3704f0468ec"}}
        private const val POPULAR_QUERY_HASH = "1e1e84fd9b5718c37ef030ea8230bbf9ddd1e5b86f5b8ce2c224b3704f0468ec"

        // https://video.unext.jp/book/newarrivals/freecomic
        // https://cc.unext.jp/?operationName=cosmo_getNewBooks&variables={"tagCode":"TAG0000014500","page":1,"pageSize":20}&extensions={"persistedQuery":{"version":1,"sha256Hash":"0570a586caa9869bd5eb0b05a59bdfec853f92dc6cf280ebd583df2cc93e1c21"}}
        private const val LATEST_QUERY_HASH = "0570a586caa9869bd5eb0b05a59bdfec853f92dc6cf280ebd583df2cc93e1c21"

        // https://video.unext.jp/freeword/book?query=%E3%81%AE%E3%81%AE
        // https://cc.unext.jp/?zxuid=b2b5221e77d4&zxemp=29455178&operationName=cosmo_bookFreewordSearch&variables={"query":"のの","page":1,"pageSize":20,"filterSaleType":null,"sortOrder":"RECOMMEND"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"2ec7804350bf993678c92a5d79f20812b3b0d5b38aaba2603c9dd291c6df927e"}}
        private const val SEARCH_QUERY_HASH = "2ec7804350bf993678c92a5d79f20812b3b0d5b38aaba2603c9dd291c6df927e"

        // https://video.unext.jp/book/title/BSD0000820098
        // https://cc.unext.jp/?operationName=cosmo_bookTitleDetail&variables={"bookSakuhinCode":"BSD0000820098","viewBookCode":"TOTAL","bookListPageSize":2,"bookListChapterPageSize":5}&extensions={"persistedQuery":{"version":1,"sha256Hash":"99f21ebea20b64b11ef5d3b811c2b3fa5b4dbd8c5d2933baadf9c26fc60b35d1"}}
        private const val DETAILS_QUERY_HASH = "99f21ebea20b64b11ef5d3b811c2b3fa5b4dbd8c5d2933baadf9c26fc60b35d1"

        // https://video.unext.jp/book/title/BSD0000820098?bel=true&epi=0
        // https://cc.unext.jp/?operationName=cosmo_bookTitleBooks&variables={"bookSakuhinCode":"BSD0000820098","booksPage":1,"booksPageSize":9999}&extensions={"persistedQuery":{"version":1,"sha256Hash":"66f0c600259b82a4826fba7be2ace33726f3ec09735e65a421f8f602b481487d"}}
        private const val CHAPTER_LIST_QUERY_HASH = "66f0c600259b82a4826fba7be2ace33726f3ec09735e65a421f8f602b481487d"

        // https://video.unext.jp/book/view/BSD0000820098/BID0001508570
        // https://cc.unext.jp/?operationName=cosmo_getBookPlaylistUrl&variables={"bookFileCode":"BFC0002699405"}&extensions={"persistedQuery":{"version":1,"sha256Hash":"f8a851c14ec61eb42dff966570b2ad49f86eeec7f39d2d32ab0ec58cad268fc1"}}
        private const val PLAYLIST_QUERY_HASH = "f8a851c14ec61eb42dff966570b2ad49f86eeec7f39d2d32ab0ec58cad268fc1"
    }
}
