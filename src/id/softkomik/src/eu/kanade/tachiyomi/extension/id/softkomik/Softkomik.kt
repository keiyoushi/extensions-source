package eu.kanade.tachiyomi.extension.id.softkomik

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
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder

class Softkomik : HttpSource() {
    override val name = "Softkomik"
    override val baseUrl = "https://softkomik.co"
    override val lang = "id"
    override val supportsLatest = true

    private var session: SessionDto? = null

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
        val url = "$apiUrl/komik/${manga.url}/chapter?limit=9999999"
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ChapterListDto>()
        val slug = response.request.url.pathSegments[1]
        return dto.chapter.map { chapter ->
            val chapterNumStr = chapter.chapter
            val chapterNum = chapterNumStr.substringBefore(".").toFloatOrNull() ?: -1f
            val displayNum = formatChapterDisplay(chapterNumStr)
            SChapter.create().apply {
                url = "/$slug/chapter/$chapterNumStr"
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
        val data = response.extractNextJs<ChapterPageDataDto>()
            ?: throw Exception("Could not find chapter data")

        val imageSrc = if (data.imageSrc.isEmpty()) {
            val slug = response.request.url.pathSegments[0]
            val chapter = response.request.url.pathSegments[2]
            val url = "$apiUrl/komik/$slug/chapter/$chapter/img/${data._id}"
            client.newCall(GET(url, headers)).execute().use {
                it.parseAs<ChapterPageImagesDto>().imageSrc
            }
        } else {
            data.imageSrc
        }

        if (imageSrc.isEmpty()) {
            throw Exception("No pages found")
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

        if (!request.url.host.endsWith("softdevices.my.id") || request.url.toString().startsWith(coverUrl)) {
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

            // retry once with session from cookie, in case the session from api is invalid but cookie has valid session
            val cookieSession = getSessionFromCookie()
            val retryRequest = request.newBuilder()
                .header("X-Token", cookieSession.token)
                .header("X-Sign", cookieSession.sign)
                .build()
            response = chain.proceed(retryRequest)
        }
        return response
    }

    // because softkomik often changes their api session url,
    // if the request fails, we can try to get session from cookies but the user needs to open manga details in WebView first to get the session cookies.
    private fun getSessionFromCookie(): SessionDto {
        synchronized(this) {
            val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())

            val rawValue = cookies.firstOrNull { it.name == "x-m" }?.value ?: throw Exception("Buka manga detail di WebView untuk mendapatkan session, lalu refresh.")
            val decodedValue = runCatching { URLDecoder.decode(rawValue, Charsets.UTF_8.name()) }
                .getOrDefault(rawValue)

            val cookieSession = runCatching { json.decodeFromString<SessionDto>(decodedValue) }.getOrNull()
            if (cookieSession == null) {
                throw Exception("Buka manga detail di WebView untuk mendapatkan session, lalu refresh.")
            }
            session = cookieSession
            return cookieSession
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

            val response = client.newCall(GET("$baseUrl/api/sessions/oqiw918pa", apiHeaders)).execute()

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

    private val apiUrl = "https://v2.softdevices.my.id"
    private val coverUrl = "https://cover.softdevices.my.id/softkomik-cover"
    private val json = Json { ignoreUnknownKeys = true }
    private val userAgentMobileSafariRegex = Regex("""\s*Mobile Safari/\d+(?:\.\d+)*""", RegexOption.IGNORE_CASE)
    private val cdnUrls = listOf(
        "https://psy1.komik.im",
        "https://image.komik.im/softkomik",
        "https://cd1.softkomik.online/softkomik",
        "https://f1.softkomik.com/file/softkomik-image",
        "https://img.softdevices.my.id/softkomik-image",
        "https://image.softkomik.com/softkomik",
    )
}
