package eu.kanade.tachiyomi.extension.th.reborntrans

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/*
Author: github.com/keegang6705, Claude Sonnet 4.6, GPT-5.2
 */

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
    private val handler = Handler(Looper.getMainLooper())
    private val context
        get() = Injekt.get<Application>()

    companion object {
        private const val TAG = "RebornTrans"
        private const val WEBVIEW_TIMEOUT = 30L
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
            GET("$ajaxUrl?action=manga_eagle_load_paginated_posts&page=$page&limit=15", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaAjax(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$ajaxUrl?action=manga_eagle_load_paginated_posts&page=$page&limit=15", headers)

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
                                                android.text.Html.FROM_HTML_MODE_LEGACY
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
                                    android.text.Html.FROM_HTML_MODE_LEGACY
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
                                it.removePrefix("category-").replace("-", " ").replaceFirstChar { c
                                    ->
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

    override fun chapterListRequest(manga: SManga): Request =
            GET("$baseUrl${manga.url}?tab=episodes", headers)

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

    override fun pageListRequest(chapter: SChapter): Request =
            GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val chapterUrl = response.request.url.toString()
        Log.d(TAG, "pageListParse: loading $chapterUrl")

        val document = response.asJsoup()

        val scriptText =
                document.select("script").map { it.data() }.firstOrNull {
                    it.contains("episodeId=")
                }
                        ?: throw Exception(
                                "[$TAG] Could not find episode script block in page HTML"
                        )

        val episodeId =
                Regex("""episodeId=(\d+)""").find(scriptText)?.groupValues?.get(1)
                        ?: throw Exception("[$TAG] Regex failed to extract episodeId from script")

        Log.d(TAG, "episodeId=$episodeId — launching WebView for Turnstile")

        val token = fetchTurnstileToken(chapterUrl, episodeId)

        if (token == null) {
            Log.w(
                    TAG,
                    "episodeId=$episodeId — WebView timed out or returned no token after ${WEBVIEW_TIMEOUT}s"
            )
            throw Exception(
                    "[$TAG] Turnstile token not received within ${WEBVIEW_TIMEOUT}s (episodeId=$episodeId)\nTry opening the chapter again."
            )
        }

        Log.d(TAG, "episodeId=$episodeId — token received (len=${token.length}), calling API")

        val ts = System.currentTimeMillis()
        val apiUrl = "$baseUrl/wp-json/manga-eagle/v1/episodes/$episodeId/images?_t=$ts"
        val bodyJson = """{"_t":$ts,"turnstile_token":"$token"}"""
        Log.d(TAG, "POST $apiUrl  body=$bodyJson")

        val apiHeaders =
                headers.newBuilder()
                        .set("Content-Type", "application/json")
                        .set("X-Requested-With", "XMLHttpRequest")
                        .set("Cache-Control", "no-cache")
                        .build()

        val apiResponse =
                client.newCall(POST(apiUrl, apiHeaders, bodyJson.toRequestBody(jsonMediaType)))
                        .execute()
        val rawBody = apiResponse.peekBody(Long.MAX_VALUE).string()
        Log.d(TAG, "API response ${apiResponse.code}: $rawBody")

        val result =
                try {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                            .decodeFromString<ImageApiResponse>(rawBody)
                } catch (e: Exception) {
                    throw Exception(
                            "[$TAG] Failed to parse image API response: ${e.message}\nRaw: $rawBody"
                    )
                }

        if (!result.success) {
            throw Exception(
                    "[$TAG] API returned success=false (episodeId=$episodeId)\nRaw: $rawBody"
            )
        }

        if (result.assets.isEmpty()) {
            throw Exception("[$TAG] API returned 0 assets (episodeId=$episodeId)\nRaw: $rawBody")
        }

        Log.d(TAG, "episodeId=$episodeId — got ${result.assets.size} images")
        return result.assets.mapIndexed { i, asset ->
            Log.d(TAG, "  page $i → ${asset.url}")
            Page(i, imageUrl = asset.url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchTurnstileToken(url: String, episodeId: String): String? {
        val latch = CountDownLatch(1)
        var token: String? = null
        var webView: WebView? = null

        // This JS is injected at document-start via onPageStarted to avoid the race
        // where turnstile.render() fires before onPageFinished
        val patchJs =
                """
            (function() {
                if (window.__tachiyomiPatched) return;
                window.__tachiyomiPatched = true;
                console.log('[RebornTrans] JS bridge installed');

                function tryPatchTurnstile() {
                    if (typeof turnstile !== 'undefined' && typeof turnstile.render === 'function') {
                        var origRender = turnstile.render;
                        turnstile.render = function(el, params) {
                            console.log('[RebornTrans] turnstile.render intercepted');
                            var origCallback = params.callback;
                            params.callback = function(t) {
                                console.log('[RebornTrans] Turnstile token received, len=' + t.length);
                                window.TachiyomiTurnstile.onToken(t);
                                if (origCallback) origCallback(t);
                            };
                            return origRender.call(this, el, params);
                        };
                        console.log('[RebornTrans] turnstile.render patched successfully');
                    } else {
                        setTimeout(tryPatchTurnstile, 50);
                    }
                }
                tryPatchTurnstile();
            })();
        """.trimIndent()

        handler.post {
            webView =
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = headers["User-Agent"]

                        addJavascriptInterface(
                                object : Any() {
                                    @JavascriptInterface
                                    fun onToken(t: String) {
                                        Log.d(
                                                TAG,
                                                "episodeId=$episodeId WebView bridge: token len=${t.length}"
                                        )
                                        token = t
                                        latch.countDown()
                                    }

                                    @JavascriptInterface
                                    fun onLog(msg: String) {
                                        Log.d(TAG, "episodeId=$episodeId WebView JS: $msg")
                                    }
                                },
                                "TachiyomiTurnstile"
                        )

                        webViewClient =
                                object : WebViewClient() {
                                    override fun onPageStarted(
                                            view: WebView,
                                            pageUrl: String,
                                            favicon: android.graphics.Bitmap?
                                    ) {
                                        Log.d(
                                                TAG,
                                                "episodeId=$episodeId WebView onPageStarted: $pageUrl"
                                        )
                                        // Inject early — before any site JS runs
                                        view.evaluateJavascript(patchJs, null)
                                    }

                                    override fun onPageFinished(view: WebView, pageUrl: String) {
                                        Log.d(
                                                TAG,
                                                "episodeId=$episodeId WebView onPageFinished: $pageUrl"
                                        )
                                        // Re-inject in case the page did a soft navigation
                                        view.evaluateJavascript(patchJs, null)
                                    }

                                    override fun onReceivedError(
                                            view: WebView,
                                            errorCode: Int,
                                            description: String,
                                            failingUrl: String,
                                    ) {
                                        Log.e(
                                                TAG,
                                                "episodeId=$episodeId WebView error $errorCode: $description @ $failingUrl"
                                        )
                                    }
                                }

                        Log.d(TAG, "episodeId=$episodeId WebView loading $url")
                        loadUrl(url)
                    }
        }

        val completed = latch.await(WEBVIEW_TIMEOUT, TimeUnit.SECONDS)
        Log.d(
                TAG,
                "episodeId=$episodeId latch completed=$completed token=${if (token != null) "present" else "null"}"
        )

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
            Log.d(TAG, "episodeId=$episodeId WebView destroyed")
        }

        return token
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()

    // ============================== Helpers ===============================

    private fun parseDate(text: String): Long =
            runCatching { dateFormat.parse(text)?.time }.getOrNull() ?: 0L
}
