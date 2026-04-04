package eu.kanade.tachiyomi.extension.id.softkomik

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Softkomik : HttpSource() {
    override val name = "Softkomik"
    override val baseUrl = "https://softkomik.co"
    override val lang = "id"
    override val supportsLatest = true

    private var session: SessionDto? = null
    private var bearerToken: BearerTokenDto? = null

    private val rscHeaders = headersBuilder()
        .add("rsc", "1")
        .build()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageInterceptor)
        .addInterceptor(::apiAuthInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ======================== Popular ========================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/komik/library".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ======================== Latest ========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/komik/library".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "newKomik")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, rscHeaders)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ======================== Search ========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$apiUrl/komik".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("search", "true")
                .addQueryParameter("limit", "20")
                .addQueryParameter("page", page.toString())
            return GET(url.build(), headers)
        }

        val url = "$baseUrl/komik/library".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> url.addQueryParameter("status", filter.selected)
                is TypeFilter -> url.addQueryParameter("type", filter.selected)
                is GenreFilter -> url.addQueryParameter("genre", filter.selected)
                is SortFilter -> url.addQueryParameter("sortBy", filter.selected)
                is MinChapterFilter -> {
                    val minValue = filter.state.toIntOrNull()
                    if (minValue != null && minValue > 0) {
                        url.addQueryParameter("min", minValue.toString())
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), rscHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val libData = if (response.request.url.toString().contains(apiUrl)) {
            response.parseAs<LibDataDto>()
        } else {
            response.extractNextJs<LibDataDto>()
        } ?: throw Exception("Could not find library data")

        val mangas = libData.data.map { manga ->
            SManga.create().apply {
                setUrlWithoutDomain(manga.title_slug)
                title = manga.title
                thumbnail_url = "$coverUrl/${manga.gambar.removePrefix("/")}"
            }
        }
        return MangasPage(mangas, libData.page < libData.maxPage)
    }

    // ======================== Details ========================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/${manga.url}", rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.extractNextJs<MangaDetailsDto>()
            ?: throw Exception("Could not find manga details")

        val slug = response.request.url.pathSegments.lastOrNull()!!
        return SManga.create().apply {
            setUrlWithoutDomain(slug)
            title = manga.title
            author = manga.author
            description = manga.sinopsis
            genre = manga.Genre?.joinToString()
            status = when (manga.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "tamat" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = "$coverUrl/${manga.gambar.removePrefix("/")}"
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    // ======================== Chapters ========================
    override fun chapterListRequest(manga: SManga): Request {
        // isRequiredLogin manga with genre ecchi or mature
        val isRequiredLogin = requiredLoginGenres.any { keyword ->
            manga.genre.orEmpty().contains(keyword, ignoreCase = true)
        }
        var url = "$apiUrl/komik/${manga.url}/chapter?limit=9999999"
        if (isRequiredLogin) {
            url += requiredLoginFragment
        }
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ChapterListDto>()
        val slug = response.request.url.pathSegments[1]
        val isRequiredLogin = response.request.url.fragment?.contains(requiredLoginSuffix) == true
        return dto.chapter.map { chapter ->
            val chapterNumStr = chapter.chapter
            val chapterNum = chapterNumStr.substringBefore(".").toFloatOrNull() ?: -1f
            val displayNum = formatChapterDisplay(chapterNumStr)
            var chapterUrl = "/$slug/chapter/$chapterNumStr"
            if (isRequiredLogin) {
                chapterUrl += requiredLoginFragment
            }
            SChapter.create().apply {
                url = chapterUrl
                name = "Chapter $displayNum"
                chapter_number = chapterNum
            }
        }.sortedByDescending { it.chapter_number }
    }

    private fun formatChapterDisplay(chapterStr: String): String {
        val parts = chapterStr.split(".")
        val numPart = parts[0]
        val suffix = parts.drop(1).joinToString(".")

        val floatVal = numPart.toFloatOrNull() ?: return chapterStr
        val formatted = if (floatVal == floatVal.toLong().toFloat()) {
            floatVal.toLong().toString()
        } else {
            floatVal.toString().trimEnd('0').trimEnd('.')
        }

        return if (suffix.isNotEmpty()) "$formatted.$suffix" else formatted
    }

    // ======================== Pages ========================
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val isRequiredLogin = response.request.url.fragment?.contains(requiredLoginSuffix) == true
        val data = response.extractNextJs<ChapterPageDataDto>()
            ?: throw Exception("Could not find chapter data")

        val imageSrc = data.imageSrc.ifEmpty {
            val slug = response.request.url.pathSegments[0]
            val chapter = response.request.url.pathSegments[2]
            val urlApi = "$apiUrl/komik/$slug/chapter/$chapter/img/${data._id}"

            val token = getBearerTokenFromCookie()
            if (token == null && isRequiredLogin) {
                throw Exception("Chapter memerlukan login di WebView")
            }
            val authHeaders = if (token != null) {
                headersBuilder()
                    .addAll(headers)
                    .set("Authorization", token.token)
                    .build()
            } else {
                headers
            }

            client.newCall(GET(urlApi, authHeaders)).execute().use {
                it.parseAs<ChapterPageImagesDto>().imageSrc
            }
        }

        // for manga/manhwa that requires login, the API still returns 200 but with empty image list.
        if (imageSrc.isEmpty()) {
            throw Exception("Chapter kosong atau memerlukan login di WebView")
        }

        val imageBaseUrl = if (data.storageInter2 == true) cdnUrls[2] else cdnUrls[0]

        return imageSrc.mapIndexed { i, img ->
            Page(i, imageUrl = "$imageBaseUrl/${img.removePrefix("/")}")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .set("Origin", baseUrl)
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    // ============================= Utilities ==============================

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val userAgent = originalRequest.header("User-Agent")
        val normalizedUserAgent = normalizeUserAgent(userAgent)

        val request = if (normalizedUserAgent != userAgent) {
            originalRequest.newBuilder()
                .header("User-Agent", normalizedUserAgent.orEmpty())
                .build()
        } else {
            originalRequest
        }

        val response = try {
            chain.proceed(request)
        } catch (e: java.net.UnknownHostException) {
            null
        }

        if (response?.isSuccessful == true) return response

        val currentHost = cdnUrls.firstOrNull { request.url.toString().startsWith(it) }

        // Only chapter CDN URLs should use retry host fallback.
        // Non-CDN hosts (e.g. cover URL) should return the original response or throw if it failed, without trying other hosts.
        if (currentHost == null) {
            return response ?: throw (java.net.UnknownHostException(request.url.host))
        }

        response?.close()

        val imagePath = request.url.toString().removePrefix(currentHost).removePrefix("/")
        val otherHosts = cdnUrls.filter { it != currentHost }

        var latestResponse: Response? = null
        for (newHost in otherHosts) {
            latestResponse?.close()
            val newUrl = "$newHost/$imagePath".toHttpUrl()
            latestResponse = try {
                chain.proceed(request.newBuilder().url(newUrl).build())
            } catch (e: java.net.UnknownHostException) {
                null
            }
            if (latestResponse?.isSuccessful == true) return latestResponse
        }

        return latestResponse ?: throw java.net.UnknownHostException("All CDN hosts failed for: $imagePath")
    }

    private fun apiAuthInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.url.toString().startsWith(apiUrl)) {
            return chain.proceed(request)
        }
        val sessionResult = getSession()
        val newRequest = request.newBuilder()
            .header("X-Token", sessionResult.token)
            .header("X-Sign", sessionResult.sign)
            .build()

        var response = chain.proceed(newRequest)
        if (response.code == 403) {
            response.close()

            // retry once with session from WebView, in case the session from api is invalid but WebView has valid session
            // get manga slug from url, e.g. https://v2.softdevices.my.id/komik/one-piece/chapter/123/img/456 -> slug = one-piece
            // only get slug if request contains /komik/{slug}/chapter, to avoid intercepting other API calls that don't require auth (e.g. search)
            val slug = request.url.pathSegments.let { segments ->
                val komikIndex = segments.indexOf("komik")
                if (komikIndex != -1 && segments.size > komikIndex + 2 && segments[komikIndex + 2] == "chapter") {
                    segments[komikIndex + 1]
                } else {
                    null
                }
            }
            if (slug == null) {
                // if slug is not found, return original 403 response
                return chain.proceed(request)
            }
            val cookieSession = getSessionViaWebView(slug)
            val retryRequest = request.newBuilder()
                .header("X-Token", cookieSession.token)
                .header("X-Sign", cookieSession.sign)
                .build()
            response = chain.proceed(retryRequest)
        }
        return response
    }

    private fun getBearerTokenFromCookie(): BearerTokenDto? {
        synchronized(this) {
            val currentToken = bearerToken
            if (currentToken != null && currentToken.ex > System.currentTimeMillis()) {
                return currentToken
            }

            val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            val cookieToken = cookies.firstOrNull { it.name == "tokkey" }
            if (cookieToken == null) return null

            val rawValue = cookieToken.value
            val token = runCatching { URLDecoder.decode(rawValue, Charsets.UTF_8.name()) }
                .getOrDefault(rawValue)
            val ex = cookieToken.expiresAt
            bearerToken = BearerTokenDto(token = token, ex = ex)
            return bearerToken
        }
    }

    private fun getSession(): SessionDto {
        val currentSession = session
        if (currentSession != null && currentSession.ex > System.currentTimeMillis()) {
            return currentSession
        }

        synchronized(this) {
            val currentSessionSync = session
            if (currentSessionSync != null && currentSessionSync.ex > System.currentTimeMillis()) {
                return currentSessionSync
            }

            val apiHeaders = headersBuilder()
                .set("Accept", "application/json")
                .set("Content-Type", "application/json")
                .set("X-Requested-With", "XMLHttpRequest")
                .build()

            val hasCookies = client.cookieJar
                .loadForRequest(baseUrl.toHttpUrl())
                .any { it.name == "zEm9be" || it.name == "AhyyL" }

            if (!hasCookies) {
                client.newCall(GET(baseUrl, headers)).execute().close()
                client.newCall(GET("$baseUrl/api/me", apiHeaders)).execute().close()
            }

            val response = client.newCall(GET("$baseUrl/api/sessions/kajskjas", apiHeaders)).execute()

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw Exception("Gagal mendapatkan akses token dari Softkomik (HTTP $code).")
            }

            val newSession = response.use { it.parseAs<SessionDto>() }
            session = newSession
            return newSession
        }
    }

    // because softkomik often changes their api session url,
    // if the request fails, we can try to get session from WebView by loading the manga detail page,
    // which will automatically trigger the chapter list API that carries the session token in the header, and we can intercept that request to get the session token.
    @SuppressLint("SetJavaScriptEnabled")
    private fun getSessionViaWebView(slug: String): SessionDto {
        synchronized(this) {
            val latch = CountDownLatch(1)
            var capturedToken: String? = null
            var capturedSign: String? = null

            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null

            handler.post {
                val wv = WebView(Injekt.get<Application>())
                webView = wv

                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.loadsImagesAutomatically = false
                wv.settings.blockNetworkImage = true
                wv.settings.userAgentString = headers["User-Agent"]

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()

                        // Intercept the chapter list API call — it always carries X-Token & X-Sign
                        if (url.contains(apiUrl)) {
                            val token = request.requestHeaders["X-Token"]
                            val sign = request.requestHeaders["X-Sign"]

                            if (!token.isNullOrEmpty() && !sign.isNullOrEmpty()) {
                                capturedToken = token
                                capturedSign = sign
                                latch.countDown()
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                // Load manga detail page, JS will automatically fire the chapter list API
                wv.loadUrl("$baseUrl/$slug")
            }

            latch.await(15, TimeUnit.SECONDS)
            handler.post { webView?.destroy() }

            val token = capturedToken ?: throw Exception("Gagal mendapatkan session. Coba lagi.")
            val sign = capturedSign ?: throw Exception("Gagal mendapatkan session. Coba lagi.")

            // Based on response session API, expire the session in 2 hours.
            session = SessionDto(token = token, sign = sign, ex = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2))
            return session!!
        }
    }

    // Normalizes the User-Agent by removing "Mobile Safari" because it can cause 401 errors.
    private fun normalizeUserAgent(userAgent: String?): String? {
        if (userAgent.isNullOrBlank()) return null

        return userAgent
            .replace(userAgentMobileSafariRegex, "")
            .trim()
            .ifEmpty { null }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Filter tidak bisa digabungkan dengan pencarian teks."),
        Filter.Separator(),
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
        MinChapterFilter(),
    )
    private val requiredLoginSuffix = "login-required"
    private val requiredLoginFragment = "#$requiredLoginSuffix"
    private val requiredLoginGenres = listOf("ecchi", "mature")
    private val apiUrl = "https://v2.softdevices.my.id"
    private val coverUrl = "https://cover.softdevices.my.id/softkomik-cover"
    private val userAgentMobileSafariRegex = Regex("""\s*Mobile Safari/\d+(?:\.\d+)*""", RegexOption.IGNORE_CASE)
    private val cdnUrls = listOf(
        "https://psy1.komik.im",
        "https://image.komik.im/softkomik",
        "https://cdn1.softkomik.online/softkomik",
        "https://cd1.softkomik.online/softkomik",
        "https://f1.softkomik.com/file/softkomik-image",
        "https://img.softdevices.my.id/softkomik-image",
        "https://image.softkomik.com/softkomik",
    )
}
