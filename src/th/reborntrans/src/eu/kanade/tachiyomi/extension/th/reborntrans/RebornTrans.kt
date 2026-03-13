package eu.kanade.tachiyomi.extension.th.reborntrans

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.extension.th.reborntrans.model.AjaxResponse
import eu.kanade.tachiyomi.extension.th.reborntrans.model.ImageApiResponse
import eu.kanade.tachiyomi.extension.th.reborntrans.model.WpPost
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RebornTrans : HttpSource() {
    override val name = "Reborn Trans"
    override val baseUrl = "https://reborntrans.com"
    override val lang = "th"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().rateLimit(3).build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yy.MM.dd", Locale.US)
    private val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"
    private val jsonMediaType = "application/json".toMediaType()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    // Lazy so it's only created when first used, by which point the main looper exists
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val context
        get() = Injekt.get<Application>()

    companion object {
        private const val TAG = "RebornTrans"
        private const val WEBVIEW_TIMEOUT = 45L

        private val FETCH_INTERCEPT_JS =
            """
            (function() {
                if (window.__rtIntercepted) return;
                window.__rtIntercepted = true;
                var _origFetch = window.fetch;
                window.fetch = function(input, init) {
                    var url = (typeof input === 'string') ? input : (input && input.url) || '';
                    if (url.indexOf('/images') !== -1 && init && init.body) {
                        try {
                            var body = JSON.parse(init.body);
                            if (body.turnstile_token) {
                                window.TachiyomiRT.onToken(body.turnstile_token);
                            } else {
                                window.TachiyomiRT.onLog('fetch /images — no token in body: ' + init.body);
                            }
                        } catch(e) {
                            window.TachiyomiRT.onLog('parse error: ' + e);
                        }
                    }
                    return _origFetch.apply(this, arguments);
                };
                window.TachiyomiRT.onLog('fetch interceptor installed');
            })();
            """.trimIndent()
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$ajaxUrl?action=manga_eagle_load_paginated_posts&page=$page&limit=15", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaAjax(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$ajaxUrl?action=manga_eagle_load_paginated_posts&page=$page&limit=15", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaAjax(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url =
            "$baseUrl/wp-json/wp/v2/posts"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("_embed", "wp:featuredmedia")
                .addQueryParameter("per_page", "20")
                .addQueryParameter("page", page.toString())
                .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val posts = response.parseAs<List<WpPost>>()
        val totalPages = response.headers["X-WP-TotalPages"]?.toIntOrNull() ?: 1
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val mangas =
            posts.map { post ->
                SManga.create().apply {
                    title =
                        android.text.Html.fromHtml(
                            post.title.rendered,
                            android.text.Html.FROM_HTML_MODE_LEGACY,
                        )
                            .toString()
                            .trim()
                    setUrlWithoutDomain(post.link.removePrefix(baseUrl))
                    thumbnail_url = post.embedded?.featuredMedia?.firstOrNull()?.sourceUrl
                }
            }
        return MangasPage(mangas, currentPage < totalPages)
    }

    // ============================== Parsing ===============================

    private fun parseMangaAjax(response: Response): MangasPage {
        val ajax = response.parseAs<AjaxResponse>()
        val html = ajax.data?.html.orEmpty()
        val doc = org.jsoup.Jsoup.parseBodyFragment(html, baseUrl)
        val mangas =
            doc.select("a.manga-card").map { el ->
                SManga.create().apply {
                    title = el.selectFirst("h3")!!.text().trim()
                    setUrlWithoutDomain(el.attr("abs:href"))
                    thumbnail_url = el.selectFirst("img")?.absUrl("src")
                }
            }
        return MangasPage(mangas, mangas.size == 15)
    }

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removeSuffix("/").substringAfterLast("/")
        val url =
            "$baseUrl/wp-json/wp/v2/posts"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("slug", slug)
                .addQueryParameter("_embed", "wp:featuredmedia,wp:term")
                .build()
        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val post = response.parseAs<List<WpPost>>().first()
        return SManga.create().apply {
            title =
                android.text.Html.fromHtml(
                    post.title.rendered,
                    android.text.Html.FROM_HTML_MODE_LEGACY,
                )
                    .toString()
                    .trim()
            description = org.jsoup.Jsoup.parseBodyFragment(post.content.rendered).text().trim()
            thumbnail_url =
                post.meta?.coverUrl ?: post.embedded?.featuredMedia?.firstOrNull()?.sourceUrl
            genre =
                post.classList
                    .filter { it.startsWith("category-") && it != "category-uncategorized" }
                    .map {
                        it.removePrefix("category-").replace("-", " ").replaceFirstChar { c ->
                            c.uppercase()
                        }
                    }
                    .joinToString()
                    .ifEmpty { null }
            status =
                when (post.meta?.workStatus) {
                    "completed" -> SManga.COMPLETED
                    "processing", "ongoing" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}?tab=episodes", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a.manga-single-episode-item").map { el ->
            SChapter.create().apply {
                setUrlWithoutDomain(el.attr("href"))
                name =
                    el.selectFirst("h3.manga-single-episode-item__title")?.text()?.trim()
                        ?: "ตอนที่ ${el.attr("data-episode-number")}"
                chapter_number = el.attr("data-episode-number").toFloatOrNull() ?: -1f
                date_upload =
                    el.selectFirst(".manga-single-episode-item__date")?.text()?.let {
                        parseDate(it)
                    }
                        ?: 0L
            }
        }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val chapterUrl = response.request.url.toString()
        Log.d(TAG, "pageListParse: $chapterUrl")

        val document = response.asJsoup()

        val scriptText =
            document.select("script").map { it.data() }.firstOrNull {
                it.contains("episodeId=")
            }
                ?: throw Exception("[$TAG] No episode script block found in HTML")

        val episodeId =
            Regex("""episodeId=(\d+)""").find(scriptText)?.groupValues?.get(1)
                ?: throw Exception("[$TAG] episodeId not found in script")

        Log.d(TAG, "episodeId=$episodeId — starting WebView")

        val token =
            fetchTurnstileToken(chapterUrl, episodeId)
                ?: throw Exception(
                    "[$TAG] Turnstile token not received within ${WEBVIEW_TIMEOUT}s\n" +
                        "episodeId=$episodeId — check logcat tag=$TAG",
                )

        Log.d(TAG, "episodeId=$episodeId — token len=${token.length}, calling API")

        val ts = System.currentTimeMillis()
        val apiUrl = "$baseUrl/wp-json/manga-eagle/v1/episodes/$episodeId/images?_t=$ts"
        val bodyJson = """{"_t":$ts,"turnstile_token":"$token"}"""

        val apiHeaders =
            headers.newBuilder()
                .set("Content-Type", "application/json")
                .set("X-Requested-With", "XMLHttpRequest")
                .set("Cache-Control", "no-cache")
                .build()

        val apiResponse =
            client.newCall(
                POST(apiUrl, apiHeaders, bodyJson.toRequestBody(jsonMediaType)),
            )
                .execute()

        val rawBody = apiResponse.peekBody(Long.MAX_VALUE).string()
        Log.d(TAG, "API ${apiResponse.code}: $rawBody")

        val result =
            try {
                json.decodeFromString<ImageApiResponse>(rawBody)
            } catch (e: Exception) {
                throw Exception("[$TAG] JSON parse failed: ${e.message} — raw: $rawBody")
            }

        if (!result.success) throw Exception("[$TAG] API success=false — raw: $rawBody")
        if (result.assets.isEmpty()) throw Exception("[$TAG] API returned 0 assets — raw: $rawBody")

        Log.d(TAG, "episodeId=$episodeId — ${result.assets.size} pages")
        return result.assets.mapIndexed { i, asset -> Page(i, imageUrl = asset.url) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchTurnstileToken(url: String, episodeId: String): String? {
        val latch = CountDownLatch(1)
        var token: String? = null
        var webView: WebView? = null

        // WebView must be created on the main thread
        mainHandler.post {
            val wv = WebView(context)
            webView = wv

            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = headers["User-Agent"]

            wv.addJavascriptInterface(
                object : Any() {
                    @JavascriptInterface
                    fun onToken(t: String) {
                        Log.d(TAG, "episodeId=$episodeId bridge.onToken len=${t.length}")
                        if (token == null) {
                            token = t
                            latch.countDown()
                        }
                    }

                    @JavascriptInterface
                    fun onLog(msg: String) {
                        Log.d(TAG, "episodeId=$episodeId JS: $msg")
                    }
                },
                "TachiyomiRT",
            )

            wv.webViewClient =
                object : WebViewClient() {
                    // shouldInterceptRequest runs on a background thread — no handler.post
                    // needed,
                    // but we cannot call evaluateJavascript here (requires main thread).
                    // Instead use onPageFinished which is always on main thread.
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        Log.d(TAG, "episodeId=$episodeId intercept: ${request.url}")
                        return null
                    }

                    override fun onPageFinished(view: WebView, pageUrl: String) {
                        // onPageFinished is called on the main thread — safe to
                        // evaluateJavascript
                        Log.d(TAG, "episodeId=$episodeId onPageFinished: $pageUrl")
                        view.evaluateJavascript(FETCH_INTERCEPT_JS, null)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(
                        view: WebView,
                        errorCode: Int,
                        description: String,
                        failingUrl: String,
                    ) {
                        Log.e(
                            TAG,
                            "episodeId=$episodeId WebView error $errorCode '$description' @ $failingUrl",
                        )
                    }
                }

            Log.d(TAG, "episodeId=$episodeId WebView.loadUrl($url)")
            wv.loadUrl(url)
        }

        val completed = latch.await(WEBVIEW_TIMEOUT, TimeUnit.SECONDS)
        Log.d(
            TAG,
            "episodeId=$episodeId latch completed=$completed token=${if (token != null) "ok(${token?.length})" else "null"}",
        )

        mainHandler.post {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            Log.d(TAG, "episodeId=$episodeId WebView destroyed")
        }

        return token
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()

    private fun parseDate(text: String): Long = runCatching { dateFormat.parse(text)?.time }.getOrNull() ?: 0L
}
