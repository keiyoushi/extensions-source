package eu.kanade.tachiyomi.extension.ko.ntk

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

abstract class NTKBase(
    override val name: String,
    protected val contentKind: String,
) : HttpSource(),
    ConfigurableSource {

    private val json = Json { ignoreUnknownKeys = true }

    protected val apiHeaders by lazy {
        headers.newBuilder()
            .set("Accept", "application/json")
            .build()
    }

    override val lang = "ko"
    override val supportsLatest = true
    protected val preferences by lazy { getPreferences() }

    protected val rootUrl: String
        get() {
            val domainNumber = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!!
            return "https://ntk$domainNumber.com"
        }

    protected open val webViewPath: String get() = contentKind
    override val baseUrl: String get() = "$rootUrl/$webViewPath"

    override fun mangaDetailsRequest(manga: SManga) = GET(rootUrl + manga.url, headers)
    override fun chapterListRequest(manga: SManga) = GET(rootUrl + manga.url, headers)
    override fun pageListRequest(chapter: SChapter) = GET(rootUrl + chapter.url, headers)

    private val headerCleanerInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .removeHeader("rsc")
            .removeHeader("next-router-state-tree")
            .removeHeader("next-url")

        if (originalRequest.header("Accept") == null) {
            requestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        }

        chain.proceed(requestBuilder.build())
    }

    private val domainUpdateInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        val finalUrl = response.request.url.toString()
        val regex = """ntk(\d+)\.com""".toRegex()
        val matchResult = regex.find(finalUrl)

        if (matchResult != null) {
            val newDomainNumber = matchResult.groupValues[1]
            val currentDomainNumber = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)
            if (newDomainNumber != currentDomainNumber) {
                preferences.edit().putString(PREF_DOMAIN_KEY, newDomainNumber).apply()
            }
        }
        response
    }

    private var lastImageRequestTime = 0L
    private val smartRateLimitInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()

        val isImage = url.contains("toonflix.app") ||
            url.contains("11toon8.com") ||
            url.endsWith(".jpg") ||
            url.endsWith(".png") ||
            url.endsWith(".webp")

        val isDownload = request.header("X-Download") != null

        if (isImage && isDownload) {
            val rateLimitSeconds = preferences.getString(PREF_RATELIMIT_KEY, PREF_RATELIMIT_DEFAULT)!!.toLong()
            if (rateLimitSeconds > 0) {
                val delayMillis = rateLimitSeconds * 1000L
                synchronized(this) {
                    val now = System.currentTimeMillis()
                    val timeToWait = delayMillis - (now - lastImageRequestTime)
                    if (timeToWait > 0) Thread.sleep(timeToWait)
                    lastImageRequestTime = System.currentTimeMillis()
                }
            }
        }
        chain.proceed(request)
    }

    private val webViewRedirectInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val isExactRoot = url == rootUrl || url == "$rootUrl/"
        if (isExactRoot) {
            chain.proceed(request.newBuilder().url("$rootUrl/$webViewPath").build())
        } else {
            chain.proceed(request)
        }
    }

    private val viewModeCookieInterceptor = Interceptor { chain ->
        val request = chain.request()
        if ("/updates" in request.url.encodedPath) {
            val existing = request.header("Cookie") ?: ""
            val updated = request.newBuilder()
                .header("Cookie", if (existing.isEmpty()) "ntk_view_mode=page" else "$existing; ntk_view_mode=page")
                .build()
            chain.proceed(updated)
        } else {
            chain.proceed(request)
        }
    }

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .addInterceptor(headerCleanerInterceptor)
            .addInterceptor(domainUpdateInterceptor)
            .addInterceptor(smartRateLimitInterceptor)
            .addInterceptor(webViewRedirectInterceptor)
            .addNetworkInterceptor(viewModeCookieInterceptor)
            .build()
    }

