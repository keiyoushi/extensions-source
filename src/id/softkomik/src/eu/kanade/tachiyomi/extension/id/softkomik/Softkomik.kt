package eu.kanade.tachiyomi.extension.id.softkomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebViewBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Softkomik : KeiSource() {

    // session cache by URL/page route.
    private val sessionsByUrlKey = ConcurrentHashMap<String, SessionDto>()
    private var bearerToken: BearerTokenDto? = null

    private val rscHeaders: Headers
        get() = headersBuilder()
            .add("rsc", "1")
            .build()

    override fun OkHttpClient.Builder.configureClient() = this
        .addInterceptor(::imageInterceptor)
        .addInterceptor(::apiAuthInterceptor)

    // ======================== Popular ========================
    override suspend fun getPopularManga(page: Int): MangasPage = getLibraryPage(libraryUrlBuilder(page).addQueryParameter("sortBy", "popular").build())

    // ======================== Latest ========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = getLibraryPage(libraryUrlBuilder(page).addQueryParameter("sortBy", "newKomik").build())

    // ======================== Search ========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = "$apiUrl/komik".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("search", "true")
                .addQueryParameter("limit", "20")
                .addQueryParameter("page", page.toString())
                .build()

            return client.get(url).parseAs<LibDataDto>().toMangasPage()
        }

        val url = libraryUrlBuilder(page)

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

        return getLibraryPage(url.build())
    }

    private fun libraryUrlBuilder(page: Int) = "$baseUrl/komik/library".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())

    private suspend fun getLibraryPage(url: HttpUrl): MangasPage {
        val libData = client.get(url, rscHeaders).extractNextJs<LibDataDto>()
            ?: throw Exception("Could not find library data")

        return libData.toMangasPage()
    }

    private fun LibDataDto.toMangasPage(): MangasPage {
        val mangas = data.map { manga ->
            SManga.create().apply {
                url = manga.titleSlug
                title = manga.title
                thumbnail_url = "$coverUrl/${manga.gambar.removePrefix("/")}"
            }
        }
        return MangasPage(mangas, page < maxPage)
    }

    // ======================== Details ========================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null

        return getMangaDetails(slug)
    }

    private suspend fun getMangaDetails(slug: String): SManga {
        val manga = client.get("$baseUrl/$slug", rscHeaders).extractNextJs<MangaDetailsDto>()
            ?: throw Exception("Could not find manga details")

        return SManga.create().apply {
            url = slug
            title = manga.title
            author = manga.author
            description = manga.sinopsis
            genre = manga.genre?.joinToString()
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
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) getMangaDetails(manga.url) else manga
        val chapterList = if (fetchChapters) getChapterList(details) else chapters

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        // isRequiredLogin manga with genre ecchi or mature
        val isRequiredLogin = requiredLoginGenres.any { keyword ->
            manga.genre.orEmpty().contains(keyword, ignoreCase = true)
        }
        val slug = manga.url
        val dto = client.get("$apiUrl/komik/$slug/chapter?limit=9999999").parseAs<ChapterListDto>()

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
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val isRequiredLogin = chapter.url.substringAfter('#', "").contains(requiredLoginSuffix)
        val segments = chapter.url.substringBefore('#').trim('/').split("/")
        val slug = segments[0]
        val chapterNum = segments[2]

        val data = client.get("$baseUrl${chapter.url}", rscHeaders)
            .extractNextJs<ChapterPageDataDto>()
            ?: throw Exception("Could not find chapter data")

        val imageSrc = data.imageSrc.ifEmpty {
            val urlApi = "$apiUrl/komik/$slug/chapter/$chapterNum/imgs/${data.id}"

            val token = getBearerTokenFromCookie()
            if (token == null && isRequiredLogin) {
                throw Exception("Chapter memerlukan login di WebView")
            }
            val authHeaders = if (token != null) {
                headersBuilder()
                    .set("Authorization", token.token)
                    .build()
            } else {
                headers
            }

            client.get(urlApi, authHeaders).parseAs<ChapterPageImagesDto>().imageSrc
        }

        // for manga/manhwa that requires login, the API still returns 200 but with empty image list.
        if (imageSrc.isEmpty()) {
            throw Exception("Chapter kosong atau memerlukan login di WebView")
        }

        val imageBaseUrl = when {
            data.backBS3 == true -> primaryCdnUrl
            data.storageInter2 == true -> secondaryCdnUrl
            else -> primaryCdnUrl
        }

        return imageSrc.mapIndexed { i, img ->
            Page(i, imageUrl = "$imageBaseUrl/${img.removePrefix("/")}?id=$imageIdParam")
        }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
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

        if (response?.isSuccessful == true && !response.isTrapImage()) return response

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
            if (latestResponse?.isSuccessful == true && !latestResponse.isTrapImage()) return latestResponse
        }

        if (latestResponse?.isTrapImage() == true) {
            latestResponse.close()
            throw Exception("Gambar diblokir oleh situs, extension perlu diperbarui")
        }

        return latestResponse ?: throw java.net.UnknownHostException("All CDN hosts failed for: $imagePath")
    }

    private fun Response.isTrapImage(): Boolean = request.url.encodedPath.endsWith(trapImagePath)

    private fun apiAuthInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.url.toString().startsWith(apiUrl)) {
            return chain.proceed(request)
        }

        val route = resolveSessionRoute(request.url)
        val apiSession = getSession(route, chain.call())
        val newRequest = request.withHeaders(apiSession)

        val response = chain.proceed(newRequest)
        if (response.isSuccessful) return response

        // Fallback to webivew just in case credentials were still invalid
        response.close()
        val webviewSession = getSessionViaWebView(route, chain.call())
        return chain.proceed(request.withHeaders(webviewSession))
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

    private data class SessionRoute(
        val key: String,
        val sessionApiUrl: String,
        val webViewUrl: String,
        val slug: String?,
        val isChapterListRequest: Boolean,
        val isChapterImageRequest: Boolean,
    )

    private fun resolveSessionRoute(url: HttpUrl): SessionRoute {
        val segments = url.pathSegments
        val komikIndex = segments.indexOf("komik")
        // slug is always the segment after "komik" in both chapter list and chapter image API
        val slug = if (komikIndex != -1) segments.getOrNull(komikIndex + 1) else null
        // chapter list API $apiUrl/komik/${manga.url}/chapter?limit=9999999
        val isChapterListRequest = komikIndex != -1 && segments.getOrNull(komikIndex + 2) == "chapter"
        // chapter image API $apiUrl/komik/${manga.url}/chapter/${chapter}/imgs/${data._id}
        val isChapterImageRequest = isChapterListRequest && segments.contains("imgs")

        val sessionKey = if (isChapterImageRequest) sessionKeyChapterImage else sessionKeyChapterList

        val sessionApiUrl = if (isChapterImageRequest) {
            "$baseUrl/api/session/chapter/oaisos"
        } else {
            "$baseUrl/api/session/aksjkas"
        }
        val webViewUrl = if (isChapterImageRequest) {
            val chapterSegment = resolveWebViewChapterSegment(url)
            if (chapterSegment != null) {
                "$baseUrl/$slug/chapter/$chapterSegment"
            } else {
                "$baseUrl/$slug/chapter/001"
            }
        } else if (isChapterListRequest) {
            "$baseUrl/$slug"
        } else {
            "$baseUrl/komik/list" // this for manga list with filters.
        }

        return SessionRoute(
            key = sessionKey,
            sessionApiUrl = sessionApiUrl,
            slug = slug,
            isChapterListRequest = isChapterListRequest,
            isChapterImageRequest = isChapterImageRequest,
            webViewUrl = webViewUrl,
        )
    }

    private fun getSession(route: SessionRoute, call: Call): SessionDto {
        sessionsByUrlKey[route.key]?.takeIf { it.ex > System.currentTimeMillis() }?.let { return it }

        synchronized(this) {
            sessionsByUrlKey[route.key]?.takeIf { it.ex > System.currentTimeMillis() }?.let { return it }
            val apiHeaders = headersBuilder()
                .set("Accept", "application/json")
                .set("Content-Type", "application/json")
                .set("X-Requested-With", "XMLHttpRequest")
                .build()

            val hasCookies = client.cookieJar
                .loadForRequest(baseUrl.toHttpUrl())
                .any { it.name == "zEm9be" || it.name == "AhyyL" }

            if (!hasCookies) {
                client.newCall(GET(baseUrl)).execute().close()
                client.newCall(GET("$baseUrl/api/me", apiHeaders)).execute().close()
            }

            val response = runCatching {
                client.newCall(GET(route.sessionApiUrl, apiHeaders)).execute()
            }.getOrNull()

            if (response?.isSuccessful == true) {
                val newSession = response.use { it.parseAs<SessionDto>() }
                sessionsByUrlKey[route.key] = newSession
                return newSession
            }
            response?.close()

            // Softkomik frequently renames their session API endpoint. When the direct
            // call fails (commonly with HTTP 404), fall back to capturing the session
            // headers that the site's own JavaScript sends from a live WebView — that
            // path survives URL changes without an extension update.
            return getSessionViaWebView(route, call)
        }
    }

    private fun resolveWebViewChapterSegment(url: HttpUrl): String? {
        val segments = url.pathSegments
        val chapterIndex = segments.indexOf("chapter")
        val rawChapter = if (chapterIndex != -1) segments.getOrNull(chapterIndex + 1) else return null

        val chapterNumber = rawChapter?.toIntOrNull()
        return if (chapterNumber != null && chapterNumber < 100) {
            chapterNumber.toString().padStart(3, '0')
        } else {
            rawChapter
        }
    }

    // because softkomik often changes their api session url,
    // if the request fails, we can try to get session from WebView by loading the manga detail page,
    // which will automatically trigger the chapter list API that carries the session token in the header, and we can intercept that request to get the session token.
    private fun getSessionViaWebView(route: SessionRoute, call: Call): SessionDto {
        synchronized(this) {
            val session = try {
                runWebViewBlocking<SessionDto>(call, timeout = 15.seconds) {
                    // the captured session token embeds the User-Agent, so it has to match the one
                    // the client actually sends (see normalizeUserAgent).
                    userAgent = normalizeUserAgent(headers["User-Agent"]).orEmpty()
                    blockImages = true

                    interceptRequest { request ->
                        // Intercept the chapter list API call — it always carries X-Token & X-Sign
                        if (request.url.toString().contains(apiUrl)) {
                            val token = request.requestHeaders["X-Token"]
                            val sign = request.requestHeaders["X-Sign"]

                            if (!token.isNullOrEmpty() && !sign.isNullOrEmpty()) {
                                val contentToken = request.requestHeaders["X-Content-Token"]
                                val contentSign = request.requestHeaders["X-Content-Sign"]

                                resolve(
                                    SessionDto(
                                        token = token,
                                        sign = sign,
                                        // Based on response session API, expire the session in 2 hours.
                                        ex = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2),
                                        contentAccess = if (!contentToken.isNullOrEmpty() && !contentSign.isNullOrEmpty()) {
                                            ContentAccessDto(contentToken, contentSign)
                                        } else {
                                            null
                                        },
                                    ),
                                )
                            }
                        }
                        null
                    }

                    // Load manga detail page, JS will automatically fire the chapter list API
                    loadUrl(route.webViewUrl)
                }
            } catch (e: WebViewTimeoutException) {
                throw Exception("Gagal mendapatkan session. Coba lagi.")
            }

            sessionsByUrlKey[route.key] = session
            return session
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

    override fun getFilterList(data: JsonElement?) = FilterList(
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
    private val sessionKeyChapterList = "chapter-list"
    private val sessionKeyChapterImage = "chapter-image"
    private val apiUrl = "https://api.softkomik.org"

    // Inlined in the site's chapter chunk as `String("...").trim()` and appended to every image URL.
    private val imageIdParam = "T4Kmwztku"
    private val trapImagePath = "/baca-image.jpeg"
    private val coverUrl = "https://cover.softdevices.my.id/softkomik-cover"
    private val userAgentMobileSafariRegex = Regex("""\s*Mobile Safari/\d+(?:\.\d+)*""", RegexOption.IGNORE_CASE)
    private val primaryCdnUrl = "https://psy1.komik.im"
    private val secondaryCdnUrl = "https://image.komik.im/softkomik"
    private val cdnUrls = listOf(primaryCdnUrl, secondaryCdnUrl)

    // Clean garabage at trailing of signature and token
    fun Request.withHeaders(session: SessionDto): Request = this.newBuilder()
        .header("X-Token", session.token.cleanB64())
        .header("X-Sign", session.sign.take(64))
        .apply {
            // sent by the site for chapters gated behind an account
            session.contentAccess?.let {
                header("X-Content-Token", it.token)
                header("X-Content-Sign", it.sign)
            }
        }
        .build()

    fun String.cleanB64(): String = substringBefore('=').let { it -> it + "=".repeat((4 - (it.length % 4)) % 4) }
}
