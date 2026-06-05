package eu.kanade.tachiyomi.extension.en.philiascans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class PhiliaScans :
    HttpSource(),
    ConfigurableSource {
    override val name = "Philia Scans"
    private val domain = "philiascans.org"
    override val baseUrl = "https://$domain"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId = 5

    private val apiUrl = "$baseUrl/api"
    private val preferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 2 }, OrderFilter()))

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesResponse>()
        val mangas = result.items.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, result.hasNextPage())
    }

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortFilter(), OrderFilter()))

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "20")
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query)
                addFilter("orderby", filters.firstInstanceOrNull<SortFilter>())
                addFilter("order", filters.firstInstanceOrNull<OrderFilter>())
                addFilter("types", filters.firstInstanceOrNull<TypeFilter>())
                addFilter("statuses", filters.firstInstanceOrNull<StatusFilter>())
                addFilter("genres", filters.firstInstanceOrNull<GenreFilter>())
            }
        return GET(url.build(), headers)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        SortFilter(),
        OrderFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().toSManga(baseUrl)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}/chapters", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val slug = response.request.url.pathSegments[2]
        return response.parseAs<ChapterResponse>().items
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl().pathSegments
        val mangaSlug = parts.first()
        val chapterSlug = parts.last()
        return GET("$apiUrl/manga/$mangaSlug/chapters/$chapterSlug", headers)
    }

    private val tokenHeaders = headersBuilder()
        .set("Accept", "application/json")
        .set("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7,ja;q=0.6")
        .set("Sec-Fetch-Mode", "cors")
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    override fun pageListParse(response: Response): List<Page> {
        val token = client.newCall(POST("$apiUrl/reader/access-token", tokenHeaders)).execute().parseAs<TokenResponse>().token
        val readerHeaders = tokenHeaders.newBuilder().add("X-Reader-Access-Token", token).build()

        val result = response.parseAs<ViewerResponse>()
        if (!result.hasAccess) throw Exception("Log in via Webview and purchased this chapter to read.")

        val isScrambled = if (result.chapter.scrambled) "1" else "0"
        val pageKeyResponse = client.newCall(GET("$apiUrl/chapters/${result.chapter.id}/page-keys", readerHeaders)).execute().parseAs<PageKeys>()
        val openResponse = client.newCall(POST("$apiUrl/chapters/${result.chapter.id}/open", readerHeaders)).execute().parseAs<OpenResponse>()
        val drmResponse = client.newCall(GET("$apiUrl/chapters/${result.chapter.id}/get-drm?session=${openResponse.sessionId}", readerHeaders)).execute().parseAs<DrmResponse>()

        return result.chapter.pages.sortedBy { it.position }.mapIndexed { i, page ->
            val imageUrl = if (page.url.startsWith("http")) page.url else "$baseUrl/${page.url}"
            Page(i, imageUrl = "$imageUrl#$isScrambled;${page.mime};${pageKeyResponse.chapterKeyB64};${pageKeyResponse.gridSize};${openResponse.payloadA};${drmResponse.payloadB};$i")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            summary = "Hide chapters that require coins to read."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
