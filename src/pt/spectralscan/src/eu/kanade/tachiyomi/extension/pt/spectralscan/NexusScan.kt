package eu.kanade.tachiyomi.extension.pt.spectralscan

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.io.IOException

class NexusScan : HttpSource(), ConfigurableSource {

    // SpectralScan (pt-BR) -> Nexus Scan (pt-BR)
    override val id = 5304928452449566995L

    override val lang = "pt-BR"

    override val name = "Nexus Scan"

    override val baseUrl = "https://nexustoons.site"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addNetworkInterceptor(CookieInterceptor("nexustoons.site", emptyList()))
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val path = response.request.url.encodedPath

            if (path.startsWith("/accounts/login") || path.startsWith("/login")) {
                response.close()
                throw IOException("Faça o login na WebView para acessar o conteúdo")
            }

            response
        }
        .build()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

    private val dateNumberRegex = Regex("""\d+""")

    private val ajaxHeaders by lazy {
        headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Referer", "$baseUrl/biblioteca/")
            .build()
    }

    private var currentMangaSlug: String? = null

    // ==================== AJAX Manga List ==========================

    private fun parseMangaListResponse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val document = Jsoup.parse(result.html, baseUrl)

        val mangas = document.select("a.content-card[href*='/manga/']").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".font-semibold")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, result.has_next)
    }

    // ==================== Popular ==========================

    private fun fetchWithSync(page: Int, request: Request): Observable<MangasPage> {
        syncNSFW()
        return client.newCall(request).asObservableSuccess()
            .map { parseMangaListResponse(it) }
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/ajax/load-mangas/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popular")
            .addQueryParameter("q", "")
            .addQueryParameter("genre", "")
            .addQueryParameter("type", "")
            .addQueryParameter("view", "grid")
            .build()
        return GET(url, ajaxHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return parseMangaListResponse(response)
    }

    // ==================== Latest ==========================

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        syncNSFW()
        return super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/ajax/load-mangas/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "latest")
            .addQueryParameter("q", "")
            .addQueryParameter("genre", "")
            .addQueryParameter("type", "")
            .addQueryParameter("view", "grid")
            .build()
        return GET(url, ajaxHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return parseMangaListResponse(response)
    }

    // ==================== Search ==========================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        syncNSFW()
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var sortValue = "popular"
        var genreValue = ""
        var typeValue = ""

        filters.forEach { filter ->
            when (filter) {
                is SelectFilter -> {
                    val value = filter.selected()
                    if (value.isNotEmpty()) {
                        when (filter.parameter) {
                            "sort" -> sortValue = value
                            "genre" -> genreValue = value
                            "type" -> typeValue = value
                        }
                    }
                }
                else -> {}
            }
        }

        val url = "$baseUrl/ajax/load-mangas/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", sortValue)
            .addQueryParameter("q", query)
            .addQueryParameter("genre", genreValue)
            .addQueryParameter("type", typeValue)
            .addQueryParameter("view", "grid")
            .build()

        return GET(url, ajaxHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return parseMangaListResponse(response)
    }

    // ==================== Details =======================

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        currentMangaSlug = getMangaSlug(manga.url)
        return client.newCall(GET("$baseUrl${manga.url}", headers)).asObservableSuccess()
            .map { response ->
                val document = response.asJsoup()
                val script = document.selectFirst("script[id=page-api-urls]")?.data()
                val urls = script?.parseAs<Map<String, String>>() ?: emptyMap()

                val apiUrl = urls["mangaDetails"]!!
                val finalApiUrl = if (apiUrl.startsWith("http")) apiUrl else "$baseUrl$apiUrl"

                val headers = headersBuilder()
                    .set("Referer", "$baseUrl${manga.url}")
                    .build()

                client.newCall(GET(finalApiUrl, headers)).execute()
                    .parseAs<MangaDetailsResponse>()
                    .manga.toSManga(currentMangaSlug!!)
            }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException("Not used")
    }

    // ==================== Chapter =======================

    private fun getMangaSlug(url: String) = url.substringAfter("/manga/").trimEnd('/')

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(GET("$baseUrl${manga.url}", headers)).asObservableSuccess()
            .flatMap { response ->
                val document = response.asJsoup()
                val script = document.selectFirst("script[id=page-api-urls]")?.data()
                val urls = script?.parseAs<Map<String, String>>() ?: emptyMap()

                val chaptersApiUrl = urls["chaptersApi"]!!
                val finalApiUrl = if (chaptersApiUrl.startsWith("http")) chaptersApiUrl else "$baseUrl$chaptersApiUrl"

                val referer = "$baseUrl${manga.url}"
                val headers = headersBuilder()
                    .set("Referer", referer)
                    .build()

                client.newCall(GET("$finalApiUrl?page=1&sort=desc&q=", headers)).asObservableSuccess()
                    .map { it.parseAs<ChapterListApiResponse>() }
                    .flatMap { firstPage ->
                        val chapters = firstPage.chapters.map { parseChapter(it) }

                        if (!firstPage.pagination.has_next) {
                            Observable.just(chapters)
                        } else {
                            fetchRemainingChaptersApi(chapters, firstPage.pagination.next_page!!, finalApiUrl, headers)
                        }
                    }
            }
    }

    private fun fetchRemainingChaptersApi(
        accumulatedChapters: List<SChapter>,
        nextPage: Int,
        chaptersApiUrl: String,
        headers: Headers,
    ): Observable<List<SChapter>> {
        val url = "$chaptersApiUrl?page=$nextPage&sort=desc&q="

        return client.newCall(GET(url, headers)).asObservableSuccess()
            .map { it.parseAs<ChapterListApiResponse>() }
            .flatMap { page ->
                val allChapters = accumulatedChapters + parseChaptersFromHtml(page.chapters_html)

                if (page.pagination.has_next) {
                    fetchRemainingChaptersApi(allChapters, page.pagination.next_page!!, chaptersApiUrl, headers)
                } else {
                    Observable.just(allChapters)
                }
            }
    }

    private fun parseChapter(chapter: ChapterApi): SChapter {
        return chapter.toSChapter()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = getMangaSlug(manga.url)
        val url = "$baseUrl/ajax/load-chapters/".toHttpUrl().newBuilder()
            .addQueryParameter("item_slug", slug)
            .addQueryParameter("page", "1")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("q", "")
            .build()
        return GET(url, ajaxHeaders)
    }

    override fun chapterListParse(response: Response) =
        parseChaptersFromHtml(response.parseAs<ChapterListResponse>().chapters_html)

    private fun parseChapterDate(dateStr: String): Long {
        val value = dateNumberRegex.find(dateStr)?.value?.toIntOrNull() ?: 1
        return when {
            "hoje" in dateStr -> Calendar.getInstance().timeInMillis
            "ontem" in dateStr -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
            "hora" in dateStr -> Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -value) }.timeInMillis
            "dia" in dateStr -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -value) }.timeInMillis
            "semana" in dateStr -> Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -value) }.timeInMillis
            "mês" in dateStr || "mes" in dateStr -> Calendar.getInstance().apply { add(Calendar.MONTH, -value) }.timeInMillis
            else -> dateFormat.tryParse(dateStr)
        }
    }

    // ==================== Page ==========================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val pageData = document.selectFirst("script[id^=d-][type='application/json']")?.data()
            ?: return emptyList()

        return pageData.parseAs<List<PageData>>().mapIndexed { index, page ->
            Page(index, imageUrl = page.image_url)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, headers)
    }

    // ==================== Filters ==========================

    override fun getFilterList() = FilterList(
        SelectFilter("Ordenar Por", "sort", sortList),
        SelectFilter("Gênero", "genre", genreList),
        SelectFilter("Tipo", "type", typeList),
    )

    // ==================== Settings ==========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ADULT_KEY
            title = "Exibir conteúdo adulto (+18)"
            summary = "Habilita a visualização de conteúdo adulto nas listas."
            setDefaultValue(PREF_ADULT_DEFAULT)
        }.also(screen::addPreference)
    }

    private fun syncNSFW() {
        try {
            val baseHttpUrl = baseUrl.toHttpUrl()
            var cookies = client.cookieJar.loadForRequest(baseHttpUrl)

            if (cookies.none { it.name == "csrftoken" }) {
                client.newCall(GET(baseUrl, headers)).execute().close()
                cookies = client.cookieJar.loadForRequest(baseHttpUrl)
            }

            val csrfToken = cookies
                .firstOrNull { it.name == "csrftoken" }
                ?.value
                ?: return

            val hasSession = cookies.any { it.name == "sessionid" }
            val isAdultActive = preferences.getBoolean(PREF_ADULT_KEY, PREF_ADULT_DEFAULT)

            if (!hasSession && !isAdultActive) return

            val lastState = preferences.getBoolean(PREF_ADULT_SYNCED_KEY, !isAdultActive)
            if (hasSession && lastState == isAdultActive) return

            val body = """{"is_adult_active":$isAdultActive}"""
                .toRequestBody("application/json".toMediaType())

            val request = POST(
                "$baseUrl/ajax/toggle-adult-content/",
                headers.newBuilder()
                    .set("X-CSRFToken", csrfToken)
                    .set("Referer", "$baseUrl/")
                    .set("X-Requested-With", "XMLHttpRequest")
                    .build(),
                body,
            )

            val response = client.newCall(request).execute()
            response.close()

            preferences.edit().putBoolean(PREF_ADULT_SYNCED_KEY, isAdultActive).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PREF_ADULT_KEY = "pref_adult_content"
        private const val PREF_ADULT_DEFAULT = false
        private const val PREF_ADULT_SYNCED_KEY = "pref_adult_synced"
    }
}
