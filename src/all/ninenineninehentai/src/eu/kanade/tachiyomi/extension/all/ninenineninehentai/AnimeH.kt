package eu.kanade.tachiyomi.extension.all.ninenineninehentai

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.ninenineninehentai.Url.Companion.toAbsUrl
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

open class AnimeH(
    final override val lang: String,
    private val siteLang: String = lang,
) : HttpSource(), ConfigurableSource {

    override val name = "AnimeH"

    override val baseUrl = "https://animeh.to"

    private val apiUrl = "https://api.animeh.to/api"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url

            if (url.host != "127.0.0.1") {
                return@addInterceptor chain.proceed(request)
            }

            val newRequest = request.newBuilder()
                .url(
                    url.newBuilder()
                        .host(preference.cdnUrl)
                        .build(),
                ).build()

            return@addInterceptor chain.proceed(newRequest)
        }
        .rateLimit(1)
        .build()

    private val preference by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val payload = GraphQL(
            PopularVariables(size, page, 1, siteLang),
            POPULAR_QUERY,
        ).toJsonRequestBody()

        return POST(apiUrl, headers, payload)
    }

    override fun popularMangaParse(response: Response) = browseMangaParse<PopularResponse>(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList())
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(SEARCH_PREFIX)) {
            val mangaId = query.substringAfter(SEARCH_PREFIX)
            client.newCall(mangaFromIDRequest(mangaId))
                .asObservableSuccess()
                .map(::searchMangaFromIDParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = GraphQL(
            SearchVariables(
                size = size,
                page = page,
                search = SearchPayload(
                    query = query.trim().takeUnless { it.isEmpty() },
                    language = siteLang,
                    sortBy = filters.firstInstanceOrNull<SortFilter>()?.selected,
                    format = filters.firstInstanceOrNull<FormatFilter>()?.selected,
                    tags = filters.firstInstanceOrNull<IncludedTagFilter>()?.tags,
                    excludeTags = filters.firstInstanceOrNull<ExcludedTagFilter>()?.tags,
                    pagesRangeStart = filters.firstInstanceOrNull<MinPageFilter>()?.value,
                    pagesRangeEnd = filters.firstInstanceOrNull<MaxPageFilter>()?.value,
                ),
            ),
            SEARCH_QUERY,
        ).toJsonRequestBody()

        return POST(apiUrl, headers, payload)
    }

    override fun searchMangaParse(response: Response) = browseMangaParse<SearchResponse>(response)
    override fun getFilterList() = getFilters()

    private fun mangaFromIDRequest(id: String): Request {
        val payload = GraphQL(
            IdVariables(id),
            DETAILS_QUERY,
        ).toJsonRequestBody()

        return POST(apiUrl, headers, payload)
    }

    private fun searchMangaFromIDParse(response: Response): MangasPage {
        val res = response.parseAs<ApiDetailsResponse>()

        val manga = res.data.details
            .takeIf { it.language == siteLang || lang == "all" }
            ?.let { manga ->
                preference.dateMap = preference.dateMap.also { dateMap ->
                    manga.uploadDate?.let { dateMap[manga.id] = it }
                }
                manga.toSManga(preference.shortTitle)
            }

        return MangasPage(listOfNotNull(manga), false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return mangaFromIDRequest(manga.url)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<ApiDetailsResponse>()
        val manga = res.data.details

        preference.dateMap = preference.dateMap.also { dateMap ->
            manga.uploadDate?.let { dateMap[manga.id] = it }
        }

        return manga.toSManga(preference.shortTitle)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/hchapter/${manga.url}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val group = manga.description
            ?.substringAfter("Group:", "")
            ?.substringBefore("\n")
            ?.trim()
            ?.takeUnless { it.isEmpty() }

        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                    date_upload = preference.dateMap[manga.url].parseDate()
                    scanlator = group
                },
            ),
        )
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/hchapter/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val payload = GraphQL(
            IdVariables(chapter.url),
            PAGES_QUERY,
        ).toJsonRequestBody()

        return POST(apiUrl, headers, payload)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res = response.parseAs<ApiPageListResponse>()

        val pages = res.data.chapter.pages?.firstOrNull()
            ?: return emptyList()

        val cdnUrl = "https://${getUpdatedCdn(res.data.chapter.id)}/"
        val cdn = pages.urlPart.toAbsUrl(cdnUrl)

        val selectedImages = when (preference.getString(PREF_IMG_QUALITY_KEY, "original")) {
            "medium" -> pages.qualityMedium?.mapIndexed { i, it ->
                it ?: pages.qualityOriginal[i]
            }
            else -> pages.qualityOriginal
        } ?: pages.qualityOriginal

        return selectedImages.mapIndexed { index, image ->
            Page(index, "", "$cdn/${image.url}")
        }
    }

    private fun getUpdatedCdn(chapterId: String): String {
        val url = "$baseUrl/hchapter/$chapterId"
        val document = client.newCall(GET(url, headers))
            .execute().use { it.asJsoup() }

        val cdnHost = document.selectFirst("meta[property=og:image]")
            ?.attr("content")
            ?.toHttpUrlOrNull()
            ?.host

        return cdnHost?.also {
            preference.cdnUrl = it
        } ?: preference.cdnUrl
    }

    private inline fun <reified T> String.parseAs(): T =
        json.decodeFromString(this)

    private inline fun <reified T> Response.parseAs(): T =
        use { body.string() }.parseAs()

    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? =
        filterIsInstance<T>().firstOrNull()

    private inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody =
        json.encodeToString(this)
            .toRequestBody(JSON_MEDIA_TYPE)

    private fun String?.parseDate(): Long {
        return runCatching {
            dateFormat.parse(this!!.trim())!!.time
        }.getOrDefault(0L)
    }

    private inline fun <reified T : BrowseResponse> browseMangaParse(response: Response): MangasPage {
        val res = response.parseAs<Data<T>>()
        val mangas = res.data.chapters.edges
        val dateMap = preference.dateMap
        val useShortTitle = preference.shortTitle
        val entries = mangas.map { manga ->
            manga.uploadDate?.let { dateMap[manga.id] = it }
            manga.toSManga(useShortTitle)
        }
        preference.dateMap = dateMap
        val hasNextPage = mangas.size == size

        return MangasPage(entries, hasNextPage)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMG_QUALITY_KEY
            title = "Default Image Quality"
            entries = arrayOf("Original", "Medium")
            entryValues = arrayOf("original", "medium")
            setDefaultValue("original")
            summary = "%s"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHORT_TITLE
            title = "Display Short Titles"
            summaryOff = "Showing Long Titles"
            summaryOn = "Showing short Titles"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private var SharedPreferences.dateMap: MutableMap<String, String>
        get() {
            val jsonMap = getString(PREF_DATE_MAP_KEY, "{}")!!
            val dateMap = runCatching { jsonMap.parseAs<MutableMap<String, String>>() }
            return dateMap.getOrDefault(mutableMapOf())
        }

        @SuppressLint("ApplySharedPref")
        set(dateMap) {
            edit()
                .putString(PREF_DATE_MAP_KEY, json.encodeToString(dateMap))
                .commit()
        }

    private var SharedPreferences.cdnUrl: String
        get() = getString(PREF_CDN_URL, DEFAULT_CDN) ?: DEFAULT_CDN

        @SuppressLint("ApplySharedPref")
        set(cdnUrl) {
            edit().putString(PREF_CDN_URL, cdnUrl).commit()
        }

    private val SharedPreferences.shortTitle get() = getBoolean(PREF_SHORT_TITLE, false)

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private const val size = 20
        const val SEARCH_PREFIX = "id:"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }

        private const val PREF_DATE_MAP_KEY = "pref_date_map"
        private const val PREF_CDN_URL = "pref_cdn_url"
        private const val PREF_IMG_QUALITY_KEY = "pref_image_quality"
        private const val PREF_SHORT_TITLE = "pref_short_title"

        private const val DEFAULT_CDN = "edge.fast4speed.rsvp"
    }
}
