package eu.kanade.tachiyomi.extension.pt.spectralscan

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class NexusScan : HttpSource(), ConfigurableSource {

    // SpectralScan (pt-BR) -> Nexus Scan (pt-BR)
    override val id = 5304928452449566995L

    override val lang = "pt-BR"

    override val name = "Nexus Scan"

    override val baseUrl = "https://nexustoons.site"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = super.client.newBuilder()
        .rateLimit(2)
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

    private val ajaxHeaders by lazy {
        headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Referer", "$baseUrl/biblioteca/")
            .build()
    }

    private val isoDateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)

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

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return fetchWithSync(page, popularMangaRequest(page))
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
        return fetchWithSync(page, latestUpdatesRequest(page))
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
        return fetchWithSync(page, searchMangaRequest(page, query, filters))
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

    override fun mangaDetailsParse(response: Response): SManga {
        val details = response.parseAs<MangaDetailsResponse>().manga

        return SManga.create().apply {
            title = details.title
            thumbnail_url = details.cover_url
            description = details.description
            author = details.author
            artist = details.artist
            status = details.status.parseStatus()
            genre = details.categories.joinToString { it.name }
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = getMangaSlug(manga.url)
        return GET("$baseUrl/api/manga/$slug/details/")
    }

    private fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        listOf("em andamento", "ongoing", "ativo", "lançando").any { contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("completo", "completed", "finalizado").any { contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("pausado", "hiato", "on hiatus").any { contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        listOf("cancelado", "cancelled", "dropped").any { contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ==================== Chapter =======================

    private fun getMangaSlug(url: String) = url.substringAfter("/manga/").trimEnd('/')

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { it.parseAs<ChapterListApiResponse>() }
            .flatMap { firstPage ->
                val chapters = firstPage.chapters.map { parseChapter(it) }

                if (!firstPage.pagination.has_next) {
                    Observable.just(chapters)
                } else {
                    fetchRemainingChaptersApi(getMangaSlug(manga.url), chapters, firstPage.pagination.next_page!!)
                }
            }
    }

    private fun fetchRemainingChaptersApi(
        slug: String,
        accumulatedChapters: List<SChapter>,
        currentPage: Int,
    ): Observable<List<SChapter>> {
        val url = "$baseUrl/api/manga/$slug/chapters/?page=$currentPage&sort=desc&q="

        return client.newCall(GET(url)).asObservableSuccess()
            .map { it.parseAs<ChapterListApiResponse>() }
            .flatMap { page ->
                val allChapters = accumulatedChapters + page.chapters.map { parseChapter(it) }

                if (page.pagination.has_next) {
                    fetchRemainingChaptersApi(slug, allChapters, page.pagination.next_page!!)
                } else {
                    Observable.just(allChapters)
                }
            }
    }

    private fun parseChapter(chapter: ChapterApi): SChapter {
        return SChapter.create().apply {
            val title = if (chapter.title.isNotEmpty()) {
                "${chapter.title} ${chapter.number}"
            } else {
                "Capítulo ${chapter.number}"
            }
            name = title
            setUrlWithoutDomain(chapter.url)
            val published = chapter.published_at
            date_upload = isoDateTimeFormat.tryParse(published)
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = getMangaSlug(manga.url)
        return GET("$baseUrl/api/manga/$slug/chapters/?page=1&sort=desc&q=")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return emptyList()
    }

    // ==================== Page ==========================

    override fun pageListParse(response: Response): List<Page> {
        val pageData = response.asJsoup()
            .selectFirst("script[id^=d-][type='application/json']")?.data()
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
