package eu.kanade.tachiyomi.extension.en.allanime

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
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
import kotlinx.serialization.json.float
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AllManga : ConfigurableSource, HttpSource() {

    override val name = "AllManga"

    override val baseUrl = "https://allmanga.to"

    private val apiUrl = "https://api.allanime.day/api"

    override val lang = "en"

    override val id = 4709139914729853090

    override val supportsLatest = true

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    /* Popular */
    override fun popularMangaRequest(page: Int): Request {
        val payload = GraphQL(
            PopularVariables(
                type = "manga",
                size = LIMIT,
                dateRange = 0,
                page = page,
                allowAdult = preferences.allowAdult,
                allowUnknown = false,
            ),
            POPULAR_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiPopularResponse>()

        val mangaList = result.data.popular.mangas
            .mapNotNull { it.manga?.toSManga() }

        val hasNextPage = result.data.popular.mangas.size == LIMIT

        return MangasPage(mangaList, hasNextPage)
    }

    /* Latest */
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList())

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    /* Search */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val url = "/manga/${query.substringAfter(SEARCH_PREFIX)}/"
        return fetchMangaDetails(SManga.create().apply { this.url = url }).map {
            MangasPage(listOf(it), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = GraphQL(
            SearchVariables(
                search = SearchPayload(
                    query = query.takeUnless { it.isEmpty() },
                    sortBy = filters.firstInstanceOrNull<SortFilter>()?.getValue(),
                    genres = filters.firstInstanceOrNull<GenreFilter>()?.included,
                    excludeGenres = filters.firstInstanceOrNull<GenreFilter>()?.excluded,
                    isManga = true,
                    allowAdult = preferences.allowAdult,
                    allowUnknown = false,
                ),
                size = LIMIT,
                page = page,
                translationType = "sub",
                countryOrigin = filters.firstInstanceOrNull<CountryFilter>()?.getValue() ?: "ALL",
            ),
            SEARCH_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiSearchResponse>()

        val mangaList = result.data.mangas.edges
            .map(SearchManga::toSManga)

        val hasNextPage = result.data.mangas.edges.size == LIMIT

        return MangasPage(mangaList, hasNextPage)
    }

    override fun getFilterList() = getFilters()

    /* Details */
    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.split("/")[2]

        val payload = GraphQL(
            IDVariables(mangaId),
            DETAILS_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ApiMangaDetailsResponse>()

        return result.data.manga.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    /* Chapters */
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.split("/")[2]

        val payload = GraphQL(
            ChapterListVariables(
                id = "manga@$mangaId",
                chapterNumStart = 0f,
                chapterNumEnd = 9999f,
            ),
            CHAPTERS_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val result = response.parseAs<ApiChapterListResponse>()

        val chapters = result.data.chapterList?.sortedByDescending { it.chapterNum.float }
            ?: return emptyList()

        val mangaUrl = manga.url.substringAfter("/manga/")

        return chapters.map { it.toSChapter(mangaUrl) }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException("Not used")
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    /* Pages */
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.split("/")
        val mangaId = chapterUrl[2]
        val chapterNo = chapterUrl[4].split("-")[1]

        val payload = GraphQL(
            PageListVariables(
                id = mangaId,
                chapterNum = chapterNo,
                translationType = "sub",
            ),
            PAGE_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ApiPageListResponse>()
        val pages = result.data.pageList?.edges?.get(0) ?: return emptyList()

        val imageDomain = pages.serverUrl?.let { server ->
            if (server.matches(urlRegex)) {
                server
            } else {
                "https://$server"
            }
        } ?: return emptyList()

        return pages.pictureUrls?.mapIndexed { index, image ->
            Page(
                index = index,
                imageUrl = "$imageDomain${image.url}",
            )
        } ?: emptyList()
    }

    override fun imageRequest(page: Page): Request {
        val quality = preferences.imageQuality

        if (quality == IMAGE_QUALITY_PREF_DEFAULT) {
            return super.imageRequest(page)
        }

        val oldUrl = imageQualityRegex.find(page.imageUrl!!)!!.groupValues[1]
        val newUrl = "$IMAGE_CDN/$oldUrl?w=$quality"

        return GET(newUrl, headers)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = IMAGE_QUALITY_PREF
            title = "Image Quality"
            entries = arrayOf("Original", "Wp-800", "Wp-480")
            entryValues = arrayOf("original", "800", "480")
            setDefaultValue(IMAGE_QUALITY_PREF_DEFAULT)
            summary = "Warning: Wp quality servers can be slow and might not work sometimes"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_ADULT_PREF
            title = "Show Adult Content"
            setDefaultValue(SHOW_ADULT_PREF_DEFAULT)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.allowAdult
        get() = getBoolean(SHOW_ADULT_PREF, SHOW_ADULT_PREF_DEFAULT)

    private val SharedPreferences.imageQuality
        get() = getString(IMAGE_QUALITY_PREF, IMAGE_QUALITY_PREF_DEFAULT)!!

    companion object {
        private const val LIMIT = 20
        const val SEARCH_PREFIX = "id:"
        val urlRegex = Regex("^https?://.*")
        private const val IMAGE_CDN = "https://wp.youtube-anime.com"
        private val imageQualityRegex = Regex("^https?://([^#]+)")

        private const val SHOW_ADULT_PREF = "pref_adult"
        private const val SHOW_ADULT_PREF_DEFAULT = false
        private const val IMAGE_QUALITY_PREF = "pref_quality"
        private const val IMAGE_QUALITY_PREF_DEFAULT = "original"
    }
}