//    override fun popularMangaParse(response: Response): MangasPage {
//        val document = response.asJsoup()
//        val mangas = document.select("div.card-grid > a.card").map { element ->
//            SManga.create().apply {
//                setUrlWithoutDomain(element.attr("href"))
//                title = element.select("p.subject").text()
//                thumbnail_url = element.select("div.thumb img:not(.platform-icon)").attr("abs:src")
//            }
//        }
//        return MangasPage(mangas, hasNextPage = false)
//    }

    protected fun htmlCardParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.card-grid > a.card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.select("p.subject").text()
                thumbnail_url = element.select("div.thumb img:not(.platform-icon)").attr("abs:src")
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // New — replaces the old popularMangaParse
    override fun popularMangaParse(response: Response): MangasPage {
        val data = json.parseToJsonElement(response.body.string()).jsonObject
        val mangas = data["works"]!!.jsonArray.map {
            val work = it.jsonObject
            SManga.create().apply {
                url = "/$contentKind/${work["sourceWorkId"]!!.jsonPrimitive.content}"
                title = work["title"]!!.jsonPrimitive.content
                thumbnail_url = work["thumbnailUrl"]?.jsonPrimitive?.content
                genre = work["genre"]?.jsonPrimitive?.content
            }
        }
        return MangasPage(mangas, data["hasMore"]!!.jsonPrimitive.boolean)
    }

//    override fun latestUpdatesParse(response: Response): MangasPage {
//        val document = response.asJsoup()
//        val mangas = document.select("ul.upd-grid > li.upd-card").map { element ->
//            SManga.create().apply {
//                val sid = element.attr("data-manhwa-sid")
//                if (sid.isNotEmpty()) {
//                    setUrlWithoutDomain("$rootUrl/$contentKind/$sid")
//                } else {
//                    val allBtnHref = element.select("a.upd-allbtn").attr("abs:href")
//                    if (allBtnHref.isNotEmpty()) {
//                        setUrlWithoutDomain(allBtnHref)
//                    } else {
//                        val epHref = element.select("a.upd-card-main").attr("href")
//                        url = "/" + epHref.trim('/').split("/").take(2).joinToString("/")
//                    }
//                }
//                title = element.select("div.upd-title").text()
//                thumbnail_url = element.select("div.upd-thumb img").attr("abs:src")
//            }
//        }
//        return MangasPage(mangas, hasNextPage = false)
//    }

//    override fun latestUpdatesParse(response: Response): MangasPage {
//        val document = response.asJsoup()
//        val items = document.select("ul.upd-grid > li.upd-card")
//        val mangas = items.map { element ->
//            SManga.create().apply {
//                val sid = element.attr("data-manhwa-sid")
//                if (sid.isNotEmpty()) {
//                    setUrlWithoutDomain("$rootUrl/$contentKind/$sid")
//                } else {
//                    val allBtnHref = element.select("a.upd-allbtn").attr("abs:href")
//                    if (allBtnHref.isNotEmpty()) {
//                        setUrlWithoutDomain(allBtnHref)
//                    } else {
//                        val epHref = element.select("a.upd-card-main").attr("href")
//                        url = "/" + epHref.trim('/').split("/").take(2).joinToString("/")
//                    }
//                }
//                title = element.select("div.upd-title").text()
//                thumbnail_url = element.select("div.upd-thumb img").attr("abs:src")
//            }
//        }
//        return MangasPage(mangas, hasNextPage = items.size >= PAGE_SIZE)
//    }

