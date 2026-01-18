package eu.kanade.tachiyomi.extension.es.ikigaimangas

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

class IkigaiMangas : HttpSource(), ConfigurableSource {

    private val isCi = System.getenv("CI") == "true"

    override val baseUrl get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    private val defaultBaseUrl: String = "https://viralikigai.melauroral.com"

    private val fetchedDomainUrl: String by lazy {
        if (!preferences.fetchDomainPref()) return@lazy preferences.prefBaseUrl
        try {
            val initClient = network.cloudflareClient
            val headers = super.headersBuilder().build()
            val document = initClient.newCall(GET("https://ikigaimangas.com", headers)).execute().asJsoup()
            val scriptUrl = document.selectFirst("div[on:click]:containsOwn(Nuevo dominio)")?.attr("on:click")
                ?: return@lazy preferences.prefBaseUrl
            val script = initClient.newCall(GET("https://ikigaimangas.com/build/$scriptUrl", headers)).execute().body.string()
            val domain = script.substringAfter("window.open(\"").substringBefore("\"")
            val host = initClient.newCall(GET(domain, headers)).execute().request.url.host
            val newDomain = "https://$host"
            preferences.prefBaseUrl = newDomain
            newDomain
        } catch (e: Exception) {
            preferences.prefBaseUrl
        }
    }

    private val apiBaseUrl: String = "https://panel.ikigaimangas.com"

    private val imageCdnUrl: String = "https://image.ikigaimangas.cloud"

    override val lang: String = "es"

    override val name: String = "Ikigai Mangas"

