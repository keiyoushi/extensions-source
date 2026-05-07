package eu.kanade.tachiyomi.extension.ko.newtoki

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NewToki :
    ParsedHttpSource(),
    ConfigurableSource {
    override val name = "NewToki"
    override val lang = "ko"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val rootUrl: String
        get() {
            val domainNumber = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!!
            return "https://ntk$domainNumber.com"
        }

    override val baseUrl: String get() = rootUrl

    private val headerCleanerInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .removeHeader("rsc")
            .removeHeader("next-router-state-tree")
            .removeHeader("next-url")
            .build()
        chain.proceed(request)
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
            chain.proceed(request.newBuilder().url("$rootUrl/manhwa").build())
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
            .build()
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhwa", headers)

    override fun popularMangaSelector() = "div.card-grid > a.card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.select("p.subject").text().trim()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        return MangasPage(mangas, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manhwa/updates", headers)

    override fun latestUpdatesSelector() = "ul.upd-grid > li.upd-card"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val sid = element.attr("data-manhwa-sid")
        if (sid.isNotEmpty()) {
            setUrlWithoutDomain("$rootUrl/manhwa/$sid")
        } else {
            val allBtnHref = element.select("a.upd-allbtn").attr("abs:href")
            if (allBtnHref.isNotEmpty()) {
                setUrlWithoutDomain(allBtnHref)
            } else {
                val epHref = element.select("a.upd-card-main").attr("href")
                url = "/" + epHref.trim('/').split("/").take(2).joinToString("/")
            }
        }
        title = element.select("div.upd-title").text().trim()
        thumbnail_url = element.select("div.upd-thumb img").attr("abs:src")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        return MangasPage(mangas, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("q", query)
                addQueryParameter("kind", "manhwa") // Fix 1: Added kind=manhwa to search
            }.build()
            return GET(url, headers)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        val sortParam = sortFilter?.let { sortList[it.state].value } ?: sortList[0].value
        val statusParam = statusFilter?.let { statusList[it.state].value } ?: statusList[0].value
        val genreParam = buildGenreParam(genreFilter)

        val url = "$baseUrl/manhwa$statusParam".toHttpUrl().newBuilder().apply {
            // Add sort parameter if not default
            if (sortParam != "new") {
                addQueryParameter("sort", sortParam)
            }

            genreParam?.let { addQueryParameter("g", it) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        return MangasPage(mangas, hasNextPage = false)
    }

    override fun popularMangaNextPageSelector() = null
    override fun latestUpdatesNextPageSelector() = null
    override fun searchMangaNextPageSelector() = null

    // --- FILTERS ---
    override fun getFilterList() = FilterList(
        Filter.Header("필터는 검색창이 비어있을 때만 적용됩니다"),
        Filter.Separator(),
        SortFilter(),
        StatusFilter(),
        Filter.Separator(),
        GenreFilter(),
    )

    // --- MANGA DETAILS ---
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1.hero-v2-title").text().trim()
        author = document.select("div.hero-v2-author a").text().trim()
        description = document.select("p.hero-v2-desc").text().trim()
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

    // --- CHAPTER LIST ---
    override fun chapterListSelector() = "ul.ep-list-v2 > li.ep-row-v2"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a.ep-row-v2-link").attr("href"))
        name = element.select("div.ep-row-v2-title strong").text().trim()
    }

    // --- PAGES ---
    override fun pageListParse(document: Document): List<Page> = document.select("div.vw-imgs img").mapIndexed { i, img ->
        Page(i, "", img.attr("abs:src"))
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    // --- SETTINGS ---
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val domainPref = EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = "도메인 번호 (ntkOOO.com)"
            summary = "현재 도메인 번호: ${preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)}\n숫자만 입력하세요 (예: 01, 02, 300)"
            setDefaultValue(PREF_DOMAIN_DEFAULT)
            dialogTitle = "도메인 번호를 입력하세요"
            setOnPreferenceChangeListener { _, newValue ->
                summary = "현재 도메인 번호: $newValue\n숫자만 입력하세요 (예: 01, 02, 300)"
                true
            }
        }
        screen.addPreference(domainPref)

        val rateLimitPref = ListPreference(screen.context).apply {
            key = PREF_RATELIMIT_KEY
            title = "다운로드 속도 제한"
            summary = "현재 설정: ${preferences.getString(PREF_RATELIMIT_KEY, PREF_RATELIMIT_DEFAULT)}초마다 1장\n" +
                "※ 다운로드할 때만 적용 (읽기 중에는 영향 없음)\n" +
                "(설정 변경 시 앱 재시작 필요)"
            entries = arrayOf(
                "제한 없음 (최고속)",
                "1초마다 다운로드",
                "2초마다 다운로드",
                "3초마다 다운로드",
            )
            entryValues = arrayOf("0", "1", "2", "3")
            setDefaultValue(PREF_RATELIMIT_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                val display = when (newValue.toString()) {
                    "0" -> "제한 없음 (최고속)"
                    else -> "${newValue}초마다 1장"
                }
                summary = "현재 설정: $display\n※ 다운로드할 때만 적용 (읽기 중에는 영향 없음)\n(설정 변경 시 앱 재시작 필요)"
                true
            }
        }
        screen.addPreference(rateLimitPref)
    }

    companion object {
        private const val PREF_DOMAIN_KEY = "pref_domain_key"
        private const val PREF_DOMAIN_DEFAULT = "01"

        private const val PREF_RATELIMIT_KEY = "pref_ratelimit_key"
        private const val PREF_RATELIMIT_DEFAULT = "0"
    }
}
