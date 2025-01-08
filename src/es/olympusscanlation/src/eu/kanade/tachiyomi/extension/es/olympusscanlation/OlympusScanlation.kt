package eu.kanade.tachiyomi.extension.es.olympusscanlation

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

class OlympusScanlation : HttpSource(), ConfigurableSource {

    override val versionId = 2
    private val isCi = System.getenv("CI") == "true"

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    private val defaultBaseUrl: String = "https://olympuslectura.com"

    private val fetchedDomainUrl: String by lazy {
        if (!preferences.fetchDomainPref()) return@lazy preferences.prefBaseUrl
        try {
            val initClient = network.cloudflareClient
            val headers = super.headersBuilder().build()
            val document = initClient.newCall(GET("https://olympus.pages.dev", headers)).execute().asJsoup()
            val domain = document.selectFirst("meta[property=og:url]")?.attr("content")
                ?: return@lazy preferences.prefBaseUrl
            val host = initClient.newCall(GET(domain, headers)).execute().request.url.host
            val newDomain = "https://$host"
            preferences.prefBaseUrl = newDomain
            newDomain
        } catch (_: Exception) {
            preferences.prefBaseUrl
        }
    }

    private val apiBaseUrl by lazy {
        fetchedDomainUrl.replace("https://", "https://dashboard.")
    }

    override val lang: String = "es"
    override val name: String = "Olympus Scanlation"

