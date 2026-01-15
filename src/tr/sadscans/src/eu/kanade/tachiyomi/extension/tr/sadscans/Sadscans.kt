package eu.kanade.tachiyomi.extension.tr.sadscans

import ProofDto
import SadScansPageDto
import SadScansSearch
import SeriesDataDto
import TrpcResponse
import android.util.Base64
import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class Sadscans : HttpSource() {
    override val name = "Sadscans"
    override val baseUrl = "https://sadscans.net"
    override val lang = "tr"
    override val supportsLatest = false
    override val versionId = 2

    companion object {
        // Thread-safe storage for TRPC tokens
        private val tokenMap = ConcurrentHashMap<String, Headers>()
        private val tokenLock = Any()
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        // 1. Inject Headers
        .addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.toString().contains("/api/trpc/")) {
                return@addInterceptor chain.proceed(request)
            }

            val endpointKey = extractEndpointKey(request.url.toString()) ?: "x7k"
            val cachedHeaders = tokenMap[endpointKey]

            if (cachedHeaders != null) {
                val newRequest = request.newBuilder()
                    .removeHeader("x-reader-proof")
                    .removeHeader("x-reader-sig")
                    .removeHeader("x-reader-timestamp")
                    .removeHeader("x-reader-chapter")
                    .removeHeader("x-reader-endpoint")

                for (i in 0 until cachedHeaders.size) {
                    newRequest.addHeader(cachedHeaders.name(i), cachedHeaders.value(i))
                }
                return@addInterceptor chain.proceed(newRequest.build())
            }
            chain.proceed(request)
        }
        // 2. Update Token from Response
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.isSuccessful) {
                val nextProof = response.header("x-next-proof")
                val nextEndpoint = response.header("x-next-endpoint")

                if (nextProof != null && nextEndpoint != null) {
                    val nextSig = response.header("x-next-sig") ?: ""
                    val nextTimestamp = response.header("x-next-timestamp") ?: ""
                    val chapterId = response.request.header("x-reader-chapter") ?: "public"

                    val h = Headers.Builder()
                        .add("x-reader-proof", nextProof)
                        .add("x-reader-sig", nextSig)
                        .add("x-reader-timestamp", nextTimestamp)
                        .add("x-reader-chapter", chapterId)
                        .add("x-reader-endpoint", nextEndpoint)
                        .build()

                    tokenMap[nextEndpoint] = h
                }
            }
            response
        }
        // 3. Retry on 403/401 (Auth Fail)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if ((response.code == 403 || response.code == 401) && request.url.toString().contains("/api/trpc/")) {
                response.close()

                val endpointKey = extractEndpointKey(request.url.toString()) ?: "x7k"

                // Synchronized block ensures only one thread refreshes the token at a time
                synchronized(tokenLock) {
                    val refreshUrl = if (endpointKey == "x7k") {
                        "$baseUrl/seriler"
                    } else {
                        request.header("Referer") ?: "$baseUrl/seriler"
                    }
                    fetchTelemetryData(refreshUrl)
                }

                val newHeaders = tokenMap[endpointKey] ?: tokenMap["x7k"]
                if (newHeaders != null) {
                    val retryRequest = request.newBuilder()
                        .removeHeader("x-reader-proof")
                        .removeHeader("x-reader-sig")
                        .removeHeader("x-reader-timestamp")
                        .removeHeader("x-reader-chapter")
                        .removeHeader("x-reader-endpoint")

                    for (i in 0 until newHeaders.size) {
                        retryRequest.addHeader(newHeaders.name(i), newHeaders.value(i))
                    }
                    return@addInterceptor chain.proceed(retryRequest.build())
                }
            }
            response
        }
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    private fun extractEndpointKey(url: String): String? {
        return Regex("""/api/trpc/([a-zA-Z0-9]+)\.""").find(url)?.groupValues?.get(1)
    }

    private fun fetchTelemetryData(url: String) {
        try {
            // Add cache-buster to ensure fresh response
            val sep = if (url.contains("?")) "&" else "?"
            val finalUrl = "$url${sep}cb=${System.currentTimeMillis()}"

            val request = GET(finalUrl, headers)
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return
            }

            val document = response.asJsoup()
            val scripts = document.select("script")

            for (script in scripts) {
                val html = script.html()
                if (html.contains("nextjs-telemetry")) {
                    val base64Str = Regex("""atob\('([^']*)'\)""").find(html)?.groupValues?.get(1) ?: continue
                    val decodedJson = String(Base64.decode(base64Str, Base64.DEFAULT))
                    populateTokenMap(decodedJson)
                }
            }
        } catch (_: Exception) { }
    }

    private fun populateTokenMap(jsonStr: String) {
        try {
            // Try parsing as Map first, then single object
            try {
                val map = jsonStr.parseAs<Map<String, ProofDto>>()
                for ((key, dto) in map) {
                    if (!dto.proof.isNullOrEmpty()) {
                        createAndCacheHeaders(key, dto)
                    }
                }
            } catch (_: Exception) {
                val dto = jsonStr.parseAs<ProofDto>()
                val key = dto.endpoint ?: "x7k"
                if (!dto.proof.isNullOrEmpty()) {
                    createAndCacheHeaders(key, dto)
                }
            }
        } catch (_: Exception) { }
    }

    private fun createAndCacheHeaders(key: String, dto: ProofDto) {
        val h = Headers.Builder()
            .add("x-reader-chapter", dto.chapterId ?: "public")
            .add("x-reader-endpoint", key)
            .add("x-reader-proof", dto.proof ?: "")
            .add("x-reader-sig", dto.sig ?: "")
            .add("x-reader-timestamp", dto.timestamp?.toString() ?: "")
            .build()
        tokenMap[key] = h
    }

    // --- POPULAR ---
    override fun popularMangaRequest(page: Int): Request {
        // Reset series tokens when returning to the list to prevent state conflicts
        if (page == 1) {
            tokenMap.remove("m3p")
            tokenMap.remove("srsDtl")
        }

        if (tokenMap["x7k"] == null) {
            synchronized(tokenLock) {
                if (tokenMap["x7k"] == null) fetchTelemetryData("$baseUrl/seriler")
            }
        }

        val rawJsonInput = """{"0":{"json":{"search":null,"type":null,"status":null,"sort":"views_desc","views":null,"page":$page,"limit":15},"meta":{"values":{"search":["undefined"],"type":["undefined"],"status":["undefined"],"views":["undefined"]},"v":1}}}"""
        val url = "$baseUrl/api/trpc/x7k.getSeries".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", rawJsonInput)
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val responseData = response.parseAs<List<SadScansSearch>>()
        val dataList = responseData.firstOrNull()?.result?.data?.json ?: emptyList()
        val mangas = dataList.map { item ->
            SManga.create().apply {
                title = Jsoup.parse(item.name ?: "").text()
                url = item.href ?: ""
                thumbnail_url = item.thumb?.let { if (it.startsWith("/")) "$baseUrl$it" else it }
                author = item.author
                status = item.status.parseStatus()
            }
        }
        return MangasPage(mangas, mangas.size >= 15)
    }

    // --- SEARCH ---
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) {
            tokenMap.remove("m3p")
            tokenMap.remove("srsDtl")
        }
        if (tokenMap["x7k"] == null) {
            synchronized(tokenLock) {
                if (tokenMap["x7k"] == null) fetchTelemetryData("$baseUrl/seriler")
            }
        }
        val rawJsonInput = """{"0":{"json":{"search":"$query","page":$page,"limit":15}}}"""
        val url = "$baseUrl/api/trpc/x7k.getSeries".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", rawJsonInput)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // --- DETAILS ---
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val script = document.selectFirst("script#webpage-structured-data")?.data() ?: ""
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: document.selectFirst(".title")?.text() ?: "Bilinmiyor"
            author = document.select("span:contains(Yazar) + span").text()
            artist = author
            status = document.select("span:contains(Durum) + span").text().parseStatus()
            description = if (script.contains("\"description\":\"")) {
                script.substringAfter("\"description\":\"").substringBefore("\",")
            } else {
                document.select("div.line-clamp-6").text()
            }
            thumbnail_url = document.select("meta[property=og:image]").attr("content")
                .ifEmpty { document.select("div.relative img").attr("abs:src") }
        }
    }

    // --- CHAPTERS ---
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            // Remove list token to ensure fresh state when returning later
            tokenMap.remove("x7k")

            val slug = manga.url.trim('/').substringAfterLast('/')
            val seriesId = manga.thumbnail_url?.let { url ->
                Regex("""/series/([a-zA-Z0-9]+)/""").find(url)?.groupValues?.get(1)
            } ?: ""

            if (seriesId.isEmpty()) return@fromCallable emptyList()

            val fullUrl = if (manga.url.startsWith("http")) manga.url else "$baseUrl${manga.url}"

            // Always fetch fresh series tokens
            fetchTelemetryData(fullUrl)

            val targetEndpoint = if (tokenMap.containsKey("m3p")) "m3p" else "srsDtl"
            val allChapters = mutableListOf<SChapter>()
            var page = 1
            var totalPages = 1

            if (tokenMap[targetEndpoint] == null) fetchTelemetryData(fullUrl)

            do {
                val rawJsonInput = """{"0":{"json":{"sef":"$slug","page":$page,"limit":20}},"1":{"json":{"seriesId":"$seriesId","userId":null},"meta":{"values":{"userId":["undefined"]}}}}"""
                val url = "$baseUrl/api/trpc/$targetEndpoint.getSeriesData,reviews.getSeriesReviews".toHttpUrl().newBuilder()
                    .addQueryParameter("batch", "1")
                    .addQueryParameter("input", rawJsonInput)
                    .build()

                val response = client.newCall(GET(url, headers)).execute()
                if (!response.isSuccessful) {
                    response.close()
                    break
                }

                val trpcResponseList = response.parseAs<List<TrpcResponse>>()
                val resultData = trpcResponseList.firstOrNull()?.result?.data
                if (resultData == null) {
                    break
                }

                val seriesData = resultData.json.toString().parseAs<SeriesDataDto>()
                if (page == 1) totalPages = seriesData.pagination?.totalPages ?: 1

                val parsedChapters = seriesData.chapters?.map { dto ->
                    SChapter.create().apply {
                        val chapterNumber = dto.no?.toString()?.removeSuffix(".0") ?: ""
                        name = "Bölüm $chapterNumber" + (if (!dto.name.isNullOrEmpty()) " - ${dto.name}" else "")
                        setUrlWithoutDomain(dto.href ?: "")
                        date_upload = parseIsoDate(dto.date)
                    }
                } ?: emptyList()
                allChapters.addAll(parsedChapters)
                page++
            } while (page <= totalPages)

            allChapters
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = emptyList()

    // --- PAGES ---
    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        // Extract embedded Base64 JSON for pages
        val base64Regex = Regex("""(W3sicGFnZ[a-zA-Z0-9+/=]+)""")
        val match = base64Regex.find(html)

        if (match != null) {
            try {
                val base64Data = match.groupValues[1]
                val jsonString = String(Base64.decode(base64Data, Base64.DEFAULT))
                val pageDtoList = jsonString.parseAs<List<SadScansPageDto>>()
                return pageDtoList.mapIndexed { index, dto ->
                    val url = dto.src ?: ""
                    val fullUrl = if (url.startsWith("/")) "$baseUrl$url" else url
                    Page(index, imageUrl = fullUrl)
                }
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    private fun parseIsoDate(dateStr: String?): Long = isoDateFormat.tryParse(dateStr)
    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "devam ediyor", "ongoing" -> SManga.ONGOING
        "tamamlandı", "completed", "bitti" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