    override val supportsLatest: Boolean = true

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimitHost(fetchedDomainUrl.toHttpUrl(), 1, 2)
            .rateLimitHost(apiBaseUrl.toHttpUrl(), 2, 1)
            .addNetworkInterceptor(::nsfwCookieInterceptor)
            .build()
    }

    private fun nsfwCookieInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return request.header("X-Add-Nsfw-Cookie")?.let {
            val newRequest = request.newBuilder()
                .removeHeader("X-Add-Nsfw-Cookie")
                .setCookie("nsfw-mode", "true")
                .build()
            chain.proceed(newRequest)
        } ?: chain.proceed(request)
    }

    private fun Request.Builder.setCookie(name: String, value: String): Request.Builder {
        val existingHeader = this.build().header("Cookie") ?: ""

        val cookies = existingHeader
            .split(";")
            .mapNotNull {
                val parts = it.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap().toMutableMap()

        cookies[name] = value

        val mergedHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        return this.header("Cookie", mergedHeader)
    }

    private val preferences: SharedPreferences = getPreferences()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$fetchedDomainUrl/")

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun popularMangaRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/swf/series/ranking-list".toHttpUrl().newBuilder()
            .addQueryParameter("type", "total_ranking")
            .addQueryParameter("series_type", "comic")
            .addQueryParameter("nsfw", if (preferences.showNsfwPref) "true" else "false")

        return GET(apiUrl.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<PayloadSeriesDto>(response.body.string())
        val mangaList = result.data.map { it.toSManga() }
        return MangasPage(mangaList, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/swf/new-chapters".toHttpUrl().newBuilder()
            .addQueryParameter("nsfw", if (preferences.showNsfwPref) "true" else "false")
            .addQueryParameter("page", page.toString())

        return GET(apiUrl.build(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.decodeFromString<PayloadLatestDto>(response.body.string())
        val mangaList = result.data.filter { it.type == "comic" }.map { it.toSManga() }
        return MangasPage(mangaList, result.hasNextPage())
    }

    private var seriesCache: List<QwikSeriesDto>? = null

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.isNotEmpty()) {
            if (seriesCache != null) {
                return Observable.just(qwikDataParse(query, seriesCache!!, page))
            }
            val series = getQuerySeriesList()
            return Observable.just(qwikDataParse(query, series, page))
        }

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response -> searchMangaParse(response) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortByFilter = filters.firstInstanceOrNull<SortByFilter>()

        val apiUrl = "$apiBaseUrl/api/swf/series".toHttpUrl().newBuilder()

        apiUrl.addQueryParameter("page", page.toString())
        apiUrl.addQueryParameter("type", "comic")
        apiUrl.addQueryParameter("nsfw", if (preferences.showNsfwPref) "true" else "false")

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter(Genre::state)
            .map(Genre::id)
            .joinToString(",")

        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.state.orEmpty()
            .filter(Status::state)
            .map(Status::id)
            .joinToString(",")

        if (genres.isNotEmpty()) apiUrl.addQueryParameter("genres", genres)
        if (statuses.isNotEmpty()) apiUrl.addQueryParameter("status", statuses)

        apiUrl.addQueryParameter("column", sortByFilter?.selected ?: "name")
        apiUrl.addQueryParameter("direction", if (sortByFilter?.state?.ascending == true) "asc" else "desc")

        return GET(apiUrl.build(), headers)
    }

    val scriptUrlRegex = """from"(.*?\.js)"""".toRegex()
    val seriesChunkRegex = """PUBLIC_BACKEND_API.*?"s_(.*?)"""".toRegex()

    private fun getQuerySeriesList(): List<QwikSeriesDto> {
        val baseUrl = preferences.prefBaseUrl
        val homeDocument = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        val mainScript =
            homeDocument.selectFirst("input[type=search][on:input]")?.attr("on:input")
                ?: throw Exception("No se pudo encontrar la lista de series.")

        val mainScriptData =
            client.newCall(GET("$baseUrl/build/$mainScript", headers)).execute().body.string()

        val scriptsUrls = scriptUrlRegex.findAll(mainScriptData).map { it.groupValues[1] }.toList()

        scriptsUrls.forEach {
            val scriptData =
                client.newCall(GET("$baseUrl/build/$it", headers)).execute().body.string()
            val seriesChunkMatch = seriesChunkRegex.find(scriptData)
            if (seriesChunkMatch != null) {
                val chunkId = seriesChunkMatch.groupValues[1]
                val url = "$baseUrl/series".toHttpUrl().newBuilder()
                    .addQueryParameter("qfunc", chunkId)
                    .build()
                val payload = """{"_entry":"1","_objs":["\u0002_#s_$chunkId",["0"]]}"""
                val body = payload.toRequestBody()
                val headers = headersBuilder()
                    .set("X-QRL", chunkId)
                    .set("Content-Type", "application/qwik-json")
                    .build()
                val response = client.newCall(POST(url.toString(), headers, body)).execute()
                return response.parseAs<QwikData>().parseAsList<QwikSeriesDto>()
                    .also { series -> seriesCache = series }
            }
        }

        throw Exception("No se pudo encontrar la lista de series.")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<PayloadSeriesDto>(response.body.string())
        val mangaList = result.data.filter { it.type == "comic" }.map { it.toSManga() }
        return MangasPage(mangaList, result.hasNextPage())
    }

    private fun qwikDataParse(query: String, seriesList: List<QwikSeriesDto>, page: Int): MangasPage {
        val nsfwEnabled = preferences.showNsfwPref

        val filteredSeries = seriesList
            .filter { it.type == "comic" }
            .filter { nsfwEnabled || !it.isMature }
            .filter { it.name.contains(query, ignoreCase = true) }

        val pagedSeries = filteredSeries
            .drop((page - 1) * PAGE_SIZE)
            .take(PAGE_SIZE)
            .map { it.toSManga(imageCdnUrl) }

        return MangasPage(pagedSeries, filteredSeries.size > page * PAGE_SIZE)
    }

    override fun getMangaUrl(manga: SManga) = preferences.prefBaseUrl + manga.url.substringBefore("#").replace("/series/comic-", "/series/")

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url
            .substringAfter("/series/comic-")
            .substringBefore("#")

        return GET("$apiBaseUrl/api/swf/series/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = json.decodeFromString<PayloadSeriesDetailsDto>(response.body.string())
        return result.series.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter) = preferences.prefBaseUrl + chapter.url.substringBefore("#")

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/series/comic-").substringBefore("#")
        return GET("$apiBaseUrl/api/swf/series/$slug/chapters?page=1", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.toString()
            .substringAfter("/series/")
            .substringBefore("/chapters")
        var result = json.decodeFromString<PayloadChaptersDto>(response.body.string())
        val mangas = mutableListOf<SChapter>()
        mangas.addAll(result.data.map { it.toSChapter(dateFormat) })
        var page = 2
        while (result.meta.hasNextPage()) {
            val newResponse = client.newCall(GET("$apiBaseUrl/api/swf/series/$slug/chapters?page=$page", headers)).execute()
            result = json.decodeFromString<PayloadChaptersDto>(newResponse.body.string())
            mangas.addAll(result.data.map { it.toSChapter(dateFormat) })
            page++
        }
        return mangas
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET(fetchedDomainUrl + chapter.url.substringBefore("#"), headers)

    override fun pageListParse(response: Response): List<Page> {
        val request = response.request
        var document = response.asJsoup()
        document.selectFirst("button > span:contains(permitir nsfw)")?.let {
            val newRequest = request.newBuilder()
                .header("X-Add-Nsfw-Cookie", "1")
                .build()
            document = client.newCall(newRequest).execute().asJsoup()
        }
        return document.select("section div.img > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        fetchFilters()

        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Nota: Los filtros son ignorados si se realiza una búsqueda por texto."),
            Filter.Separator(),
            SortByFilter("Ordenar por", getSortProperties()),
        )

        filters += if (filtersState == FiltersState.FETCHED) {
            listOf(
                StatusFilter("Estados", getStatusFilters()),
                GenreFilter("Géneros", getGenreFilters()),
            )
        } else {
            listOf(
                Filter.Header("Presione 'Restablecer' para intentar cargar los filtros"),
            )
        }

        return FilterList(filters)
    }

    private fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty("Nombre", "name"),
        SortProperty("Creado en", "created_at"),
        SortProperty("Actualización más reciente", "last_chapter_date"),
        SortProperty("Número de favoritos", "bookmark_count"),
        SortProperty("Número de valoración", "rating_count"),
        SortProperty("Número de vistas", "view_count"),
    )

    private fun getGenreFilters(): List<Genre> = genresList.map { Genre(it.first, it.second) }
    private fun getStatusFilters(): List<Status> = statusesList.map { Status(it.first, it.second) }

    private var genresList: List<Pair<String, Long>> = emptyList()
    private var statusesList: List<Pair<String, Long>> = emptyList()
    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val response = client.newCall(GET("$apiBaseUrl/api/swf/filter-options", headers)).execute()
                val filters = json.decodeFromString<PayloadFiltersDto>(response.body.string())

                genresList = filters.data.genres.map { it.name.trim() to it.id }
                statusesList = filters.data.statuses.map { it.name.trim() to it.id }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_NSFW_PREF
            title = SHOW_NSFW_PREF_TITLE
            setDefaultValue(SHOW_NSFW_PREF_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                _cachedNsfwPref = newValue as Boolean
                true
            }
        }.also { screen.addPreference(it) }

        SwitchPreferenceCompat(screen.context).apply {
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

    private fun SharedPreferences.fetchDomainPref() = getBoolean(FETCH_DOMAIN_PREF, FETCH_DOMAIN_PREF_DEFAULT)

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

    private var _cachedNsfwPref: Boolean? = null
    private var SharedPreferences.showNsfwPref: Boolean
        get() {
            if (_cachedNsfwPref == null) {
                _cachedNsfwPref = getBoolean(SHOW_NSFW_PREF, SHOW_NSFW_PREF_DEFAULT)
            }
            return _cachedNsfwPref!!
        }
        set(value) {
            _cachedNsfwPref = value
            edit().putBoolean(SHOW_NSFW_PREF, value).apply()
        }

    private inline fun <reified R> List<*>.firstInstanceOrNull(): R? =
        filterIsInstance<R>().firstOrNull()

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    companion object {
        private const val SHOW_NSFW_PREF = "pref_show_nsfw"
        private const val SHOW_NSFW_PREF_TITLE = "Mostrar contenido NSFW"
        private const val SHOW_NSFW_PREF_DEFAULT = false

        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Editar URL de la fuente"
        private const val BASE_URL_PREF_SUMMARY = "Para uso temporal, si la extensión se actualiza se perderá el cambio."
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP_MESSAGE = "Reinicie la aplicación para aplicar los cambios"

        private const val FETCH_DOMAIN_PREF = "fetchDomain"
        private const val FETCH_DOMAIN_PREF_TITLE = "Buscar dominio automáticamente"
        private const val FETCH_DOMAIN_PREF_SUMMARY = "Intenta buscar el dominio automáticamente al abrir la fuente."
        private const val FETCH_DOMAIN_PREF_DEFAULT = true

        private const val PAGE_SIZE = 20
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