//    override fun latestUpdatesParse(response: Response): MangasPage {
//        val document = response.asJsoup()
//        val items = document.select("ul.upd-grid > li.upd-card")
//        val mangas = items.map { element ->
//            SManga.create().apply {
//                val sid = element.attr("data-manhwa-sid")
//                if (sid.isNotEmpty()) {
//                    setUrlWithoutDomain("$rootUrl/$contentKind/$sid")
//                } else {
//                    val allBtnHref = element.select("a.upd-allbtn").attr("abs:href")
//                    if (allBtnHref.isNotEmpty()) {
//                        setUrlWithoutDomain(allBtnHref)
//                    } else {
//                        val epHref = element.select("a.upd-card-main").attr("href")
//                        url = "/" + epHref.trim('/').split("/").take(2).joinToString("/")
//                    }
//                }
//                title = element.select("div.upd-title").text()
//                thumbnail_url = element.select("div.upd-thumb img").attr("abs:src")
//            }
//        }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        // All 200 update entries are embedded in the RSC payload on initial load
        val rscData = document.select("script")
            .map { it.data() }
            .firstOrNull { "allCards" in it }
            ?: return MangasPage(emptyList(), false)

        // Extract JSON string content between push([1," and "])
        val rawContent = rscData
            .substringAfter("[1,\"")
            .substringBeforeLast("\"])")

        // Unescape JavaScript string encoding — \\ must come before \"
        val unescaped = rawContent
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\/", "/")

        // Extract the allCards JSON array
        val marker = "\"allCards\":"
        val markerIdx = unescaped.indexOf(marker)
        if (markerIdx < 0) return MangasPage(emptyList(), false)

        val arrayStart = markerIdx + marker.length
        var depth = 0
        var arrayEnd = arrayStart
        for (i in arrayStart until unescaped.length) {
            when (unescaped[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        arrayEnd = i + 1
                        break
                    }
                }
            }
        }

        val cards = json.parseToJsonElement(unescaped.substring(arrayStart, arrayEnd)).jsonArray

        // Deduplicate by sourceWorkId — same series can appear multiple times
        val seen = mutableSetOf<String>()
        val mangas = cards.mapNotNull {
            val card = it.jsonObject
            val sid = card["sourceWorkId"]!!.jsonPrimitive.content
            if (seen.add(sid)) {
                SManga.create().apply {
                    url = "/$contentKind/$sid"
                    title = card["workTitle"]!!.jsonPrimitive.content
                    thumbnail_url = card["thumbnailUrl"]?.jsonPrimitive?.content
                    genre = card["genre"]?.jsonPrimitive?.content
                    author = card["author"]?.jsonPrimitive?.content
                }
            } else {
                null
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

//    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val contentType = response.header("Content-Type") ?: ""
        return if (contentType.contains("application/json")) {
            popularMangaParse(response)
        } else {
            htmlCardParse(response)
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select("h1.hero-v2-title").text()
            author = document.select("div.hero-v2-author a").text()
            description = document.select("p.hero-v2-desc").text()
            thumbnail_url = document.select("div.hero-v2-thumb img").attr("abs:src")

            val statusText = document.select("span.pill-status").text()
            status = when {
                statusText.contains("연재중") -> SManga.ONGOING
                statusText.contains("완결") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            genre = document.select("a.hero-v2-tag").joinToString(", ") {
                it.text().replace("#", "").trim()
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.ep-list-v2 > li.ep-row-v2").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.select("a.ep-row-v2-link").attr("href"))
                name = element.select("div.ep-row-v2-title strong").text()
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.vw-imgs img").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = "도메인 번호 (ntkOOO.com)"
            summary = "현재 도메인 번호: ${preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)}\n숫자만 입력하세요 (예: 01, 02, 300)"
            setDefaultValue(PREF_DOMAIN_DEFAULT)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_RATELIMIT_KEY
            title = "다운로드 속도 제한"
            summary = "현재 설정: ${preferences.getString(PREF_RATELIMIT_KEY, PREF_RATELIMIT_DEFAULT)}초마다 1장\n※ 다운로드할 때만 적용 (읽기 중에는 영향 없음)"
            entries = arrayOf("제한 없음 (최고속)", "1초마다 다운로드", "2초마다 다운로드", "3초마다 다운로드")
            entryValues = arrayOf("0", "1", "2", "3")
            setDefaultValue(PREF_RATELIMIT_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_DOMAIN_KEY = "pref_domain_key"
        private const val PREF_DOMAIN_DEFAULT = "01"
        private const val PREF_RATELIMIT_KEY = "pref_ratelimit_key"
        private const val PREF_RATELIMIT_DEFAULT = "3"

        const val PAGE_SIZE = 49
    }
}
