package eu.kanade.tachiyomi.extension.es.olympusscanlation

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OlympusScanlation :
    HttpSource(),
    ConfigurableSource {

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

        return@lazy client
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Volatile
    private var seriesList: List<MangaDto> = emptyList()

    @Volatile
    private var lastFetchTime: Long = 0L

    @Synchronized
    private fun fetchSeriesList() {
        val now = System.currentTimeMillis()

        if (seriesList.isNotEmpty() && (now - lastFetchTime) < CACHE_DURATION_MS) {
            return
        }

        val result = client.newCall(GET("$baseUrl/api/series/list")).execute()
        if (!result.isSuccessful) {
            throw Exception("Failed to fetch series list: HTTP ${result.code}")
        }

        val series = result.parseAs<PayloadMangaDto>()

        val comics = series.data.asSequence()
            .filter { it.type == "comic" }
            .toList()

        seriesList = comics
        lastFetchTime = now

        val newSlugMap = comics.associate { it.id to it.slug }

        preferences.slugMap += newSlugMap
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        fetchSeriesList()
        return super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request {
        val apiUrl = "$baseUrl/api/rankings?page=$page&period=total_ranking".toHttpUrl().newBuilder()
            .build()
        return GET(apiUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<RankingDto>()
        val slugMap = preferences.slugMap.toMutableMap()
        val mangaList = result.data
            .filter { it.type == "comic" }
            .map {
                slugMap[it.id] = it.slug
                it.toSManga()
            }
        preferences.slugMap = slugMap
        return MangasPage(mangaList, hasNextPage = result.hasNextPage())
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        fetchSeriesList()
        return super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$baseUrl/api/new-chapters?page=$page".toHttpUrl().newBuilder()
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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        fetchSeriesList()
        return Observable.just(parseSearchManga(page, query))
    }

    private fun parseSearchManga(page: Int, query: String): MangasPage {
        val filteredList = seriesList.filter { it.name.contains(query, ignoreCase = true) }
        val paginatedList = filteredList.drop((page - 1) * 20).take(20)
        val hasNextPage = page * 20 < filteredList.size
        return MangasPage(paginatedList.map { it.toSManga() }, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String {
        val slug = preferences.slugMap[manga.url.toInt()]!!
        return "$baseUrl/series/comic-$slug"
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        fetchSeriesList()
        return super.fetchMangaDetails(manga)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = preferences.slugMap[manga.url.toInt()]!!

        val apiUrl = "$baseUrl/api/series/$slug?type=comic"
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

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        fetchSeriesList()
        return super.fetchChapterList(manga)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url
        val mangaSlug = preferences.slugMap[mangaId.toInt()]!!

        return paginatedChapterListRequest(mangaSlug, mangaId, 1)
    }

    private fun paginatedChapterListRequest(mangaSlug: String, mangaId: String, page: Int): Request = GET(
        url = "$apiBaseUrl/api/series/$mangaSlug/chapters?page=$page&direction=desc&type=comic#$mangaId",
        headers = headers,
    )

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

        return GET("$baseUrl/api/capitulo/comic-$mangaSlug/$chapterId")
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PayloadPagesDto>().chapter.pages.mapIndexed { i, img ->
        Page(i, imageUrl = img)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
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

    private var cachedBaseUrl: String? = null
    private var SharedPreferences.prefBaseUrl: String
        get() {
            if (cachedBaseUrl == null) {
                cachedBaseUrl = getString(BASE_URL_PREF, defaultBaseUrl)!!
            }
            return cachedBaseUrl!!
        }
        set(value) {
            cachedBaseUrl = value
            edit().putString(BASE_URL_PREF, value).apply()
        }

    private fun SharedPreferences.fetchDomainPref() = getBoolean(FETCH_DOMAIN_PREF, FETCH_DOMAIN_PREF_DEFAULT)

    private var slugMapCache: Map<Int, String>? = null
    private var SharedPreferences.slugMap: Map<Int, String>
        get() {
            slugMapCache?.let { return it }
            val json = getString(SLUG_MAP, "{}")!!
            slugMapCache = try {
                json.parseAs<Map<Int, String>>()
            } catch (_: SerializationException) {
                emptyMap()
            }
            return slugMapCache!!
        }
        set(map) {
            slugMapCache = map
            edit().putString(SLUG_MAP, map.toJsonString()).apply()
        }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"

        private const val FETCH_DOMAIN_PREF = "fetchDomain"
        private const val FETCH_DOMAIN_PREF_DEFAULT = true

        private const val SLUG_MAP = "slugMap"

        private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour
    }
}
