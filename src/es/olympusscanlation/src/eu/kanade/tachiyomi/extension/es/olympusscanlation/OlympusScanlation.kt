package eu.kanade.tachiyomi.extension.es.olympusscanlation

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds

@Source
abstract class OlympusScanlation :
    HttpSource(),
    ConfigurableSource {
    private val fetchedDomainUrlHost by lazy { fetchedDomainUrl.toHttpUrl().host }
    private val apiBaseUrlHost by lazy { apiBaseUrl.toHttpUrl().host }

    private val isCi = System.getenv("CI") == "true"
    private val shouldFetchDomain: Boolean get() = preferences.getBoolean("fetchDomain", true)

    override val baseUrl: String get() =
        when {
            isCi -> defaultBaseUrl
            shouldFetchDomain -> fetchedDomainUrl
            else -> getPrefBaseUrl()
        }

    private val defaultBaseUrl: String = "https://olympusxyz.com"

    private val fetchedDomainUrl: String by lazy {
        if (!shouldFetchDomain) return@lazy getPrefBaseUrl()
        try {
            val initClient = network.client
            val headers = super.headersBuilder().build()
            val document = initClient.newCall(GET("https://olympus.pages.dev", headers)).execute().asJsoup()
            val domain =
                document.selectFirst("meta[property=og:url]")?.attr("content")
                    ?: return@lazy getPrefBaseUrl()
            val host =
                initClient
                    .newCall(GET(domain, headers))
                    .execute()
                    .request.url.host
            val newDomain = "https://$host"
            setPrefBaseUrl(newDomain)
            newDomain
        } catch (_: Exception) {
            getPrefBaseUrl()
        }
    }

    private val apiBaseUrl by lazy {
        fetchedDomainUrl.replace("https://", "https://panel.")
    }

    private val publicApiBaseUrl: String get() = baseUrl
    private val dashboardApiBaseUrl: String get() = apiBaseUrl

    override val supportsLatest: Boolean = true

    private val preferences: SharedPreferences = getPreferences()
    private val cacheManager = MangaCacheManager(preferences)
    private val apiHelper by lazy { ApiHelper(client, headersMap, dashboardApiBaseUrl) }
    private val filterManager by lazy { FilterManager(preferences, client) }

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"

        private const val FETCH_DOMAIN_PREF = "fetchDomain"
        private const val FETCH_DOMAIN_PREF_DEFAULT = true

        private const val SLUG_MAP = "slugMap"

        private const val CHAPTER_COUNT_MAP = "chapterCountMap"

        private const val MIN_ACCEPTED_CHAPTER_PERCENT = 70

        private const val TAG = "OlympusScanlation"

        private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour

        private val CHAPTER_COUNT_TEXT_REGEX = Regex(
            "(\\d+)\\s+cap[ií]tulos?\\s+en\\s+total",
            RegexOption.IGNORE_CASE,
        )

        private val CHAPTER_NUMBER_TEXT_REGEX = Regex(
            "cap[ií]tulo\\s*(\\d+(?:\\.\\d+)?)",
            RegexOption.IGNORE_CASE,
        )
    }

    private val headersMap: Map<String, String>
        get() {
            val map = mutableMapOf<String, String>()
            for (i in 0 until headers.size) {
                map[headers.name(i)] = headers.value(i)
            }
            return map
        }

    override val client by lazy {
        val logger = Interceptor { chain ->
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "${response.code} ${request.method} ${request.url} (${duration}ms)")
            response
        }

        val client = network.client.newBuilder()
            .addNetworkInterceptor(logger)
            .rateLimit(1, 2.seconds) { it.host == fetchedDomainUrlHost }
            .rateLimit(2, 1.seconds) { it.host == apiBaseUrlHost }
            .build()
        client
    }

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private data class MangaRefTag(val manga: SManga)

    private data class MangaTitleTag(val title: String)

    private var SharedPreferences.slugMap: Map<Int, String>
        get() = runCatching {
            json.decodeFromString<Map<Int, String>>(getString(SLUG_MAP, "{}") ?: "{}")
        }.getOrDefault(emptyMap())
        set(value) {
            edit().putString(SLUG_MAP, json.encodeToString(value)).apply()
        }

    private var SharedPreferences.chapterCountMap: Map<Int, Int>
        get() = runCatching {
            json.decodeFromString<Map<Int, Int>>(getString(CHAPTER_COUNT_MAP, "{}") ?: "{}")
        }.getOrDefault(emptyMap())
        set(value) {
            edit().putString(CHAPTER_COUNT_MAP, json.encodeToString(value)).apply()
        }

    @Volatile
    private var seriesList: List<MangaDto> = emptyList()

    @Volatile
    private var lastFetchTime: Long = 0L

    @Volatile
    private var chapterNameToIdCache: Map<String, Int> = emptyMap()

    @Synchronized
    private fun fetchSeriesList() {
        val now = System.currentTimeMillis()

        if (seriesList.isNotEmpty() && (now - lastFetchTime) < CACHE_DURATION_MS) {
            return
        }

        val comics = try {
            fetchSeriesListFromListEndpoint()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh series list, falling back to homepage slugs", e)
            // List endpoint failed — fall back to homepage for slug updates only.
            // Don't overwrite seriesList so search still works with previously cached data.
            try {
                val homepageSlugs = fetchHomepageSlugs()
                if (homepageSlugs.isNotEmpty()) {
                    preferences.slugMap += homepageSlugs
                }
            } catch (homepageError: Exception) {
                Log.w(TAG, "Failed to refresh homepage slugs", homepageError)
                // Both endpoints down, keep existing data intact
            }
            return
        }

        seriesList = comics
        lastFetchTime = now

        val newSlugMap = comics.mapNotNull { dto -> dto.id?.let { it to dto.slug } }.toMap()

        preferences.slugMap += newSlugMap + fetchHomepageSlugs()
    }

    /** Fetch the cached site catalogue used by the website search dropdown. */
    private fun fetchSeriesListFromListEndpoint(): List<MangaDto> {
        val response = client.newCall(GET("$baseUrl/api/series/list", headers)).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch series list: HTTP ${response.code}")
        }
        return json.decodeMangaListPayload(response.body.string()).filter { it.type == "comic" }
    }

    private fun fetchHomepageSlugs(): Map<Int, String> = try {
        val homepage = client.newCall(GET("$baseUrl/api/homepage", headers)).execute()
            .parseAs<HomepageDto>()

        val slugs = mutableMapOf<Int, String>()

        homepage.data.newChapters
            ?.filter { it.type == "comic" }
            ?.forEach { slugs[it.id] = it.slug }

        homepage.rankings
            ?.filter { it.type == "comic" }
            ?.forEach { slugs[it.id] = it.slug }

        slugs
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse homepage slugs", e)
        emptyMap()
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        fetchSeriesList()
        return super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request {
        // HTML-first strategy: directly use base site scraping
        return GET(baseUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        // HTML-first strategy: directly use existing scraping helper
        return fetchPopularMangaByScraping()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val updatesUrl =
            "$publicApiBaseUrl/api/new-chapters"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
        return GET(updatesUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val payload = response.parseAs<NewChaptersDto>()
        val mangaList = payload.data
            .filter { it.type == "comic" }
            .mapNotNull { dto ->
                val mangaId = dto.id ?: return@mapNotNull null
                cacheManager.updateMangaCache(dto)
                preferences.slugMap = preferences.slugMap + (mangaId to dto.slug)
                dto.toSManga(mangaId.toString())
            }

        return MangasPage(mangaList, hasNextPage = payload.current_page < payload.last_page)
    }

    private fun fetchPopularMangaByScraping(): MangasPage {
        // El fallback de scraping siempre debe usar el sitio web (no dashboard API)
        val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        val section =
            document.selectFirst("section:has(h2:matchesOwn((?i)Popular Del Dia))")
                ?: document.selectFirst("section:has(h2:matchesOwn((?i)Popular))")
                ?: throw Exception("No se encontró la sección de populares en la web")

        val mangaList =
            section.select("figure a[href^=/series/comic-], a[href^=/series/comic-]")
                .mapNotNull { link ->
                    val href = link.attr("href").trim()
                    if (href.isBlank()) return@mapNotNull null

                    val title =
                        link.selectFirst("figcaption")?.text()?.trim()
                            ?: link.attr("title").trim()
                                .ifBlank { link.attr("aria-label").trim() }
                                .ifBlank { link.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty() }
                    if (title.isBlank()) return@mapNotNull null

                    val imageElement =
                        link.selectFirst("img[src]")
                            ?: link.closest("figure")?.selectFirst("img[src]")
                            ?: link.parent()?.selectFirst("img[src]")

                    val thumbnail = imageElement?.attr("abs:src")?.trim().orEmpty()
                    val trackedUrl = buildTrackedMangaUrlForFallback(href, title)

                    SManga.create().apply {
                        this.title = title
                        this.url = trackedUrl
                        this.thumbnail_url = thumbnail.ifBlank { null }
                    }
                }.distinctBy { it.url }

        if (mangaList.isEmpty()) {
            throw Exception("No se pudieron obtener populares via fallback HTML")
        }
        return MangasPage(mangaList, hasNextPage = false)
    }

    private fun fetchLatestUpdatesByScraping(page: Int): MangasPage {
        val updatesUrl =
            "$baseUrl/capitulos"
                .toHttpUrl()
                .newBuilder()
                .apply {
                    if (page > 1) addQueryParameter("page", page.toString())
                }.build()

        val document = client.newCall(GET(updatesUrl, headers)).execute().asJsoup()

        val primaryLinks = document.select("div.grid.md\\:grid-cols-2.gap-4 div.bg-gray-800 a[href^=/series/comic-]")
        val links = if (primaryLinks.isNotEmpty()) primaryLinks else document.select("div.grid a[href^=/series/comic-], a[href^=/series/comic-]")

        val mangaList =
            links
                .mapNotNull { link ->
                    val href = link.attr("href").trim()
                    if (href.isBlank()) return@mapNotNull null

                    val title =
                        link.selectFirst("figcaption")?.text()?.trim()
                            ?: link.attr("title").trim()
                                .ifBlank { link.attr("aria-label").trim() }
                                .ifBlank { link.closest(".bg-gray-800")?.selectFirst("figcaption")?.text()?.trim().orEmpty() }
                    if (title.isBlank()) return@mapNotNull null

                    val imageElement =
                        link.selectFirst("img[src]")
                            ?: link.closest(".bg-gray-800")?.selectFirst("img[src]")
                    val thumbnail = imageElement?.attr("abs:src")?.trim().orEmpty()
                    val trackedUrl = buildTrackedMangaUrlForFallback(href, title)

                    SManga.create().apply {
                        this.title = title
                        this.url = trackedUrl
                        this.thumbnail_url = thumbnail.ifBlank { null }
                    }
                }.distinctBy { it.url }

        if (mangaList.isEmpty()) {
            throw Exception("No se pudieron obtener recientes via fallback HTML")
        }

        val maxPageFromLinks =
            document.select("a[href^=/capitulos?page=]")
                .mapNotNull { anchor ->
                    anchor.attr("href")
                        .substringAfter("page=", "")
                        .substringBefore("&")
                        .toIntOrNull()
                }.maxOrNull()

        val nextPageFromArrow =
            document.selectFirst(
                "a[title*=siguiente], a[name*=siguiente], a:has(i.i-heroicons-arrow-right-20-solid)",
            )?.attr("href")
                ?.substringAfter("page=", "")
                ?.substringBefore("&")
                ?.toIntOrNull()

        val hasNextPage =
            when {
                nextPageFromArrow != null -> nextPageFromArrow > page
                maxPageFromLinks != null -> page < maxPageFromLinks
                else -> mangaList.size >= 10
            }

        return MangasPage(mangaList, hasNextPage = hasNextPage)
    }

    private fun buildTrackedMangaUrlForFallback(
        href: String,
        title: String,
    ): String {
        val slug = href.substringAfter("/series/comic-").substringBefore("?").substringBefore("/")
        val match = runCatching { fetchMangaDtoBySlug(slug) }.getOrNull()
            ?: runCatching { apiHelper.resolveMangaByName(title, null, cacheManager) }.getOrNull()
        val id = match?.id
        if (slug.isNotBlank() && id != null) {
            preferences.slugMap = preferences.slugMap + (id to slug)
            cacheManager.updateMangaCache(id.toString(), title, slug)
        }
        return id?.toString() ?: "/series/comic-$slug"
    }

    private fun resolveStableId(
        slug: String,
        name: String,
        idString: String?,
    ): String {
        val id = idString?.toIntOrNull()
            ?: apiHelper.resolveIdBySlug(slug, cacheManager)?.toIntOrNull()
            ?: throw Exception("Unable to resolve Olympus manga ID for $name")
        preferences.slugMap = preferences.slugMap + (id to slug)
        cacheManager.updateMangaCache(id.toString(), name, slug)
        return id.toString()
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        if (query.isNotEmpty()) {
            if (query.length < 3) {
                throw Exception("La búsqueda debe tener al menos 3 caracteres")
            }
            return GET("$publicApiBaseUrl/api/series/list", headers)
                .newBuilder()
                .tag(String::class.java, "query:$query")
                .build()
        }

        val url = "$publicApiBaseUrl/api/series".toHttpUrl().newBuilder()
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
        val body = response.body.string()
        if (response.code == 401) {
            logHttpIssue("searchMangaParse#401", response)
            throw Exception("Error en la búsqueda: sesión no autorizada (401)")
        }
        if (apiHelper.isErrorPage(response.code, body)) {
            logHttpIssue("searchMangaParse", response)
            throw Exception("Error en la búsqueda: respuesta HTML inesperada")
        }

        val requestTag = response.request.tag(String::class.java)
        val searchId = requestTag?.let { tag -> if (tag.startsWith("id_search:")) tag.substringAfter("id_search:") else null }
        val searchQuery = requestTag?.let { tag -> if (tag.startsWith("query:")) tag.substringAfter("query:") else null }

        if (searchId != null) {
            val seriesList = json.decodeMangaListPayload(body)
            val match = seriesList.firstOrNull { it.id?.toString() == searchId }
            return if (match != null) {
                cacheManager.updateMangaCache(match)
                MangasPage(listOf(match.toSManga(resolveStableId(match.slug, match.name, match.id?.toString()))), false)
            } else {
                MangasPage(emptyList(), false)
            }
        }
        if (searchQuery != null) {
            val normalizedQuery = searchQuery.trim().lowercase()
            val mangaList = json.decodeMangaListPayload(body)
                .filter { it.type == "comic" }
                .filter { it.name.lowercase().contains(normalizedQuery) }
                .map { dto ->
                    cacheManager.updateMangaCache(dto)
                    dto.toSManga(resolveStableId(dto.slug, dto.name, dto.id?.toString()))
                }
            return MangasPage(mangaList, hasNextPage = false)
        }
        if (response.request.url
                .toString()
                .startsWith("$publicApiBaseUrl/api/series")
        ) {
            val mangaList =
                json.decodeMangaListPayload(body).filter { it.type == "comic" }.map { dto ->
                    cacheManager.updateMangaCache(dto)
                    dto.toSManga(resolveStableId(dto.slug, dto.name, dto.id?.toString()))
                }
            return MangasPage(mangaList, hasNextPage = false)
        }

        return MangasPage(emptyList(), hasNextPage = false)
    }

    private fun parseMangaId(url: String): Int {
        // Handles: "123", "/series/comic-slug?mangaId=123", "123/456", and legacy chapter URLs.
        val idFromParam = url.substringAfter("mangaId=", "")
            .substringBefore("&")
            .takeIf { it.isNotEmpty() }
        val rawId = idFromParam ?: url.substringBefore("/").substringBefore("?")
        return rawId.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Unable to parse Olympus manga ID from URL: $url")
    }

    private fun parseMangaIdOrNull(url: String): Int? = runCatching { parseMangaId(url) }.getOrNull()

    private fun normalizedMangaId(url: String): String = parseMangaId(url).toString()

    private fun parseChapterIds(url: String): Pair<String, String> {
        val mangaId = normalizedMangaId(url)
        val chapterId = if (url.contains("/capitulo/")) {
            url.substringAfter("/capitulo/").substringBefore("/").substringBefore("?")
        } else {
            url.substringAfter("/", "").substringBefore("?")
        }.normalizeChapterIdentifier()

        if (chapterId.isEmpty()) {
            throw IllegalArgumentException("Unable to parse Olympus chapter ID from URL: $url")
        }

        return mangaId to chapterId
    }

    private fun String.normalizeChapterIdentifier(): String = trim()
        .removePrefix("Capitulo")
        .removePrefix("Capítulo")
        .removePrefix("capitulo")
        .removePrefix("capítulo")
        .trim()

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = parseMangaIdOrNull(manga.url)
        val slug = if (mangaId != null) {
            resolveSlugForMangaId(mangaId, manga.title)
        } else {
            UrlUtils.mangaSlugFromUrl(manga.url) ?: throw Exception("Slug not found for manga ${manga.title}")
        }
        return "$baseUrl/series/comic-$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = parseMangaIdOrNull(manga.url)
        val slug = if (mangaId != null) {
            resolveSlugForMangaId(mangaId, manga.title)
        } else {
            UrlUtils.mangaSlugFromUrl(manga.url) ?: throw Exception("Slug not found for manga ${manga.title}")
        }

        val apiUrl = "$baseUrl/api/series/$slug?type=comic"
        return GET(url = apiUrl, headers = headers)
            .newBuilder()
            .tag(MangaRefTag::class.java, MangaRefTag(manga))
            .tag(MangaTitleTag::class.java, MangaTitleTag(manga.title))
            .build()
    }

    private fun resolveSlugForMangaId(
        mangaId: Int,
        title: String? = null,
    ): String {
        val id = mangaId.toString()
        val resolvedSlug = apiHelper.resolveSlugById(id, title, cacheManager)
        if (!resolvedSlug.isNullOrBlank()) {
            preferences.slugMap = preferences.slugMap + (mangaId to resolvedSlug)
            return resolvedSlug
        }
        return preferences.slugMap[mangaId] ?: throw Exception("Slug not found for manga $mangaId")
    }

    private fun updateTaggedMangaUrl(
        response: Response,
        slug: String,
    ) {
        val taggedManga = response.request.tag(MangaRefTag::class.java)?.manga ?: return
        val mangaId = UrlUtils.mangaIdFromUrl(taggedManga.url) ?: return
        preferences.slugMap = preferences.slugMap + (mangaId.toInt() to slug)
        taggedManga.url = mangaId
        cacheManager.updateMangaCache(mangaId, taggedManga.title, slug)
    }

    private fun fetchMangaDetailsBySlug(slug: String): SManga {
        val dto = fetchMangaDtoBySlug(slug)
        cacheManager.updateMangaCache(dto)
        persistChapterCount(dto.id, dto.chapterCount, "fetchMangaDetailsBySlug")
        return dto.toSMangaDetails(resolveStableId(dto.slug, dto.name, dto.id?.toString()))
    }

    private fun fetchMangaDtoBySlug(slug: String): MangaDto {
        val response = client.newCall(GET("$baseUrl/api/series/$slug?type=comic", headers)).execute()
        val body = response.body.string()
        if (apiHelper.isErrorPage(response.code, body)) throw Exception("Error al obtener detalles de Olympus")
        return json.decodeMangaDetailPayload(body)
    }

    private fun fetchMangaDetailsByScraping(
        slug: String,
        preferredMangaId: String?,
        preferredTitle: String?,
    ): SManga {
        val document = client.newCall(GET("$baseUrl/series/comic-$slug", headers)).execute().asJsoup()
        val title = document.selectFirst("h1")?.text()?.trim().orEmpty().ifBlank { preferredTitle ?: slug }
        val id = preferredMangaId
            ?: apiHelper.resolveIdBySlug(slug, cacheManager)
            ?: throw Exception("Unable to resolve Olympus manga ID for $title")
        preferences.slugMap = preferences.slugMap + (id.toInt() to slug)
        cacheManager.updateMangaCache(id, title, slug)
        return SManga.create().apply {
            this.title = title
            url = id
            thumbnail_url = document.selectFirst("img[src*=/storage/], img[src]")?.attr("abs:src")?.trim()
            description = document.selectFirst("p")?.text()?.trim()
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val taggedManga = response.request.tag(MangaRefTag::class.java)?.manga
        val taggedId = taggedManga?.let { UrlUtils.mangaIdFromUrl(it.url) }
        // Si la API devuelve 401, intentar scraping
        if (response.code == 401) {
            logHttpIssue("mangaDetailsParse#401", response)
            val slug = mangaSlugFromDetailsRequest(response)
            return fetchMangaDetailsByScraping(
                slug = slug,
                preferredMangaId = taggedId,
                preferredTitle = taggedManga?.title,
            )
        }
        if (apiHelper.isErrorPage(response.code, body)) {
            logHttpIssue("mangaDetailsParse#errorPage", response)
            val title = response.request.tag(MangaTitleTag::class.java)?.title
            val currentId = taggedId
            val match = title?.let { apiHelper.resolveMangaByName(it, currentId, cacheManager) }
            if (match != null) {
                cacheManager.updateMangaCache(match)
                val details = fetchMangaDetailsBySlug(match.slug)
                updateTaggedMangaUrl(response, match.slug)
                return details
            }
            // Intentar scraping como último recurso
            val slugFromUrl = mangaSlugFromDetailsRequest(response)
            return fetchMangaDetailsByScraping(
                slug = slugFromUrl,
                preferredMangaId = currentId,
                preferredTitle = title,
            )
        }

        val dto = json.decodeMangaDetailPayload(body)
        cacheManager.updateMangaCache(dto)
        persistChapterCount(dto.id, dto.chapterCount, "mangaDetailsParse")
        val resolvedId = resolveStableId(dto.slug, dto.name, dto.id?.toString())
        val details = dto.toSMangaDetails(resolvedId)
        updateTaggedMangaUrl(response, dto.slug)
        return details
    }

    private fun mangaSlugFromDetailsRequest(response: Response): String = UrlUtils
        .mangaSlugFromUrl(response.request.url.toString())
        ?: throw Exception("Unable to parse Olympus manga slug from ${response.request.url}")

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, chapterIdentifier) = parseChapterIds(chapter.url)
        val parsedId = parseMangaId(mangaId)
        val mangaSlug = resolveSlugForMangaId(parsedId)
        val backendChapterId = resolveChapterId(mangaId, chapterIdentifier, mangaSlug)
        return "$baseUrl/capitulo/$backendChapterId/comic-$mangaSlug"
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = normalizedMangaId(manga.url)
        val parsedId = parseMangaId(mangaId)
        val mangaSlug = resolveSlugForMangaId(parsedId, manga.title)

        return paginatedChapterListRequest(mangaSlug, mangaId, 1)
            .newBuilder()
            .tag(MangaRefTag::class.java, MangaRefTag(manga))
            .tag(MangaTitleTag::class.java, MangaTitleTag(manga.title))
            .build()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable
        .fromCallable {
            resolveAndUpdateMangaSlug(manga)
        }.map {
            client.newCall(chapterListRequest(manga)).execute().use { response -> chapterListParse(response) }
        }.onErrorReturn { error ->
            Log.w(TAG, "Failed to fetch API chapters for manga ${manga.url}, falling back to HTML", error)
            val (chapters, parsedTotal) = fetchChapterListFromHtml(manga)
            val validatedChapters = validateChapterList(
                mangaId = normalizedMangaId(manga.url),
                slug = UrlUtils.mangaSlugFromUrl(manga.url),
                chapters = chapters,
                source = "html-fallback",
                reportedTotal = parsedTotal,
                pagesFetched = 1,
            )
            persistChapterCount(parseMangaIdOrNull(normalizedMangaId(manga.url)), parsedTotal, "html-fallback")
            validatedChapters
        }

    private fun resolveAndUpdateMangaSlug(manga: SManga) {
        val mangaId = UrlUtils.mangaIdFromUrl(manga.url) ?: return
        val slug = apiHelper.resolveSlugForManga(manga, cacheManager) ?: UrlUtils.mangaSlugFromUrl(manga.url) ?: return
        preferences.slugMap = preferences.slugMap + (mangaId.toInt() to slug)
        manga.url = mangaId
        cacheManager.updateMangaCache(mangaId, manga.title, slug)
    }

    private fun paginatedChapterListRequest(
        mangaUrl: String,
        mangaId: String,
        page: Int,
    ): Request = GET(
        url = "$dashboardApiBaseUrl/api/series/$mangaUrl/chapters?page=$page&direction=desc&type=comic",
        headers = headers,
    ).newBuilder()
        .tag(String::class.java, mangaId)
        .build()

    private fun fetchChapterListBySlug(
        slug: String,
        mangaId: String,
    ): List<SChapter> = client
        .newCall(paginatedChapterListRequest(slug, mangaId, 1))
        .execute()
        .use { chapterListParse(it) }

    private fun fetchChapterListByScraping(
        slug: String,
        preferredMangaId: String?,
    ): List<SChapter> {
        val mangaId = preferredMangaId
            ?: apiHelper.resolveIdBySlug(slug, cacheManager)
            ?: throw Exception("Unable to resolve Olympus manga ID for $slug")
        val (chapters, parsedTotal) = fetchChapterListFromHtml(
            SManga.create().apply {
                title = cacheManager.getMangaTitleById(mangaId) ?: slug
                url = mangaId
            },
        )
        val validatedChapters = validateChapterList(
            mangaId = mangaId,
            slug = slug,
            chapters = chapters,
            source = "html-fallback-internal",
            reportedTotal = parsedTotal,
            pagesFetched = 1,
        )
        persistChapterCount(parseMangaIdOrNull(mangaId), parsedTotal, "html-fallback-internal")
        return validatedChapters
    }

    /**
     * Stale-slug recovery for chapter list API errors (404, 500, HTML error pages).
     * Force-refreshes the series list cache, resolves the latest slug by stable manga ID,
     * and retries the chapter API exactly once. Returns null when a retry is not possible.
     */
    private fun forceRefreshAndRetryChapterList(
        taggedMangaId: String?,
        originalResponse: Response,
    ): List<SChapter>? {
        if (taggedMangaId == null) return null
        Log.d(TAG, "Stale-slug recovery: force-refreshing series list for mangaId=$taggedMangaId")
        try {
            apiHelper.forceRefreshSeriesList(cacheManager)
        } catch (e: Exception) {
            Log.w(TAG, "Stale-slug recovery: force-refresh failed", e)
            return null
        }
        val match = apiHelper.resolveMangaById(taggedMangaId, cacheManager)
        if (match == null) {
            Log.d(TAG, "Stale-slug recovery: mangaId=$taggedMangaId not found after refresh")
            return null
        }
        val newSlug = match.slug
        val oldSlug = originalResponse.request.url.toString()
            .substringAfter("/series/").substringBefore("/chapters")
        if (newSlug == oldSlug) {
            Log.d(TAG, "Stale-slug recovery: slug unchanged ($newSlug), skipping retry")
            return null
        }
        Log.d(TAG, "Stale-slug recovery: retrying with new slug=$newSlug (was=$oldSlug)")
        cacheManager.updateMangaCache(match)
        val mangaId = match.id?.toString() ?: taggedMangaId
        return fetchChapterListBySlug(newSlug, mangaId)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val taggedManga = response.request.tag(MangaRefTag::class.java)?.manga
        val taggedMangaId = taggedManga?.let { UrlUtils.mangaIdFromUrl(it.url) }
        // Si la API devuelve 401, intentar scraping fallback
        if (response.code == 401) {
            logHttpIssue("chapterListParse#401", response)
            val slug = response.request.url.toString().substringAfter("/series/").substringBefore("/chapters")
            return fetchChapterListByScraping(slug, taggedMangaId)
        }
        if (apiHelper.isErrorPage(response.code, body)) {
            logHttpIssue("chapterListParse#errorPage", response)
            // Stale-slug recovery: force-refresh series list, resolve by stable manga ID, retry once.
            val retryResult = forceRefreshAndRetryChapterList(taggedMangaId, response)
            if (retryResult != null) return retryResult
            // Force-refresh did not yield a usable slug — fall through to original resolution.
            val title = response.request.tag(MangaTitleTag::class.java)?.title
            val currentId = taggedMangaId
            val match =
                currentId?.let { apiHelper.resolveMangaById(it, cacheManager) }
                    ?: title?.let { apiHelper.resolveMangaByName(it, currentId, cacheManager) }
            if (match != null) {
                cacheManager.updateMangaCache(match)
                updateTaggedMangaUrl(response, match.slug)
                return fetchChapterListBySlug(match.slug, match.id?.toString() ?: currentId ?: match.slug)
            }
            // Intentar scraping como último recurso
            val slugFromUrl = response.request.url.toString().substringAfter("/series/").substringBefore("/chapters")
            return fetchChapterListByScraping(slugFromUrl, currentId)
        }

        val slug =
            response.request.url
                .toString()
                .substringAfter("/series/")
                .substringBefore("/chapters")
        val mangaId = response.request.tag(String::class.java) ?: apiHelper.resolveIdBySlug(slug, cacheManager) ?: slug
        val data = json.decodeFromString<PayloadChapterDto>(body)
        Log.d(
            TAG,
            "Chapter page loaded: mangaId=$mangaId slug=$slug page=1 pageCount=${data.data.size} reportedTotal=${data.meta.total}",
        )
        var resultSize = data.data.size
        var page = 2
        while (data.meta.total > resultSize) {
            val newRequest = paginatedChapterListRequest(slug, mangaId, page)
            val newResponse = client.newCall(newRequest).execute()
            val newBody = newResponse.body.string()
            if (apiHelper.isErrorPage(newResponse.code, newBody)) {
                throw Exception("Error al obtener página $page de capítulos")
            }
            val newData = json.decodeFromString<PayloadChapterDto>(newBody)
            Log.d(
                TAG,
                "Chapter page loaded: mangaId=$mangaId slug=$slug page=$page pageCount=${newData.data.size} reportedTotal=${newData.meta.total}",
            )
            if (newData.data.isEmpty()) {
                throw Exception(
                    "Olympus chapter pagination stopped with empty page: mangaId=$mangaId slug=$slug " +
                        "page=$page loaded=$resultSize reportedTotal=${data.meta.total}",
                )
            }
            data.data += newData.data
            resultSize += newData.data.size
            page += 1
        }

        synchronized(this) {
            val cacheUpdates = mutableMapOf<String, Int>()
            data.data.forEach { dto ->
                cacheUpdates["$mangaId/${dto.name}"] = dto.id
            }
            chapterNameToIdCache = chapterNameToIdCache + cacheUpdates
        }

        val chapters = data.data.map { it.toSChapter(slug, dateFormat, mangaId) }
        persistChapterCount(parseMangaIdOrNull(mangaId), data.meta.total, "chapterListParse-api")
        return validateChapterList(
            mangaId = mangaId,
            slug = slug,
            chapters = chapters,
            source = "api",
            reportedTotal = data.meta.total,
            pagesFetched = page - 1,
        )
    }

    private fun fetchChapterListFromHtml(manga: SManga): Pair<List<SChapter>, Int?> {
        val mangaId = normalizedMangaId(manga.url)
        val slug = resolveSlugForMangaId(parseMangaId(mangaId), manga.title)
        val pageUrl = "$baseUrl/series/comic-$slug"

        val document = client.newCall(GET(pageUrl, headers)).execute().asJsoup()

        val parsedTotal = CHAPTER_COUNT_TEXT_REGEX.find(document.text())
            ?.groupValues?.getOrNull(1)?.toIntOrNull()

        val chapters = document.select("a[href*=/capitulo/]").mapNotNull { element ->
            val href = element.attr("href")
            val chapterId = href.substringAfter("/capitulo/").substringBefore("/")
                .takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            val chapterNameEl = element.selectFirst(".chapter-name")
            val chapterNameText = chapterNameEl?.text()?.trim()
            // Extract chapter number from the displayed name, never from the backend internal ID.
            // "Capitulo 1" → 1, "Capitulo 145.5" → 145.5, "1" → 1
            val chapterNumber = chapterNameText?.let { text ->
                CHAPTER_NUMBER_TEXT_REGEX.find(text)?.groupValues?.getOrNull(1)
                    ?: text.toFloatOrNull()?.toString()
            } ?: "-1"

            val timeEl = element.selectFirst("time[datetime]")
            val dateStr = timeEl?.attr("datetime") ?: ""

            val backendId = chapterId.toIntOrNull()

            SChapter.create().apply {
                name = "Capitulo $chapterNumber"
                url = "$mangaId/$chapterNumber"
                chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                date_upload = try {
                    dateFormat.parse(dateStr)?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }.also {
                if (backendId != null) {
                    synchronized(this) {
                        chapterNameToIdCache = chapterNameToIdCache + mapOf("$mangaId/$chapterNumber" to backendId)
                    }
                }
            }
        }
        Log.d(TAG, "HTML chapter list loaded: mangaId=$mangaId slug=$slug chapterCount=${chapters.size} parsedTotal=$parsedTotal")
        return Pair(chapters, parsedTotal)
    }

    private fun persistChapterCount(
        mangaId: Int?,
        chapterCount: Int?,
        source: String,
    ) {
        if (mangaId == null || chapterCount == null || chapterCount <= 0) return
        val existing = preferences.chapterCountMap[mangaId]
        if (existing == null || chapterCount > existing) {
            preferences.chapterCountMap = preferences.chapterCountMap + (mangaId to chapterCount)
            Log.d(TAG, "Persisted expected chapter count: mangaId=$mangaId count=$chapterCount source=$source previous=$existing")
        }
    }

    private fun validateChapterList(
        mangaId: String,
        slug: String?,
        chapters: List<SChapter>,
        source: String,
        reportedTotal: Int?,
        pagesFetched: Int,
    ): List<SChapter> {
        val parsedMangaId = parseMangaIdOrNull(mangaId)
        if (parsedMangaId == null) {
            Log.d(
                TAG,
                "Chapter list accepted without count guard: mangaId=$mangaId slug=$slug " +
                    "source=$source current=${chapters.size} reportedTotal=$reportedTotal pagesFetched=$pagesFetched",
            )
            return chapters
        }

        val previousCount = preferences.chapterCountMap[parsedMangaId]
        val maxChapterNumber = chapters.maxOfOrNull { it.chapter_number }
        val maxChapterFloor = maxChapterNumber?.toInt()?.takeIf { it > 0 }

        // Best expected count from all available sources: API reported total, persisted count,
        // and maximum chapter number seen in the list (as a floor).
        val bestExpectedCount = listOfNotNull(reportedTotal, previousCount, maxChapterFloor).maxOrNull()

        Log.d(
            TAG,
            "Chapter list summary: mangaId=$parsedMangaId slug=$slug source=$source " +
                "current=${chapters.size} previous=$previousCount reportedTotal=$reportedTotal " +
                "bestExpected=$bestExpectedCount pagesFetched=$pagesFetched maxChapterNumber=$maxChapterNumber maxChapterFloor=$maxChapterFloor",
        )

        // Guard: reject significantly incomplete lists when we have a reliable expected count.
        // Skip guard when bestExpectedCount is null (unknown) or <= 1 (one-shots / new series).
        if (bestExpectedCount != null && bestExpectedCount > 1) {
            val minimumAccepted = bestExpectedCount * MIN_ACCEPTED_CHAPTER_PERCENT / 100
            if (minimumAccepted > 1 && chapters.size < minimumAccepted) {
                throw Exception(
                    "Olympus chapter list looks incomplete: mangaId=$parsedMangaId slug=$slug " +
                        "source=$source bestExpected=$bestExpectedCount current=${chapters.size} " +
                        "minimumAccepted=$minimumAccepted reportedTotal=$reportedTotal previous=$previousCount " +
                        "pagesFetched=$pagesFetched maxChapterNumber=$maxChapterNumber",
                )
            }
        }

        if (chapters.isNotEmpty()) {
            persistChapterCount(parsedMangaId, chapters.size, source)
            Log.d(TAG, "Stored good chapter count: mangaId=$parsedMangaId count=${chapters.size} source=$source")
        }

        return chapters
    }

    private fun resolveChapterId(mangaId: String, chapterIdentifier: String, mangaSlug: String): String {
        val normalizedChapterIdentifier = chapterIdentifier.normalizeChapterIdentifier()
        val cacheKey = "$mangaId/$normalizedChapterIdentifier"

        chapterNameToIdCache[cacheKey]?.let { return it.toString() }

        val parsedMangaId = parseMangaId(mangaId)
        try {
            val page1Request = paginatedChapterListRequest(mangaSlug, mangaId, 1)
            val firstResponse = client.newCall(page1Request).execute()
            val firstPage = firstResponse.parseAs<PayloadChapterDto>()
            val allChapters = mutableListOf<ChapterDto>()
            allChapters += firstPage.data

            var resultSize = firstPage.data.size
            var page = 2
            while (firstPage.meta.total > resultSize) {
                val newRequest = paginatedChapterListRequest(mangaSlug, mangaId, page)
                val newResponse = client.newCall(newRequest).execute()
                val newData = newResponse.parseAs<PayloadChapterDto>()
                allChapters += newData.data
                resultSize += newData.data.size
                page += 1
            }

            synchronized(this) {
                val cacheUpdates = mutableMapOf<String, Int>()
                allChapters.forEach { dto ->
                    cacheUpdates["$mangaId/${dto.name}"] = dto.id
                }
                chapterNameToIdCache = chapterNameToIdCache + cacheUpdates
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve chapter ID via chapter list for manga $parsedMangaId", e)
        }

        chapterNameToIdCache[cacheKey]?.let { return it.toString() }

        normalizedChapterIdentifier.toIntOrNull()?.let { return normalizedChapterIdentifier }

        throw Exception("Unable to resolve chapter ID for $chapterIdentifier in manga $mangaId")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val (mangaId, chapterIdentifier) = parseChapterIds(chapter.url)
        val parsedId = parseMangaId(mangaId)
        val mangaSlug = resolveSlugForMangaId(parsedId)
        val backendChapterId = resolveChapterId(mangaId, chapterIdentifier, mangaSlug)

        return GET("$baseUrl/api/capitulo/comic-$mangaSlug/$backendChapterId", headers)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val slugFromUrl = UrlUtils.chapterSlugFromUrl(chapter.url)
        val mangaId = UrlUtils.chapterMangaIdFromUrl(chapter.url) ?: cacheManager.getMangaIdBySlug(slugFromUrl)
        val resolvedSlug =
            if (mangaId != null) {
                apiHelper.resolveSlugById(mangaId, cacheManager.getMangaTitleById(mangaId), cacheManager) ?: slugFromUrl
            } else {
                slugFromUrl
            }
        if (resolvedSlug != slugFromUrl && resolvedSlug.isNotBlank()) {
            chapter.url = chapter.url.replace(Regex("comic-[^/?]+"), "comic-$resolvedSlug")
            if (mangaId != null) {
                cacheManager.updateMangaCache(mangaId, cacheManager.getMangaTitleById(mangaId), resolvedSlug)
            }
        }
    }.map {
        client.newCall(pageListRequest(chapter)).execute().use { response -> pageListParse(response) }
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        // Si la API devuelve 401 (Unauthorized), hacer scraping del HTML
        if (response.code == 401 || apiHelper.isErrorPage(response.code, body)) {
            logHttpIssue("pageListParse", response)
            // Intentar scraping del capítulo via HTML
            val chapterId = getChapterIdFromUrl(response.request.url.toString())
            val mangaSlug = getMangaSlugFromUrl(response.request.url.toString())
            if (chapterId != null && mangaSlug != null) {
                return fetchChapterPagesByScraping(mangaSlug, chapterId)
            }
            throw Exception("Error al cargar páginas del capítulo: API retornó 401")
        }
        return json.decodeFromString<PayloadPagesDto>(body).chapter.pages.mapIndexed { i, img ->
            Page(i, imageUrl = img)
        }
    }

    private fun getChapterIdFromUrl(url: String): String? = when {
        "/api/capitulo/comic-" in url -> url.substringAfterLast("/").substringBefore("?")
        "/chapters/" in url -> url.substringAfter("/chapters/").substringBefore("?").substringBefore("/")
        "/capitulo/" in url -> url.substringAfter("/capitulo/").substringBefore("/").substringBefore("?")
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun getMangaSlugFromUrl(url: String): String? {
        if ("/api/capitulo/comic-" in url) {
            return url.substringAfter("/api/capitulo/comic-").substringBefore("/").substringBefore("?")
        }
        if ("/capitulo/" in url && "/comic-" in url) {
            return url.substringAfter("/comic-").substringBefore("/").substringBefore("?")
        }
        val match = Regex("/series/([^/]+)/chapters").find(url)
        return match?.groupValues?.getOrNull(1)
    }

    private fun fetchChapterPagesByScraping(mangaSlug: String, chapterId: String): List<Page> {
        val chapterUrl = "$baseUrl/capitulo/$chapterId/comic-$mangaSlug"
        val document = client.newCall(GET(chapterUrl, headers)).execute().asJsoup()

        // Nuevo layout: múltiples contenedores con <img src="...storage/comics/..."> dentro de section principal.
        val imgElements =
            document.select(
                "section img[src], div.flex.flex-col img[src], div.relative img[src], img[src*=/storage/comics/]",
            )

        val uniqueImageUrls = linkedSetOf<String>()
        imgElements.forEach { img ->
            val src = img.attr("abs:src").trim()
            if (src.isNotBlank() && src.contains("/storage/comics/", ignoreCase = true)) {
                uniqueImageUrls.add(src)
            }
        }

        val images = uniqueImageUrls.mapIndexed { i, src -> Page(i, imageUrl = src) }
        if (images.isEmpty()) {
            throw Exception("No se pudieron extraer las páginas del capítulo")
        }
        return images
    }

    private fun logHttpIssue(
        stage: String,
        response: Response,
    ) {
        val requestUrl = response.request.url.toString()
        val host = response.request.url.host
        val code = response.code
        Log.w("OlympusScanlation", "HTTP issue stage=$stage code=$code host=$host url=$requestUrl")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = filterManager.getFilterList(headersMap, dashboardApiBaseUrl)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        filterManager.setupPreferenceScreen(screen, defaultBaseUrl)
    }

    private var cachedBaseUrl: String? = null
    private fun getPrefBaseUrl(): String {
        if (cachedBaseUrl == null) {
            cachedBaseUrl = preferences.getString("overrideBaseUrl", defaultBaseUrl)!!
        }
        return cachedBaseUrl!!
    }

    private fun setPrefBaseUrl(value: String) {
        cachedBaseUrl = value
        preferences.edit().putString("overrideBaseUrl", value).apply()
    }
}
