package eu.kanade.tachiyomi.extension.es.shadowmanga

import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.es.shadowmanga.interceptor.ImageFallbackInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ShadowManga :
    HttpSource(),
    ConfigurableSource {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    private val scope = CoroutineScope(Dispatchers.IO)

    private val preferences by getPreferencesLazy()

    private val isAdultSection: Boolean
        get() = preferences.getString(PREF_SECTION, SECTION_SFW) == SECTION_NSFW

    private val cdnHosts = listOf(
        "media.shademanga.com",
        "cdn.shademanga.com",
    )

    private val fallbackPrefix = "/api/media/"

    override val client = network.client.newBuilder()
        .addInterceptor(ImageFallbackInterceptor(cdnHosts, baseUrlHost, fallbackPrefix))
        .rateLimit(2, 1.seconds, 500.milliseconds) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun getAdultPageToken(page: Int, pageSize: Int = 24): String {
        val half = pageSize / 2
        val lo = (page - 1) * half
        val so = (page - 1) * half
        val json = """{"lo":$lo,"so":$so}"""
        val b64 = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "cx_$b64"
    }

    override fun popularMangaRequest(page: Int): Request = if (isAdultSection) {
        val url = "$baseUrl/api/series-locales/adultos".toHttpUrl().newBuilder()
            .addQueryParameter("orden", "vistos")
            .addQueryParameter("pageSize", "48")
        if (page > 1) {
            url.addQueryParameter("p", getAdultPageToken(page, 48))
        }
        GET(url.build(), headers)
    } else {
        GET("$baseUrl/api/series-locales/popular", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath.contains("/adultos")) {
            val result = response.parseAs<AdultCatalogResponse>()
            val mangas = result.items
                .filter { !it.externo }
                .map { it.toSManga() }
            val hasNextPage = mangas.isNotEmpty() && (result.pageTokens?.next != null || result.page < result.totalPages)
            return MangasPage(mangas, hasNextPage)
        }
        val result = response.parseAs<List<SeriesWrapper>>()
        val series = result.flatMap { it.series }.distinctBy { it.id }.map { it.toSManga() }
        return MangasPage(series, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = if (isAdultSection) {
        val url = "$baseUrl/api/series-locales/adultos".toHttpUrl().newBuilder()
            .addQueryParameter("orden", "recientes")
            .addQueryParameter("pageSize", "48")
        if (page > 1) {
            url.addQueryParameter("p", getAdultPageToken(page, 48))
        }
        GET(url.build(), headers)
    } else {
        GET("$baseUrl/api/series-locales/capitulos/recientes?page=$page&pageSize=24", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (response.request.url.encodedPath.contains("/adultos")) {
            return popularMangaParse(response)
        }
        val result = response.parseAs<RecentChaptersResponse>()
        val mangas = result.items.map { it.serie.toSManga() }.distinctBy { it.url }
        val hasNextPage = result.page < result.totalPages
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sectionFilter = filters.firstInstanceOrNull<SectionFilter>()
        val searchAdult = when (sectionFilter?.state) {
            1 -> false
            2 -> true
            else -> isAdultSection
        }

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val excludedGenres = genreFilter?.getExcluded().orEmpty()

        if (searchAdult) {
            val url = "$baseUrl/api/series-locales/adultos".toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", "24")
            if (page > 1) {
                url.addQueryParameter("p", getAdultPageToken(page))
            }
            if (query.isNotBlank()) {
                url.addQueryParameter("q", query)
            }
            val includedGenres = genreFilter?.getIncluded().orEmpty()
            if (includedGenres.isNotEmpty()) {
                url.addQueryParameter("generos", includedGenres.joinToString(","))
            }
            val orderByFilter = filters.firstInstanceOrNull<OrderByFilter>()
            val orden = when (orderByFilter?.state) {
                1 -> "vistos"
                2 -> "az"
                else -> "recientes"
            }
            if (orden != "recientes") {
                url.addQueryParameter("orden", orden)
            }
            return GET(url.build(), headers).newBuilder()
                .tag(List::class.java, excludedGenres)
                .build()
        }

        val url = "$baseUrl/api/series-locales/search-candidates".toHttpUrl().newBuilder()
        url.addQueryParameter("q", query)
        url.addQueryParameter("includeAdult", "false")
        url.addQueryParameter("showSinPortada", "false")
        url.addQueryParameter("take", MAX_RESULTS.toString())

        genreFilter?.getIncluded()?.forEach {
            url.addQueryParameter("tags", it)
        }

        return GET(url.build(), headers).newBuilder()
            .tag(List::class.java, excludedGenres)
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    override fun searchMangaParse(response: Response): MangasPage {
        val excludedGenres = response.request.tag(List::class.java) as? List<String>
        if (response.request.url.encodedPath.contains("/adultos")) {
            val result = response.parseAs<AdultCatalogResponse>()
            val mangas = result.items
                .filter { series ->
                    excludedGenres.isNullOrEmpty() || series.getGenreList().none { genre ->
                        genre in excludedGenres
                    }
                }
                .map { it.toSManga() }
            val hasNextPage = result.items.isNotEmpty() && (result.pageTokens?.next != null || result.page < result.totalPages)
            return MangasPage(mangas, hasNextPage)
        }

        val mangas = response.parseAs<List<Series>>()
            .filter { series ->
                excludedGenres.isNullOrEmpty() || series.getGenreList().none { genre ->
                    genre in excludedGenres
                }
            }.sortedBy { it.title }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/series-locales/${manga.url}", headers)

    override fun getMangaUrl(manga: SManga): String = if (manga.url.startsWith("ext/")) {
        "$baseUrl/adultos/manga/o/${manga.url.removePrefix("ext/")}"
    } else {
        "$baseUrl/serie/local/${manga.url}"
    }

    override fun mangaDetailsParse(response: Response): SManga = if (response.request.url.encodedPath.contains("/ext/")) {
        response.parseAs<OneshotDetails>().toSMangaDetails()
    } else {
        response.parseAs<Series>().toSMangaDetails()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.request.url.encodedPath.contains("/ext/")) {
            val oneshot = response.parseAs<OneshotDetails>()
            return listOf(
                SChapter.create().apply {
                    name = "Capítulo 1"
                    url = "ext/${oneshot.smId}/${oneshot.chapterId}"
                    date_upload = 0L
                },
            )
        }
        val series = response.parseAs<Series>()
        return series.chapters
            .sortedByDescending { it.chapterNumber }
            .map { it.toSChapter(series.id) }
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("ext/")) {
        val smId = chapter.url.removePrefix("ext/").substringBefore("/")
        "$baseUrl/adultos/manga/o/$smId"
    } else {
        val chapterId = chapter.url.substringAfter("/")
        "$baseUrl/reader/local/$chapterId"
    }

    override fun pageListRequest(chapter: SChapter): Request = if (chapter.url.startsWith("ext/")) {
        val smId = chapter.url.removePrefix("ext/").substringBefore("/")
        GET("$baseUrl/api/series-locales/ext/$smId/paginas", headers)
    } else {
        val mangaId = chapter.url.substringBefore("/")
        val chapterId = chapter.url.substringAfter("/")
        GET("$baseUrl/api/series-locales/$mangaId/capitulos/$chapterId/paginas", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PagesWrapper>()
        return result.pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun getFilterList(): FilterList {
        fetchFilters()
        return FilterList(
            SectionFilter(),
            OrderByFilter(),
            if (genresList.isEmpty()) {
                Filter.Header("Presione 'Reiniciar' para intentar cargar los filtros.")
            } else {
                GenreFilter(genresList)
            },
        )
    }

    private val adultGenres = listOf(
        "Ahegao", "Anal", "Bañador", "Bondage", "Control mental", "Creampie",
        "DILF", "Doble penetración", "Embarazada", "Enfermera", "Femdom",
        "Footjob", "Futanari", "Gafas", "Grupal", "Gyaru", "Harén", "Incesto",
        "Infidelidad", "Juguetes", "Lactancia", "MILF", "Maid", "Medias",
        "Mind break", "Musculosa", "Netorare", "Netorase", "Non-con", "Oral",
        "Paizuri", "Pechos enormes", "Pechos grandes", "Piel morena", "Preñez",
        "Primera vez", "Profesora", "Tomboy", "Trasero grande", "Uniforme escolar",
        "Yaoi", "Yuri",
    )

    private var genresList: List<String> = emptyList()
    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        scope.launch {
            try {
                val tagsResponse = client.newCall(GET("$baseUrl/api/series-locales/tags", headers)).execute()
                val tagsList = tagsResponse.parseAs<List<String>>()

                genresList = (tagsList + adultGenres).distinct().sorted()
                filtersState = FiltersState.FETCHED
            } catch (_: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_SECTION
            title = "Sección de navegación"
            summary = "%s"
            entries = arrayOf("Todo público (SFW)", "Adultos (+18)")
            entryValues = arrayOf(SECTION_SFW, SECTION_NSFW)
            setDefaultValue(SECTION_SFW)
        }.also(screen::addPreference)
    }

    companion object {
        const val MAX_RESULTS = 120
        private const val PREF_SECTION = "pref_section"
        private const val SECTION_SFW = "sfw"
        private const val SECTION_NSFW = "nsfw"
    }
}
