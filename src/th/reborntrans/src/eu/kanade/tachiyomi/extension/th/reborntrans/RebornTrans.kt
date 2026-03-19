package eu.kanade.tachiyomi.extension.th.reborntrans

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.text.Html
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
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
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

/*
Author: keegang, Claude Opus4.6, Gemini 3.1 Pro

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
    private val json = Json { ignoreUnknownKeys = true }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val context
        get() = Injekt.get<Application>()

    companion object {
        private const val WEBVIEW_TIMEOUT = 45L
        private val EPISODE_ID_REGEX = Regex("""episodeId=(\d+)""")

        private val FETCH_INTERCEPT_JS =
            """
            (function() {
                if (window._0x3f2a) return;
                window._0x3f2a = true;
                var _origFetch = window.fetch;
                window.fetch = function(input, init) {
                    var url = (typeof input === 'string') ? input : (input && input.url) || '';
                    if (url.indexOf('/images') !== -1 && init && init.body) {
                        try {
                            var body = JSON.parse(init.body);
                            if (body.turnstile_token) {
                                window.TachiyomiRT.onToken(body.turnstile_token);
                            }
                        } catch(e) {}
                    }
                    return _origFetch.apply(this, arguments);
                };
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
                        Html.fromHtml(
                            post.title.rendered,
                            Html.FROM_HTML_MODE_LEGACY,
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
        val document = org.jsoup.Jsoup.parseBodyFragment(html, baseUrl)
        val mangas =
            document.select("a.manga-card").map { el ->
                SManga.create().apply {
                    title = el.selectFirst("h3")!!.text()
                    setUrlWithoutDomain(el.attr("abs:href"))
                    thumbnail_url = el.selectFirst("img")?.absUrl("src")
                }
            }
        return MangasPage(mangas, mangas.size == 15)
    }

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments.last { it.isNotEmpty() }
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
                Html.fromHtml(
                    post.title.rendered,
                    Html.FROM_HTML_MODE_LEGACY,
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
                        dateFormat.tryParse(it)
                    }
                        ?: 0L
            }
        }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val chapterUrl = response.request.url.toString()
        val document = response.asJsoup()

        var scriptText: String? = null
        for (element in document.select("script")) {
            if (element.data().contains("episodeId=")) {
                scriptText = element.data()
                break
            }
        }
        if (scriptText == null) throw Exception("No episode script block found in HTML")

        val episodeId =
            EPISODE_ID_REGEX.find(scriptText)?.groupValues?.get(1)
                ?: throw Exception("episodeId not found in script")

        val token =
            fetchTurnstileToken(chapterUrl, episodeId)
                ?: throw Exception(
                    "Turnstile token not received within ${WEBVIEW_TIMEOUT}s",
                )

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

        val result =
            try {
                json.decodeFromString<ImageApiResponse>(rawBody)
            } catch (e: Exception) {
                throw Exception("JSON parse failed: ${e.message}")
            }

        if (!result.success) throw Exception("API success=false")
        if (result.assets.isEmpty()) throw Exception("API returned 0 assets")

        return result.assets.mapIndexed { i, asset -> Page(i, imageUrl = asset.url) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchTurnstileToken(url: String, episodeId: String): String? {
        val latch = CountDownLatch(1)
        var token: String? = null
        var webView: WebView? = null

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
                        if (token == null) {
                            token = t
                            latch.countDown()
                        }
                    }

                    @JavascriptInterface fun onLog(msg: String) {}
                },
                "TachiyomiRT",
            )

            wv.webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = null

                    override fun onPageFinished(view: WebView, pageUrl: String) {
                        view.evaluateJavascript(FETCH_INTERCEPT_JS, null)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(
                        view: WebView,
                        errorCode: Int,
                        description: String,
                        failingUrl: String,
                    ) {}
                }

            wv.loadUrl(url)
        }

        val completed = latch.await(WEBVIEW_TIMEOUT, TimeUnit.SECONDS)

        mainHandler.post {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }

        return token
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()
}