    override val supportsLatest: Boolean = true

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimitHost(fetchedDomainUrl.toHttpUrl(), 1, 2)
            .rateLimitHost(apiBaseUrl.toHttpUrl(), 2, 1)
            .build()
    }

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun popularMangaRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/sf/home".toHttpUrl().newBuilder()
            .build()
        return GET(apiUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<PayloadHomeDto>(response.body.string())
        val mangaList = result.data.popularComics.filter { it.type == "comic" }.map { it.toSManga() }
        return MangasPage(mangaList, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/sf/new-chapters?page=$page".toHttpUrl().newBuilder()
            .build()
        return GET(apiUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.decodeFromString<NewChaptersDto>(response.body.string())
        val mangaList = result.data.filter { it.type == "comic" }.map { it.toSManga() }
        val hasNextPage = result.current_page < result.last_page
        return MangasPage(mangaList, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val apiUrl = "$apiBaseUrl/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .build()
            return GET(apiUrl, headers)
        }

        val url = "$apiBaseUrl/api/series".toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    if (filter.state?.ascending == true) {
                        url.addQueryParameter("direction", "desc")
                    } else {
                        url.addQueryParameter("direction", "asc")
                    }
                }
                is GenreFilter -> {
                    if (filter.toUriPart() != 9999) {
                        url.addQueryParameter("genres", filter.toUriPart().toString())
                    }
                }
                is StatusFilter -> {
                    if (filter.toUriPart() != 9999) {
                        url.addQueryParameter("status", filter.toUriPart().toString())
                    }
                }
                else -> {}
            }
        }
        url.addQueryParameter("type", "comic")
        url.addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().startsWith("$apiBaseUrl/api/search")) {
            val result = json.decodeFromString<PayloadMangaDto>(response.body.string())
            val mangaList = result.data.filter { it.type == "comic" }.map { it.toSManga() }
            return MangasPage(mangaList, hasNextPage = false)
        }

        val result = json.decodeFromString<PayloadSeriesDto>(response.body.string())
        val mangaList = result.data.series.data.map { it.toSManga() }
        val hasNextPage = result.data.series.current_page < result.data.series.last_page
        return MangasPage(mangaList, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val slug = response.request.url
            .toString()
            .substringAfter("/series/comic-")
            .substringBefore("/chapters")
        val apiUrl = "$apiBaseUrl/api/series/$slug?type=comic"
        val newRequest = GET(url = apiUrl, headers = headers)
        val newResponse = client.newCall(newRequest).execute()
        val result = json.decodeFromString<MangaDetailDto>(newResponse.body.string())
        return result.data.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request {
        return paginatedChapterListRequest(
            manga.url
                .substringAfter("/series/comic-")
                .substringBefore("/chapters"),
            1,
        )
    }

    private fun paginatedChapterListRequest(mangaUrl: String, page: Int): Request {
        return GET(
            url = "$apiBaseUrl/api/series/$mangaUrl/chapters?page=$page&direction=desc&type=comic",
            headers = headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url
            .toString()
            .substringAfter("/series/")
            .substringBefore("/chapters")
        val data = json.decodeFromString<PayloadChapterDto>(response.body.string())
        var resultSize = data.data.size
        var page = 2
        while (data.meta.total > resultSize) {
            val newRequest = paginatedChapterListRequest(slug, page)
            val newResponse = client.newCall(newRequest).execute()
            val newData = json.decodeFromString<PayloadChapterDto>(newResponse.body.string())
            data.data += newData.data
            resultSize += newData.data.size
            page += 1
        }
        return data.data.map { it.toSChapter(slug, dateFormat) }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url
            .substringAfter("/capitulo/")
            .substringBefore("/chapters")
            .substringBefore("/comic")
        val slug = chapter.url
            .substringAfter("comic-")
            .substringBefore("/chapters")
            .substringBefore("/comic")
        return GET("$apiBaseUrl/api/series/$slug/chapters/$id?type=comic")
    }

    override fun pageListParse(response: Response): List<Page> {
        return json.decodeFromString<PayloadPagesDto>(response.body.string()).chapter.pages.mapIndexed { i, img ->
            Page(i, imageUrl = img)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private class SortFilter : Filter.Sort(
        "Ordenar",
        arrayOf("Alfabético"),
        Selection(0, false),
    )

    private class GenreFilter(genres: List<Pair<String, Int>>) : UriPartFilter(
        "Género",
        arrayOf(
            Pair("Todos", 9999),
            *genres.toTypedArray(),
        ),
    )

    private class StatusFilter(statuses: List<Pair<String, Int>>) : UriPartFilter(
        "Estado",
        arrayOf(
            Pair("Todos", 9999),
            *statuses.toTypedArray(),
        ),
    )

    override fun getFilterList(): FilterList {
        fetchFilters()
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Los filtros no funcionan en la búsqueda por texto"),
            Filter.Separator(),
            SortFilter(),
        )

        if (filtersState == FiltersState.FETCHED) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Filtrar por género"),
                GenreFilter(genresList),
            )

            filters += listOf(
                Filter.Separator(),
                Filter.Header("Filtrar por estado"),
                StatusFilter(statusesList),
            )
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Presione 'Reiniciar' para intentar cargar los filtros"),
            )
        }

        return FilterList(filters)
    }

    private var genresList: List<Pair<String, Int>> = emptyList()
    private var statusesList: List<Pair<String, Int>> = emptyList()
    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val response = client.newCall(GET("$apiBaseUrl/api/genres-statuses", headers)).execute()
                val filters = json.decodeFromString<GenresStatusesDto>(response.body.string())

                genresList = filters.genres.map { it.name.trim() to it.id }
                statusesList = filters.statuses.map { it.name.trim() to it.id }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, Int>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = FETCH_DOMAIN_PREF
            title = FETCH_DOMAIN_PREF_TITLE
            summary = FETCH_DOMAIN_PREF_SUMMARY
            setDefaultValue(FETCH_DOMAIN_PREF_DEFAULT)
        }.also { screen.addPreference(it) }

        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "URL por defecto:\n$defaultBaseUrl"
            setDefaultValue(defaultBaseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    private var _cachedBaseUrl: String? = null
    private var SharedPreferences.prefBaseUrl: String
        get() {
            if (_cachedBaseUrl == null) {
                _cachedBaseUrl = getString(BASE_URL_PREF, defaultBaseUrl)!!
            }
            return _cachedBaseUrl!!
        }
        set(value) {
            _cachedBaseUrl = value
            edit().putString(BASE_URL_PREF, value).apply()
        }

    private fun SharedPreferences.fetchDomainPref() = getBoolean(FETCH_DOMAIN_PREF, FETCH_DOMAIN_PREF_DEFAULT)

    companion object {

        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Editar URL de la fuente"
        private const val BASE_URL_PREF_SUMMARY = "Para uso temporal, si la extensión se actualiza se perderá el cambio."
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Reinicie la aplicación para aplicar los cambios"

        private const val FETCH_DOMAIN_PREF = "fetchDomain"
        private const val FETCH_DOMAIN_PREF_TITLE = "Buscar dominio automáticamente"
        private const val FETCH_DOMAIN_PREF_SUMMARY = "Intenta buscar el dominio automáticamente al abrir la fuente."
        private const val FETCH_DOMAIN_PREF_DEFAULT = true
    }

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { domain ->
            if (domain != defaultBaseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }
}
