package eu.kanade.tachiyomi.extension.es.olympusscanlation

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.math.min

class OlympusScanlation : HttpSource(), ConfigurableSource {

    override val versionId = 3
    private val isCi = System.getenv("CI") == "true"

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    private val defaultBaseUrl: String = "https://olympusbiblioteca.com"

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

    private val preferences: SharedPreferences = getPreferences {
        this.getString(DEFAULT_BASE_URL_PREF, null).let { domain ->
            if (domain != defaultBaseUrl) {
                this.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override val client by lazy {
        val client = network.cloudflareClient.newBuilder()
            .rateLimitHost(fetchedDomainUrl.toHttpUrl(), 1, 2)
            .rateLimitHost(apiBaseUrl.toHttpUrl(), 2, 1)
            .build()

        fetchBookmarks()

        return@lazy client
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun popularMangaRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/sf/home".toHttpUrl().newBuilder()
            .build()
        return GET(apiUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<PayloadHomeDto>()
        val slugMap = preferences.slugMap.toMutableMap()
        val mangaList = result.data.popularComics
            .filter { it.type == "comic" }
            .map {
                slugMap[it.id] = it.slug
                it.toSManga()
            }
        preferences.slugMap = slugMap
        return MangasPage(mangaList, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/sf/new-chapters?page=$page".toHttpUrl().newBuilder()
            .build()
        return GET(apiUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<NewChaptersDto>()
        val slugMap = preferences.slugMap.toMutableMap()
        val mangaList = result.data.filter { it.type == "comic" }
            .map {
                slugMap[it.id] = it.slug
                it.toSManga()
            }
        preferences.slugMap = slugMap
        return MangasPage(mangaList, result.hasNextPage())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            if (query.length < 3) {
                throw Exception("La búsqueda debe tener al menos 3 caracteres")
            }
            val apiUrl = "$apiBaseUrl/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query.substring(0, min(query.length, 40)))
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
            val result = response.parseAs<PayloadMangaDto>()
            val slugMap = preferences.slugMap.toMutableMap()
            val mangaList = result.data.filter { it.type == "comic" }
                .map {
                    slugMap[it.id] = it.slug
                    it.toSManga()
                }
            preferences.slugMap = slugMap
            return MangasPage(mangaList, hasNextPage = false)
        }

        val result = response.parseAs<PayloadSeriesDto>()
        val mangaList = result.data.series.data.map { it.toSManga() }
        return MangasPage(mangaList, result.data.series.hasNextPage())
    }

    private var bookmarksState = BookmarksState.NOT_FETCHED

    private fun fetchBookmarks() {
        if (!preferences.fetchBookmarksPref()) return
        if (bookmarksState != BookmarksState.NOT_FETCHED) return
        bookmarksState = BookmarksState.FETCHING
        val slugMap = preferences.slugMap.toMutableMap()
        var page = 1
        try {
            do {
                val response = network.cloudflareClient.newCall(GET("$apiBaseUrl/api/user/bookmarks?page=$page", headers)).execute()
                if (!response.isSuccessful) return
                val result = response.parseAs<BookmarksWrapperDto>()
                result.getBookmarks().forEach { bookmark ->
                    slugMap[bookmark.id!!] = bookmark.slug!!
                }
                page++
            } while (result.meta.hasNextPage())
        } catch (_: Exception) { } finally {
            bookmarksState = BookmarksState.FETCHED
        }
        preferences.slugMap = slugMap
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = preferences.slugMap[manga.url.toInt()]!!
        return "$baseUrl/series/comic-$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = preferences.slugMap[manga.url.toInt()]!!

        val apiUrl = "$apiBaseUrl/api/series/$slug?type=comic"
        return GET(url = apiUrl, headers = headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailDto>()
        return result.data.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = chapter.url.substringBefore("/")
        val chapterId = chapter.url.substringAfter("/")
        val mangaSlug = preferences.slugMap[mangaId.toInt()]!!
        return "$baseUrl/capitulo/$chapterId/comic-$mangaSlug"
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url
        val mangaSlug = preferences.slugMap[mangaId.toInt()]!!

        return paginatedChapterListRequest(mangaSlug, mangaId, 1)
    }

    private fun paginatedChapterListRequest(mangaSlug: String, mangaId: String, page: Int): Request {
        return GET(
            url = "$apiBaseUrl/api/series/$mangaSlug/chapters?page=$page&direction=desc&type=comic#$mangaId",
            headers = headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.fragment ?: ""
        val slug = response.request.url.toString()
            .substringAfter("/series/")
            .substringBefore("/chapters")

        val data = response.parseAs<PayloadChapterDto>()
        var resultSize = data.data.size
        var page = 2
        while (data.meta.total > resultSize) {
            val newRequest = paginatedChapterListRequest(slug, mangaId, page)
            val newResponse = client.newCall(newRequest).execute()
            val newData = newResponse.parseAs<PayloadChapterDto>()
            data.data += newData.data
            resultSize += newData.data.size
            page += 1
        }
        return data.data.map { it.toSChapter(mangaId, dateFormat) }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = chapter.url.substringBefore("/")
        val chapterId = chapter.url.substringAfter("/")
        val mangaSlug = preferences.slugMap[mangaId.toInt()]!!

        return GET("$apiBaseUrl/api/series/$mangaSlug/chapters/$chapterId?type=comic")
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<PayloadPagesDto>().chapter.pages.mapIndexed { i, img ->
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
                val filters = response.parseAs<GenresStatusesDto>()

                genresList = filters.genres.map { it.name.trim() to it.id }
                statusesList = filters.statuses.map { it.name.trim() to it.id }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, Int>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }
    private enum class BookmarksState { NOT_FETCHED, FETCHING, FETCHED }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = FETCH_BOOKMARKS_PREF
            title = "Usar marcadores"
            summary = "Usa los marcadores del sitio para obtener la url actual de la serie.\nRequiere iniciar sesión en WebView y seguir la serie."
            setDefaultValue(FETCH_BOOKMARKS_PREF_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Reinicie la aplicación para aplicar los cambios", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }

        SwitchPreferenceCompat(screen.context).apply {
            key = FETCH_DOMAIN_PREF
            title = "Buscar dominio automáticamente"
            summary = "Intenta buscar el dominio automáticamente al abrir la fuente."
            setDefaultValue(FETCH_DOMAIN_PREF_DEFAULT)
        }.also { screen.addPreference(it) }

        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Editar URL de la fuente"
            summary = "Para uso temporal, si la extensión se actualiza se perderá el cambio."
            dialogTitle = "Editar URL de la fuente"
            dialogMessage = "URL por defecto:\n$defaultBaseUrl"
            setDefaultValue(defaultBaseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Reinicie la aplicación para aplicar los cambios", Toast.LENGTH_LONG).show()
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
    private fun SharedPreferences.fetchBookmarksPref() = getBoolean(FETCH_BOOKMARKS_PREF, FETCH_BOOKMARKS_PREF_DEFAULT)

    private var _slugMap: Map<Int, String>? = null
    private var SharedPreferences.slugMap: Map<Int, String>
        get() {
            _slugMap?.let { return it }
            val json = getString(SLUG_MAP, "{}")!!
            _slugMap = try {
                json.parseAs<Map<Int, String>>()
            } catch (_: SerializationException) {
                emptyMap()
            }
            return _slugMap!!
        }
        set(map) {
            _slugMap = map
            edit().putString(SLUG_MAP, map.toJsonString()).apply()
        }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"

        private const val FETCH_DOMAIN_PREF = "fetchDomain"
        private const val FETCH_DOMAIN_PREF_DEFAULT = true

        private const val FETCH_BOOKMARKS_PREF = "fetchBookmarks"
        private const val FETCH_BOOKMARKS_PREF_DEFAULT = false

        private const val SLUG_MAP = "slugMap"
    }
}
