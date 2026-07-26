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
import keiyoushi.utils.boolean
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebViewBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    override val supportsLatest = true

    // session cache by URL/page route.
    private val sessionsByUrlKey = ConcurrentHashMap<String, SessionDto>()
    private var bearerToken: BearerTokenDto? = null

    private val rscHeaders get() = headersBuilder()
        .add("rsc", "1")
        .build()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(::imageInterceptor)
        addInterceptor(::apiAuthInterceptor)
    }

    // ======================== Popular ========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/komik/library".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url, rscHeaders)
        return parseSearchManga(response)
    }

    // ======================== Latest ========================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/komik/library".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "newKomik")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url, rscHeaders)
        return parseSearchManga(response)
    }

    // ======================== Search ========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = "$apiUrl/komik".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("search", "true")
                .addQueryParameter("limit", "20")
                .addQueryParameter("page", page.toString())
            val response = client.get(url.build())
            return parseSearchManga(response)
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

        val response = client.get(url.build(), rscHeaders)
        return parseSearchManga(response)
    }

    private fun parseSearchManga(response: Response): MangasPage {
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

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host) {
            val slug = url.pathSegments.firstOrNull() ?: return null
            if (slug.isNotEmpty() && slug != "komik" && slug != "akun" && slug != "api") {
                val manga = SManga.create().apply {
                    setUrlWithoutDomain(slug)
                }
                return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
            }
        }
        return null
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var parsedManga: SManga? = null
        var parsedChapters: List<SChapter>? = null

        if (fetchDetails) {
            val response = client.get(getMangaUrl(manga), rscHeaders)
            val detailsDto = response.extractNextJs<MangaDetailsDto>()
                ?: throw Exception("Could not find manga details")
            parsedManga = SManga.create().apply {
                setUrlWithoutDomain(manga.url)
                title = detailsDto.title
                author = detailsDto.author
                description = detailsDto.sinopsis
                genre = detailsDto.Genre?.joinToString()
                status = when (detailsDto.status?.lowercase()) {
                    "ongoing" -> SManga.ONGOING
                    "tamat" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                thumbnail_url = "$coverUrl/${detailsDto.gambar.removePrefix("/")}"
            }
        }

        if (fetchChapters) {
            // isRequiredLogin manga with genre ecchi or mature
            val isRequiredLogin = requiredLoginGenres.any { keyword ->
                manga.genre.orEmpty().contains(keyword, ignoreCase = true)
            }
            var url = "$apiUrl/komik/${manga.url}/chapter?limit=9999999"
            if (isRequiredLogin) {
                url += requiredLoginFragment
            }
            val response = client.get(url)
            val dto = response.parseAs<ChapterListDto>()
            parsedChapters = dto.chapter.map { chapter ->
                val chapterNumStr = chapter.chapter
                val chapterNum = chapterNumStr.substringBefore(".").toFloatOrNull() ?: -1f
                val displayNum = formatChapterDisplay(chapterNumStr)
                val chapterUrl = "/${manga.url}/chapter/$chapterNumStr"
                SChapter.create().apply {
                    this.url = chapterUrl
                    this.name = "Chapter $displayNum"
                    this.chapter_number = chapterNum
                    this.memo = buildJsonObject {
                        put("isRequiredLogin", isRequiredLogin)
                    }
                }
            }.sortedByDescending { it.chapter_number }
        }

        return SMangaUpdate(parsedManga ?: manga, parsedChapters ?: chapters)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

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
        val response = client.get(getChapterUrl(chapter), rscHeaders)
        val isRequiredLogin = chapter.memo["isRequiredLogin"]?.boolean == true
        val data = response.extractNextJs<ChapterPageDataDto>()
            ?: throw Exception("Could not find chapter data")

        val imageSrc = data.imageSrc.ifEmpty {
            val slug = response.request.url.pathSegments[0]
            val chapterNum = response.request.url.pathSegments[2]
            val urlApi = "$apiUrl/komik/$slug/chapter/$chapterNum/imgs/${data._id}"

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

            val res = client.get(urlApi, authHeaders)
            res.parseAs<ChapterPageImagesDto>().imageSrc
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

        val route = resolveSessionRoute(request.url)
        val apiSession = getSession(route, chain.call())
        val newRequest = request.withHeaders(apiSession)

        var response = chain.proceed(newRequest)
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
        val isChapterImageRequest = isChapterListRequest && (segments.contains("imgs") || segments.contains("img"))

        val sessionKey = if (isChapterImageRequest) sessionKeyChapterImage else sessionKeyChapterList

        val sessionApiUrl = if (isChapterImageRequest) {
            "$baseUrl/api/session/chapter/oioa"
        } else {
            "$baseUrl/api/session/iuiuiwqw"
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

    private fun getSession(route: SessionRoute, call: okhttp3.Call): SessionDto {
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
                .any { it.name == "zEm983" || it.name == "AhyyL" }

            if (!hasCookies) {
                // Fallback to synchronous block, since this is in okhttp interceptor
                client.newCall(GET(baseUrl, headers)).execute().close()
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
        val rawChapter = if (chapterIndex != -1) segments.getOrNull(chapterIndex + 1) else null

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
    private fun getSessionViaWebView(route: SessionRoute, call: okhttp3.Call): SessionDto {
        val webViewUrl = route.webViewUrl
        synchronized(this) {
            return runWebViewBlocking<SessionDto>(call, timeout = 15.seconds) {
                userAgent = headers["User-Agent"]!!
                blockImages = false

                interceptRequest { request ->
                    val url = request.url.toString()
                    if (url.contains(apiUrl)) {
                        val token = request.requestHeaders["X-Token"]
                        val sign = request.requestHeaders["X-Sign"]
                        if (!token.isNullOrEmpty() && !sign.isNullOrEmpty()) {
                            resolve(
                                SessionDto(
                                    token = token,
                                    sign = sign,
                                    ex = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2),
                                ),
                            )
                        }
                    }
                    null
                }

                loadUrl(webViewUrl)
            }.also {
                sessionsByUrlKey[route.key] = it
            }
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

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
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

    // Clean garabage at trailing of signature and token
    fun Request.withHeaders(session: SessionDto): Request = this.newBuilder()
        .header("X-Token", session.token.cleanB64())
        .header("X-Sign", session.sign.substringBefore("|oiq&").take(64))
        .build()

    fun String.cleanB64(): String = substringBefore('=').let { it -> it + "=".repeat((4 - (it.length % 4)) % 4) }
}
